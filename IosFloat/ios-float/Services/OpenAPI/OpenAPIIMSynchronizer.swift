import Foundation

enum OpenApiIMSynchronizer {
    private static let contactAvatarDetailLookupLimit = 1500
    private static let contactAvatarDetailBatchSize = 10

    static func loadSnapshot(contactLimitPerAccount: Int = 1500, groupLimitPerAccount: Int = 120) async throws -> OpenApiIMSnapshot {
        let loadedAccounts = try await loadAccounts()
        var contacts: [OpenApiConversationContext] = []
        var chatrooms: [OpenApiConversationContext] = []
        var friendRequests: [OpenApiFriendRequestContext] = []

        for account in loadedAccounts {
            contacts.append(contentsOf: try await loadContacts(for: account, limit: contactLimitPerAccount))
            chatrooms.append(contentsOf: try await loadChatrooms(for: account, limit: groupLimitPerAccount))
            friendRequests.append(contentsOf: await loadFriendRequestsIfAvailable(for: account, limit: 100))
        }
        let accounts = accountsWithDiscoveredAvatars(
            loadedAccounts,
            conversations: contacts + chatrooms + friendRequests.map(\.conversationContext)
        )
        let avatarHydratedConversations = conversationsWithSharedAvatars(
            contacts + chatrooms + friendRequests.map(\.conversationContext),
            accounts: accounts
        )
        let avatarHydratedConversationByID = Dictionary(
            avatarHydratedConversations.map { ($0.participantID, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        contacts = contacts.map { avatarHydratedConversationByID[$0.participantID] ?? $0 }
        chatrooms = chatrooms.map { avatarHydratedConversationByID[$0.participantID] ?? $0 }
        friendRequests = friendRequests.map { request in
            guard let hydrated = avatarHydratedConversationByID[request.participantID],
                  OpenApiDisplay.avatarURL(from: hydrated.avatarURL) != nil,
                  OpenApiDisplay.avatarURL(from: request.avatarURL) == nil
            else { return request }
            return request.replacingAvatarURL(hydrated.avatarURL)
        }
        let taskResults = await loadRecentTasksIfAvailable(for: accounts, limit: 80)

        return OpenApiIMSnapshot(
            accounts: accounts,
            // Keep one relationship per owner account. OpenApiIMSnapshot.conversations
            // performs the display-level de-duplication by canonical participant ID.
            contacts: contacts,
            chatrooms: chatrooms,
            friendRequests: uniqueFriendRequests(friendRequests),
            taskResults: uniqueTaskResults(taskResults)
        )
    }

    static func loadContactsForInvite(for account: OpenApiWeChatAccountContext, limit: Int = 3000) async throws -> [OpenApiConversationContext] {
        try await loadContacts(for: account, limit: limit)
    }

    private static func loadAccounts() async throws -> [OpenApiWeChatAccountContext] {
        let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/wechat-accounts")
        let rawAccounts = arrayPayload(from: result.jsonObject)
        return rawAccounts.compactMap { raw in
            let wxid = string(raw["wxid"])
            guard !wxid.isEmpty else { return nil }
            let nickname = OpenApiDisplay.cleaned(string(raw["nickname"])).isEmpty
                ? wxid
                : OpenApiDisplay.cleaned(string(raw["nickname"]))
            return OpenApiWeChatAccountContext(
                participantID: OpenApiStableID.uuid(namespace: "openapi-account", key: wxid),
                wxid: wxid,
                nickname: nickname,
                clientUuid: string(raw["clientUuid"]),
                accountStatus: int(raw["accountStatus"]),
                avatarURL: OpenApiDisplay.avatarURLString(from: raw)
            )
        }
    }

    private static func loadContacts(for account: OpenApiWeChatAccountContext, limit: Int) async throws -> [OpenApiConversationContext] {
        var page = 1
        let pageSize = min(max(limit, 1), 100)
        var contacts: [OpenApiConversationContext] = []
        var totalCount: Int?
        let socialAccount = OpenApiSocialAccount(deviceUUID: account.clientUuid, weChatID: account.wxid)
        let socialService = OpenApiSocialManagementService()
        repeat {
            let result = try await socialService.listContacts(
                account: socialAccount,
                page: page,
                pageSize: pageSize,
                onlyFriends: true,
                includeProfile: true,
                includeDeleted: false
            )
            let rawItems = arrayPayload(from: result.jsonObject)
            totalCount = totalPayloadCount(from: result.jsonObject) ?? totalCount
            contacts.append(contentsOf: rawItems.compactMap { raw in
                let wxid = firstCleanedValue([
                    raw["wxid"],
                    raw["wxId"],
                    raw["userName"],
                    raw["username"],
                    nestedValue(raw, ["contact", "wxid"]),
                    nestedValue(raw, ["contact", "wxId"]),
                    nestedValue(raw, ["contact", "userName"]),
                    nestedValue(raw, ["user", "wxid"]),
                    nestedValue(raw, ["user", "wxId"]),
                    nestedValue(raw, ["user", "userName"])
                ])
                guard !wxid.isEmpty else { return nil }
                let remark = firstCleanedValue([
                    raw["remarks"],
                    raw["remark"],
                    raw["remarkName"],
                    raw["friendRemark"],
                    raw["conRemark"],
                    nestedValue(raw, ["contact", "remarks"]),
                    nestedValue(raw, ["contact", "remark"]),
                    nestedValue(raw, ["contact", "remarkName"]),
                    nestedValue(raw, ["friend", "remarks"]),
                    nestedValue(raw, ["friend", "remark"])
                ])
                let rawNickname = firstCleanedValue([
                    raw["nickname"],
                    raw["nickName"],
                    nestedValue(raw, ["contact", "nickname"]),
                    nestedValue(raw, ["contact", "nickName"]),
                    nestedValue(raw, ["user", "nickname"]),
                    nestedValue(raw, ["user", "nickName"]),
                    raw["displayName"],
                    raw["name"],
                    nestedValue(raw, ["contact", "displayName"]),
                    nestedValue(raw, ["contact", "name"]),
                    nestedValue(raw, ["user", "displayName"]),
                    nestedValue(raw, ["user", "name"])
                ])
                let nickname = rawNickname.caseInsensitiveCompare(wxid) == .orderedSame
                    || rawNickname.lowercased().hasPrefix("wxid_")
                    || rawNickname.hasSuffix("@chatroom")
                    ? ""
                    : rawNickname
                let friendNo = firstCleanedValue([
                    raw["friendNo"], raw["friend_no"], raw["wechatNo"], raw["wechat_no"],
                    raw["wechatAlias"], raw["wechat_alias"], raw["alias"], raw["aliasName"],
                    nestedValue(raw, ["contact", "friendNo"]),
                    nestedValue(raw, ["contact", "wechatNo"]),
                    nestedValue(raw, ["contact", "wechatAlias"]),
                    nestedValue(raw, ["contact", "alias"]),
                    nestedValue(raw, ["friend", "friendNo"]),
                    nestedValue(raw, ["friend", "wechatNo"]),
                    nestedValue(raw, ["friend", "alias"]),
                    nestedValue(raw, ["user", "friendNo"]),
                    nestedValue(raw, ["user", "wechatNo"]),
                    nestedValue(raw, ["user", "alias"])
                ])
                let displayName = OpenApiDisplay.contactDisplayName(
                    remark: remark,
                    friendNo: friendNo,
                    nickname: nickname
                )
                let customerLevel = firstCleanedValue([
                    nestedValue(raw, ["customerProfile", "customerLevel"]),
                    raw["customerLevel"]
                ])
                let sourceChannel = firstCleanedValue([
                    nestedValue(raw, ["customerProfile", "sourceChannel"]),
                    raw["sourceChannel"]
                ])
                let profileKey = firstCleanedValue([
                    nestedValue(raw, ["customerProfile", "profileKey"]),
                    raw["profileKey"]
                ])
                let profileNotes = firstCleanedValue([
                    nestedValue(raw, ["customerProfile", "notes"]),
                    raw["notes"]
                ])
                return OpenApiConversationContext(
                    participantID: OpenApiStableID.uuid(namespace: "openapi-contact", key: wxid),
                    ownerWxid: account.wxid,
                    wxid: wxid,
                    backendID: string(raw["id"]),
                    displayName: displayName,
                    friendNo: friendNo,
                    avatarURL: OpenApiDisplay.contactAvatarURLString(from: raw),
                    remark: remark,
                    source: OpenApiDisplay.cleaned(string(raw["source"])),
                    sourceExt: OpenApiDisplay.cleaned(string(raw["sourceExt"])),
                    customerLevel: customerLevel,
                    sourceChannel: sourceChannel,
                    profileKey: profileKey,
                    phone: phoneString(from: raw),
                    notes: profileNotes,
                    kind: .contact,
                    memberCount: nil
                )
            })
            page += 1
            if rawItems.count < pageSize { break }
        } while contacts.count < min(totalCount ?? limit, limit)
        return await contactsWithDetailAvatars(Array(contacts.prefix(limit)))
    }

    private static func contactsWithDetailAvatars(_ contacts: [OpenApiConversationContext]) async -> [OpenApiConversationContext] {
        var resolvedContacts = contacts
        let lookupIndexes = Array(resolvedContacts.indices.filter { index in
            let contact = resolvedContacts[index]
            return contact.kind == .contact
                && OpenApiDisplay.avatarURL(from: contact.avatarURL) == nil
                && int(contact.backendID) > 0
        }.prefix(contactAvatarDetailLookupLimit))
        guard !lookupIndexes.isEmpty else { return resolvedContacts }

        var offset = 0
        while offset < lookupIndexes.count {
            let batch = Array(lookupIndexes[offset..<min(offset + contactAvatarDetailBatchSize, lookupIndexes.count)])
            await withTaskGroup(of: (Int, String)?.self) { group in
                for index in batch {
                    let contact = resolvedContacts[index]
                    group.addTask {
                        guard let avatarURL = await loadContactDetailAvatar(for: contact) else {
                            return nil
                        }
                        return (index, avatarURL)
                    }
                }
                for await result in group {
                    guard let (index, avatarURL) = result else { continue }
                    resolvedContacts[index] = resolvedContacts[index].replacingAvatarURL(avatarURL)
                }
            }
            offset += contactAvatarDetailBatchSize
        }
        return resolvedContacts
    }

    private static func loadContactDetailAvatar(for contact: OpenApiConversationContext) async -> String? {
        let contactID = int(contact.backendID)
        guard contactID > 0 else { return nil }
        do {
            let result = try await OpenApiHTTPClient.request(
                "GET",
                path: "/openapi/v1/contacts/\(contactID)/detail",
                query: [
                    URLQueryItem(name: "commonChatRoomLimit", value: "0"),
                    URLQueryItem(name: "relationLogLimit", value: "0")
                ]
            )
            let root = result.jsonObject as? [String: Any] ?? [:]
            let payload = dictionaryPayload(from: result.jsonObject)
            for raw in [payload, root] where !raw.isEmpty {
                let avatarCandidates = [
                    OpenApiDisplay.contactAvatarURLString(from: raw),
                    (raw["contact"] as? [String: Any]).map(OpenApiDisplay.contactAvatarURLString(from:)) ?? "",
                    (raw["profile"] as? [String: Any]).map(OpenApiDisplay.contactAvatarURLString(from:)) ?? "",
                    (raw["customerProfile"] as? [String: Any]).map(OpenApiDisplay.contactAvatarURLString(from:)) ?? ""
                ]
                if let avatarURL = avatarCandidates.first(where: { OpenApiDisplay.avatarURL(from: $0) != nil }) {
                    return avatarURL
                }
            }
        } catch {
            return nil
        }
        return nil
    }

    private static func loadChatrooms(for account: OpenApiWeChatAccountContext, limit: Int) async throws -> [OpenApiConversationContext] {
        var page = 1
        let pageSize = min(max(limit, 1), 100)
        var chatrooms: [OpenApiConversationContext] = []
        var totalCount: Int?
        let socialAccount = OpenApiSocialAccount(deviceUUID: account.clientUuid, weChatID: account.wxid)
        let socialService = OpenApiSocialManagementService()
        repeat {
            let result = try await socialService.listChatRooms(
                account: socialAccount,
                page: page,
                pageSize: pageSize,
                includeDeleted: false
            )
            let rawItems = arrayPayload(from: result.jsonObject)
            totalCount = totalPayloadCount(from: result.jsonObject) ?? totalCount
            chatrooms.append(contentsOf: rawItems.compactMap { raw in
                let chatRoomId = OpenApiDisplay.chatRoomID(from: raw)
                guard !chatRoomId.isEmpty else { return nil }
                let ownerWxid = OpenApiDisplay.cleaned(string(raw["ownerWxid"]))
                let resolvedOwnerWxid = ownerWxid.isEmpty ? account.wxid : ownerWxid
                let name = firstCleanedValue([
                    raw["name"],
                    raw["displayName"],
                    raw["chatroomName"],
                    raw["roomName"],
                    raw["nickname"],
                    raw["remark"],
                    raw["remarks"]
                ])
                let ownerMemberWxid = OpenApiDisplay.cleaned(string(raw["ownerMemberWxid"]))
                let groupStatus = int(raw["groupStatus"])
                let notes = [
                    ownerMemberWxid.isEmpty ? "" : "ownerMemberWxid=\(ownerMemberWxid)",
                    groupStatus == 0 ? "" : "groupStatus=\(groupStatus)",
                    bool(raw["isDeleted"]) ? "isDeleted=true" : ""
                ].filter { !$0.isEmpty }.joined(separator: ";")
                return OpenApiConversationContext(
                    participantID: OpenApiStableID.uuid(namespace: "openapi-chatroom", key: chatRoomId),
                    ownerWxid: resolvedOwnerWxid,
                    wxid: chatRoomId,
                    backendID: string(raw["id"]),
                    displayName: name.isEmpty ? chatRoomId : name,
                    friendNo: "",
                    avatarURL: OpenApiDisplay.avatarURLString(from: raw),
                    remark: "",
                    source: OpenApiDisplay.cleaned(string(raw["source"])),
                    sourceExt: OpenApiDisplay.cleaned(string(raw["sourceExt"])),
                    customerLevel: "",
                    sourceChannel: "",
                    profileKey: "",
                    phone: "",
                    notes: notes,
                    kind: .chatroom,
                    memberCount: int(raw["memberCount"])
                )
            })
            page += 1
            if rawItems.count < pageSize { break }
        } while chatrooms.count < min(totalCount ?? limit, limit)
        return Array(chatrooms.prefix(limit))
    }

    static func loadChatroomMembers(
        for account: OpenApiWeChatAccountContext,
        chatRoomID: String,
        limit: Int
    ) async throws -> [OpenApiChatRoomMemberContext] {
        // URL.appendingPathComponent performs the required escaping. Pre-encoding
        // `@chatroom` here turns `%40` into `%2540` and the server rejects the ID.
        let validatedChatRoomID = OpenApiDisplay.validChatRoomID(chatRoomID) ?? chatRoomID
        var page = 1
        let pageSize = min(max(limit, 1), 200)
        var members: [OpenApiChatRoomMemberContext] = []
        var totalCount: Int?
        let socialAccount = OpenApiSocialAccount(deviceUUID: account.clientUuid, weChatID: account.wxid)
        let socialService = OpenApiSocialManagementService()
        repeat {
            let result = try await socialService.listChatRoomMembers(
                account: socialAccount,
                chatRoomID: validatedChatRoomID,
                page: page,
                pageSize: pageSize,
                includeDeleted: false
            )
            let rawItems = arrayPayload(from: result.jsonObject)
            totalCount = totalPayloadCount(from: result.jsonObject) ?? totalCount
            members.append(contentsOf: rawItems.compactMap { raw in
                let memberWxid = firstCleanedValue([
                    raw["memberWxid"],
                    raw["memberWxId"],
                    raw["memberWxID"],
                    raw["wxid"],
                    raw["wxId"],
                    raw["userName"],
                    raw["username"],
                    raw["userWxid"],
                    raw["userWxId"],
                    raw["memberId"],
                    raw["memberID"],
                    raw["friendWxid"],
                    raw["friendWxId"],
                    nestedValue(raw, ["member", "wxid"]),
                    nestedValue(raw, ["member", "wxId"]),
                    nestedValue(raw, ["member", "memberWxid"]),
                    nestedValue(raw, ["member", "memberWxId"]),
                    nestedValue(raw, ["member", "userName"]),
                    nestedValue(raw, ["member", "username"]),
                    nestedValue(raw, ["memberInfo", "wxid"]),
                    nestedValue(raw, ["memberInfo", "wxId"]),
                    nestedValue(raw, ["memberInfo", "memberWxid"]),
                    nestedValue(raw, ["memberInfo", "userName"]),
                    nestedValue(raw, ["contact", "wxid"]),
                    nestedValue(raw, ["contact", "wxId"]),
                    nestedValue(raw, ["user", "wxid"]),
                    nestedValue(raw, ["user", "wxId"]),
                    nestedValue(raw, ["user", "userName"])
                ])
                guard !memberWxid.isEmpty else { return nil }
                let nickname = firstCleanedValue([
                    raw["nickname"],
                    raw["nickName"],
                    raw["memberNickname"],
                    raw["memberNickName"],
                    raw["userNickname"],
                    nestedValue(raw, ["member", "nickname"]),
                    nestedValue(raw, ["member", "nickName"]),
                    nestedValue(raw, ["memberInfo", "nickname"]),
                    nestedValue(raw, ["memberInfo", "nickName"]),
                    nestedValue(raw, ["contact", "nickname"]),
                    nestedValue(raw, ["contact", "nickName"]),
                    nestedValue(raw, ["user", "nickname"]),
                    nestedValue(raw, ["user", "nickName"])
                ])
                let remarks = firstCleanedValue([
                    raw["remarks"],
                    raw["remark"],
                    raw["memberRemark"],
                    raw["memberRemarks"],
                    raw["chatRoomRemark"],
                    raw["groupRemark"],
                    nestedValue(raw, ["member", "remarks"]),
                    nestedValue(raw, ["member", "remark"]),
                    nestedValue(raw, ["member", "memberRemark"]),
                    nestedValue(raw, ["member", "memberRemarks"]),
                    nestedValue(raw, ["memberInfo", "remarks"]),
                    nestedValue(raw, ["memberInfo", "remark"]),
                    nestedValue(raw, ["memberInfo", "memberRemark"]),
                    nestedValue(raw, ["memberInfo", "memberRemarks"]),
                    nestedValue(raw, ["contact", "remarks"]),
                    nestedValue(raw, ["contact", "remark"])
                ])
                let rawFriendNo = firstCleanedValue([
                    raw["friendNo"],
                    raw["friend_no"],
                    raw["wechatNo"],
                    raw["wechat_no"],
                    raw["wechatAlias"],
                    raw["wechat_alias"],
                    raw["alias"],
                    raw["aliasName"],
                    raw["userAlias"],
                    nestedValue(raw, ["member", "friendNo"]),
                    nestedValue(raw, ["member", "friend_no"]),
                    nestedValue(raw, ["member", "wechatNo"]),
                    nestedValue(raw, ["member", "wechatAlias"]),
                    nestedValue(raw, ["member", "alias"]),
                    nestedValue(raw, ["memberInfo", "friendNo"]),
                    nestedValue(raw, ["memberInfo", "friend_no"]),
                    nestedValue(raw, ["memberInfo", "wechatNo"]),
                    nestedValue(raw, ["memberInfo", "wechat_no"]),
                    nestedValue(raw, ["memberInfo", "wechatAlias"]),
                    nestedValue(raw, ["memberInfo", "wechat_alias"]),
                    nestedValue(raw, ["memberInfo", "alias"]),
                    nestedValue(raw, ["contact", "friendNo"]),
                    nestedValue(raw, ["contact", "friend_no"]),
                    nestedValue(raw, ["contact", "wechatNo"]),
                    nestedValue(raw, ["contact", "wechatAlias"]),
                    nestedValue(raw, ["contact", "alias"]),
                    nestedValue(raw, ["user", "friendNo"]),
                    nestedValue(raw, ["user", "friend_no"]),
                    nestedValue(raw, ["user", "wechatNo"]),
                    nestedValue(raw, ["user", "wechat_no"]),
                    nestedValue(raw, ["user", "wechatAlias"]),
                    nestedValue(raw, ["user", "wechat_alias"]),
                    nestedValue(raw, ["user", "alias"])
                ])
                let friendNo = rawFriendNo.caseInsensitiveCompare(memberWxid) == .orderedSame
                    || rawFriendNo.lowercased().hasPrefix("wxid_")
                    ? ""
                    : rawFriendNo
                let compatibleNickname = firstCleanedValue([
                    nickname,
                    raw["displayName"], raw["display_name"], raw["memberDisplayName"],
                    raw["memberDisplayname"], raw["memberName"], raw["name"],
                    nestedValue(raw, ["member", "displayName"]),
                    nestedValue(raw, ["member", "name"]),
                    nestedValue(raw, ["memberInfo", "displayName"]),
                    nestedValue(raw, ["memberInfo", "name"]),
                    nestedValue(raw, ["contact", "displayName"]),
                    nestedValue(raw, ["contact", "name"])
                ])
                let displayName = OpenApiDisplay.groupMemberDisplayName(
                    remarks: remarks,
                    friendNo: friendNo,
                    nickname: compatibleNickname
                )
                return OpenApiChatRoomMemberContext(
                    ownerWxid: account.wxid,
                    chatRoomID: OpenApiDisplay.validChatRoomID(firstCleanedValue([
                        raw["chatRoomId"],
                        raw["chatroomId"],
                        raw["chatRoomID"],
                        nestedValue(raw, ["member", "chatRoomId"]),
                        nestedValue(raw, ["memberInfo", "chatRoomId"])
                    ])) ?? chatRoomID,
                    memberWxid: memberWxid,
                    friendNo: friendNo,
                    nickname: nickname,
                    displayName: displayName,
                    avatarURL: OpenApiDisplay.groupMemberAvatarURLString(from: raw),
                    remarks: remarks,
                    memberRole: int(firstCleanedValue([
                        raw["memberRole"],
                        raw["role"],
                        nestedValue(raw, ["member", "memberRole"]),
                        nestedValue(raw, ["memberInfo", "memberRole"])
                    ])),
                    isOwner: bool(raw["isOwner"]) || bool(nestedValue(raw, ["member", "isOwner"])),
                    isAdmin: bool(raw["isAdmin"]) || bool(nestedValue(raw, ["member", "isAdmin"]))
                )
            })
            page += 1
            if rawItems.count < pageSize { break }
        } while members.count < min(totalCount ?? limit, limit)
        return Array(members.prefix(limit))
    }

    private static func loadFriendRequestsIfAvailable(for account: OpenApiWeChatAccountContext, limit: Int) async -> [OpenApiFriendRequestContext] {
        do {
            return try await loadFriendRequests(for: account, limit: limit)
        } catch {
            return []
        }
    }

    private static func loadFriendRequests(for account: OpenApiWeChatAccountContext, limit: Int) async throws -> [OpenApiFriendRequestContext] {
        let pageSize = min(max(limit, 1), 100)
        let result = try await OpenApiSocialManagementService().listFriendRequests(
            account: OpenApiSocialAccount(deviceUUID: account.clientUuid, weChatID: account.wxid),
            count: pageSize,
            pendingOnly: false
        )
        let requests: [OpenApiFriendRequestContext] = arrayPayload(from: result.jsonObject).compactMap { raw -> OpenApiFriendRequestContext? in
            let requestWxid = string(raw["requestWxid"])
            guard !requestWxid.isEmpty else { return nil }
            let nickname = OpenApiDisplay.cleaned(string(raw["nickname"]))
            let ownerWxid = OpenApiDisplay.cleaned(string(raw["ownerWxid"]))
            let resolvedOwnerWxid = ownerWxid.isEmpty ? account.wxid : ownerWxid
            return OpenApiFriendRequestContext(
                participantID: OpenApiStableID.uuid(namespace: "openapi-contact", key: requestWxid),
                ownerWxid: resolvedOwnerWxid,
                requestWxid: requestWxid,
                backendID: string(raw["id"]),
                nickname: nickname.isEmpty ? requestWxid : nickname,
                avatarURL: OpenApiDisplay.avatarURLString(from: raw),
                source: string(raw["source"]),
                requestMessage: OpenApiDisplay.cleaned(string(raw["requestMessage"])),
                status: int(raw["status"]),
                requestTime: date(raw["requestTime"]) ?? date(raw["createdAt"])
            )
        }
        return Array(requests.prefix(limit))
    }

    private static func accountsWithDiscoveredAvatars(
        _ accounts: [OpenApiWeChatAccountContext],
        conversations: [OpenApiConversationContext]
    ) -> [OpenApiWeChatAccountContext] {
        var avatarByWxid: [String: String] = [:]
        for conversation in conversations {
            guard OpenApiDisplay.avatarURL(from: conversation.avatarURL) != nil,
                  avatarByWxid[conversation.wxid] == nil
            else { continue }
            avatarByWxid[conversation.wxid] = conversation.avatarURL
        }
        return accounts.map { account in
            guard OpenApiDisplay.avatarURL(from: account.avatarURL) == nil,
                  let avatarURL = avatarByWxid[account.wxid]
            else { return account }
            return account.replacingAvatarURL(avatarURL)
        }
    }

    private static func conversationsWithSharedAvatars(
        _ contexts: [OpenApiConversationContext],
        accounts: [OpenApiWeChatAccountContext]
    ) -> [OpenApiConversationContext] {
        var avatarByScopedWxid: [String: String] = [:]
        var avatarByWxid: [String: String] = [:]

        func remember(ownerWxid: String, wxid: String, avatarURL: String) {
            let cleanedWxid = OpenApiDisplay.cleaned(wxid)
            let cleanedAvatar = OpenApiDisplay.cleaned(avatarURL)
            guard !cleanedWxid.isEmpty,
                  OpenApiDisplay.avatarURL(from: cleanedAvatar) != nil
            else { return }
            if !ownerWxid.isEmpty {
                avatarByScopedWxid["\(ownerWxid)|\(cleanedWxid)", default: cleanedAvatar] = cleanedAvatar
            }
            avatarByWxid[cleanedWxid, default: cleanedAvatar] = cleanedAvatar
        }

        for account in accounts {
            remember(ownerWxid: account.wxid, wxid: account.wxid, avatarURL: account.avatarURL)
        }
        for context in contexts {
            remember(ownerWxid: context.ownerWxid, wxid: context.wxid, avatarURL: context.avatarURL)
        }

        return contexts.map { context in
            guard OpenApiDisplay.avatarURL(from: context.avatarURL) == nil else { return context }
            let scopedKey = "\(context.ownerWxid)|\(context.wxid)"
            guard let avatarURL = avatarByScopedWxid[scopedKey] ?? avatarByWxid[context.wxid],
                  OpenApiDisplay.avatarURL(from: avatarURL) != nil
            else { return context }
            return context.replacingAvatarURL(avatarURL)
        }
    }

    private static func loadRecentTasksIfAvailable(for accounts: [OpenApiWeChatAccountContext], limit: Int) async -> [OpenApiTaskResultContext] {
        do {
            return try await loadRecentTasks(for: accounts, limit: limit)
        } catch {
            return []
        }
    }

    private static func loadRecentTasks(for accounts: [OpenApiWeChatAccountContext], limit: Int) async throws -> [OpenApiTaskResultContext] {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/tasks/recent",
            query: [
                URLQueryItem(name: "count", value: "\(limit)")
            ]
        )
        let accountByWxid = Dictionary(accounts.map { ($0.wxid, $0) }, uniquingKeysWith: { current, _ in current })
        let accountByDevice = Dictionary(accounts.compactMap { account -> (String, OpenApiWeChatAccountContext)? in
            guard !account.clientUuid.isEmpty else { return nil }
            return (account.clientUuid, account)
        }, uniquingKeysWith: { current, _ in current })
        let rawItems = arrayPayload(from: result.jsonObject)
        return rawItems.compactMap { raw in
            let data = taskData(from: raw)
            let ownerWxidCandidates = [
                string(data?["effectiveWeChatId"]),
                string(data?["weChatId"]),
                string(data?["WeChatId"])
            ].map(OpenApiDisplay.cleaned)
            let deviceUuid = string(raw["deviceUuid"])
            let ownerWxid = ownerWxidCandidates.first { !$0.isEmpty }
                ?? accountByDevice[deviceUuid]?.wxid
                ?? ""
            guard !ownerWxid.isEmpty, accountByWxid[ownerWxid] != nil else { return nil }

            let conversationCandidates = [
                string(data?["effectiveFriendId"]),
                string(data?["EffectiveFriendId"]),
                string(data?["conversationId"]),
                string(data?["ConversationId"]),
                string(data?["friendId"]),
                string(data?["FriendId"]),
                string(data?["talker"]),
                string(data?["Talker"]),
                string(data?["roomId"]),
                string(data?["RoomId"]),
                string(data?["rawFriendId"]),
                string(data?["RawFriendId"])
            ].map(OpenApiDisplay.cleaned)
            guard let conversationWxid = conversationCandidates.first(where: { !$0.isEmpty }) else { return nil }

            let taskID = OpenApiDisplay.cleaned(string(raw["taskId"]))
            guard !taskID.isEmpty else { return nil }
            let participantNamespace = conversationWxid.hasSuffix("@chatroom") ? "openapi-chatroom" : "openapi-contact"
            let contentType = [
                string(data?["pendingContentType"]),
                string(data?["PendingContentType"]),
                string(data?["messageType"]),
                string(data?["MessageType"])
            ].map(OpenApiDisplay.cleaned).first { !$0.isEmpty } ?? ""

            return OpenApiTaskResultContext(
                participantID: OpenApiStableID.uuid(namespace: participantNamespace, key: conversationWxid),
                ownerWxid: ownerWxid,
                conversationWxid: conversationWxid,
                taskID: taskID,
                state: taskVisibleState(raw: raw, data: data),
                status: OpenApiDisplay.cleaned(string(raw["status"])),
                resultCode: OpenApiDisplay.cleaned(string(raw["resultCode"])),
                message: OpenApiDisplay.cleaned(string(raw["message"])),
                deviceUuid: deviceUuid,
                receivedAt: date(raw["receivedAt"]),
                contentType: contentType,
                msgSvrId: [
                    string(data?["msgSvrId"]),
                    string(data?["MsgSvrId"]),
                    string(data?["msgId"]),
                    string(data?["MsgId"])
                ].map(OpenApiDisplay.cleaned).first { !$0.isEmpty } ?? "",
                rawHidden: bool(raw["rawHidden"]),
                avatarURL: firstCleaned([
                    data.map(OpenApiDisplay.avatarURLString(from:)) ?? "",
                    OpenApiDisplay.avatarURLString(from: raw)
                ])
            )
        }
    }

    private static func taskVisibleState(raw: [String: Any], data: [String: Any]?) -> OpenApiVisibleTaskState {
        let success = optionalBool(raw["success"])
            ?? optionalBool(data?["success"])
            ?? optionalBool(raw["isSuccess"])
            ?? optionalBool(data?["isSuccess"])
            ?? optionalBool(raw["succeeded"])
            ?? optionalBool(data?["succeeded"])
        let resultUnknown = optionalBool(raw["resultUnknown"]) ?? optionalBool(data?["resultUnknown"]) ?? false
        let sentMessageID = firstCleanedValue([
            data?["msgSvrId"],
            data?["MsgSvrId"],
            data?["messageSvrId"],
            data?["MessageSvrId"],
            data?["wechatMsgId"],
            data?["WechatMsgId"],
            raw["msgSvrId"],
            raw["MsgSvrId"],
            raw["messageSvrId"],
            raw["MessageSvrId"],
            raw["wechatMsgId"],
            raw["WechatMsgId"]
        ])
        if !sentMessageID.isEmpty {
            return .succeeded
        }
        let searchable = [
            string(raw["status"]),
            string(data?["status"]),
            string(raw["state"]),
            string(data?["state"]),
            string(raw["taskStatus"]),
            string(data?["taskStatus"]),
            string(raw["resultCode"]),
            string(data?["resultCode"]),
            string(raw["code"]),
            string(data?["code"]),
            string(raw["message"]),
            string(data?["message"])
        ]
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        .filter { !$0.isEmpty }
        .joined(separator: " ")

        let pendingTerms = ["pending", "running", "processing", "queued", "created", "accepted", "wait", "progress", "处理中", "排队", "等待", "已提交", "已创建", "受理", "上传中"]
        if resultUnknown || pendingTerms.contains(where: searchable.contains) {
            return .pending
        }
        let successTerms = ["success", "succeed", "done", "completed", "finished", "ok", "sent", "已完成", "发送成功", "成功"]
        if success == true || successTerms.contains(where: searchable.contains) {
            return .succeeded
        }
        let failureTerms = [
            "fail", "error", "timeout", "cancel", "reject", "denied", "disabled", "not allowed",
            "allowchatvoicetasks=false", "allowchatvideotasks=false",
            "失败", "错误", "异常", "超时", "取消", "拒绝", "未开启", "不允许", "不可用"
        ]
        if failureTerms.contains(where: searchable.contains) {
            return .failed
        }
        return .pending
    }

    private static func firstCleaned(_ values: [String]) -> String {
        for value in values {
            let cleaned = OpenApiDisplay.cleaned(value)
            if !cleaned.isEmpty {
                return cleaned
            }
        }
        return ""
    }

    private static func firstCleanedValue(_ values: [Any?]) -> String {
        firstCleaned(values.map(string))
    }

    private static func phoneString(from raw: [String: Any]) -> String {
        firstCleanedValue([
            raw["phone"],
            raw["mobile"],
            raw["mobilePhone"],
            raw["phoneNumber"],
            raw["telephone"],
            raw["tel"],
            raw["cellphone"],
            raw["contactPhone"],
            nestedValue(raw, ["customerProfile", "phone"]),
            nestedValue(raw, ["customerProfile", "mobile"]),
            nestedValue(raw, ["customerProfile", "mobilePhone"]),
            nestedValue(raw, ["customerProfile", "phoneNumber"]),
            nestedValue(raw, ["customerProfile", "telephone"]),
            nestedValue(raw, ["profile", "phone"]),
            nestedValue(raw, ["profile", "mobile"]),
            nestedValue(raw, ["contact", "phone"]),
            nestedValue(raw, ["contact", "mobile"]),
            nestedValue(raw, ["friend", "phone"]),
            nestedValue(raw, ["friend", "mobile"])
        ])
    }

    private static func nestedValue(_ raw: [String: Any], _ path: [String]) -> Any? {
        var current: Any? = raw
        for key in path {
            guard let dictionary = current as? [String: Any] else { return nil }
            current = dictionary[key]
        }
        return current
    }

    private static func uniqueConversations(_ contexts: [OpenApiConversationContext]) -> [OpenApiConversationContext] {
        var seen = Set<UUID>()
        return contexts.filter { seen.insert($0.participantID).inserted }
    }

    private static func uniqueFriendRequests(_ contexts: [OpenApiFriendRequestContext]) -> [OpenApiFriendRequestContext] {
        var seen = Set<String>()
        return contexts.filter {
            let key = "\($0.ownerWxid)-\($0.requestWxid)-\($0.backendID)"
            return seen.insert(key).inserted
        }
    }

    private static func uniqueTaskResults(_ contexts: [OpenApiTaskResultContext]) -> [OpenApiTaskResultContext] {
        var seen = Set<String>()
        return contexts.filter { seen.insert($0.taskID).inserted }
    }

    private static func arrayPayload(from object: Any?) -> [[String: Any]] {
        if let array = object as? [[String: Any]] {
            return array
        }
        guard let root = object as? [String: Any] else { return [] }

        // The service has used several list envelopes across endpoint versions:
        // `data.items`, `data.content`, and a direct `members` array all occur
        // in otherwise equivalent responses.
        for key in ["items", "data", "records", "rows", "list", "content", "members", "results"] {
            if let items = root[key] as? [[String: Any]] {
                return items
            }
        }
        for key in ["data", "result", "payload", "item", "record"] {
            if let nested = root[key] as? [String: Any] {
                let items = arrayPayload(from: nested)
                if !items.isEmpty {
                    return items
                }
            }
        }
        return []
    }

    private static func dictionaryPayload(from object: Any?) -> [String: Any] {
        guard let root = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload", "item", "record"] {
            if let nested = root[key] as? [String: Any] {
                return nested
            }
        }
        return root
    }

    private static func taskData(from raw: [String: Any]) -> [String: Any]? {
        if let data = raw["data"] as? [String: Any] {
            return data
        }
        let message = string(raw["message"])
        guard let markerRange = message.range(of: "Data=") else {
            return nil
        }
        let suffix = String(message[markerRange.upperBound...])
        guard let startIndex = suffix.firstIndex(of: "{") else {
            return nil
        }
        var depth = 0
        var endIndex: String.Index?
        var index = startIndex
        while index < suffix.endIndex {
            let character = suffix[index]
            if character == "{" {
                depth += 1
            } else if character == "}" {
                depth -= 1
                if depth == 0 {
                    endIndex = index
                    break
                }
            }
            index = suffix.index(after: index)
        }
        guard let endIndex else { return nil }
        let jsonText = String(suffix[startIndex...endIndex])
        guard let data = jsonText.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return nil
        }
        return object
    }

    private static func totalPayloadCount(from object: Any?) -> Int? {
        if let root = object as? [String: Any] {
            if let total = root["totalCount"] { return int(total) }
            if let total = root["total"] { return int(total) }
            if let data = root["data"] as? [String: Any] {
                if let total = data["totalCount"] { return int(total) }
                if let total = data["total"] { return int(total) }
            }
        }
        return nil
    }

    private static func string(_ value: Any?) -> String {
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }

    private static func int(_ value: Any?) -> Int {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let int = Int(string) { return int }
        return 0
    }

    private static func bool(_ value: Any?) -> Bool {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            switch string.lowercased() {
            case "true", "1", "yes", "success": return true
            default: return false
            }
        }
        return false
    }

    private static func optionalBool(_ value: Any?) -> Bool? {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            switch string.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "true", "1", "yes", "success", "succeeded", "ok": return true
            case "false", "0", "no", "fail", "failed", "error": return false
            default: return nil
            }
        }
        return nil
    }

    private static func date(_ value: Any?) -> Date? {
        if let date = value as? Date { return date }
        if let number = value as? NSNumber {
            let seconds = number.doubleValue > 9_999_999_999 ? number.doubleValue / 1000 : number.doubleValue
            return Date(timeIntervalSince1970: seconds)
        }
        guard let string = value as? String,
              !string.isEmpty,
              string != "0001-01-01T00:00:00"
        else { return nil }

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = isoFormatter.date(from: string) {
            return date
        }
        isoFormatter.formatOptions = [.withInternetDateTime]
        if let date = isoFormatter.date(from: string) {
            return date
        }
        return nil
    }
}
