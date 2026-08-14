import UIKit

struct OpenApiChatContentParser {
    let senderWxid: String
    let content: String

    static func parse(_ content: String, chatType: Int) -> OpenApiChatContentParser {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard chatType == 2,
              let separator = trimmed.firstIndex(of: ":")
        else {
            return OpenApiChatContentParser(senderWxid: "", content: trimmed)
        }

        let prefix = String(trimmed[..<separator])
        let suffix = String(trimmed[trimmed.index(after: separator)...])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let looksLikeSenderID = !prefix.isEmpty
            && prefix.count <= 96
            && !prefix.contains(where: \.isWhitespace)
            && !prefix.contains("/")
            && (prefix.hasPrefix("wxid_")
                || prefix.contains("@")
                || prefix.range(of: "^[A-Za-z0-9_-]+$", options: .regularExpression) != nil)
        guard looksLikeSenderID, !suffix.isEmpty else {
            return OpenApiChatContentParser(senderWxid: "", content: trimmed)
        }
        return OpenApiChatContentParser(senderWxid: prefix, content: suffix)
    }
}

extension ChatWindowViewController {
    func loadOpenApiChatSyncCheckpoint() {
        openApiChatSyncCheckpoint = ChatSQLiteStore.shared.codable(
            OpenApiChatSyncCheckpoint.self,
            forKey: Self.openApiChatSyncCheckpointStorageKey
        ) ?? OpenApiChatSyncCheckpoint()
        if ChatSQLiteStore.shared.string(forKey: Self.openApiChatMappingVersionStorageKey)
            != Self.openApiChatMappingVersion {
            openApiChatSyncCheckpoint.nextSequenceByAccountWxid.removeAll()
            ChatSQLiteStore.shared.setString(
                Self.openApiChatMappingVersion,
                forKey: Self.openApiChatMappingVersionStorageKey
            )
        }
        hasCompletedOpenApiChatBootstrap = openApiChatSyncCheckpoint.lastSuccessfulSyncAt != nil
    }

    func scheduleOpenApiChatMessageSync() {
        guard openApiChatSyncTask == nil,
              !openApiAccountContextsByID.isEmpty,
              OpenApiConfiguration.current.apiKey.isEmpty == false
        else { return }

        let accounts = openApiAccountContextsByID.values.sorted {
            if $0.isOnline != $1.isOnline { return $0.isOnline && !$1.isOnline }
            return $0.wxid.localizedStandardCompare($1.wxid) == .orderedAscending
        }
        openApiChatSyncTask = Task { @MainActor [weak self] in
            guard let self else { return }
            self.isOpenApiChatSyncing = true
            defer {
                self.isOpenApiChatSyncing = false
                self.openApiChatSyncTask = nil
                self.startOpenApiChatPolling()
            }

            var didReachBackend = false
            for account in accounts {
                guard !Task.isCancelled else { return }
                do {
                    let bootstrap = try await OpenApiChatSynchronizer.loadBootstrap(for: account)
                    self.registerOpenApiChatConversations(bootstrap.conversations, for: account)
                    self.openApiChatSyncCheckpoint.conversationsByAccountWxid[account.wxid] = bootstrap.conversations
                    self.openApiChatSyncCheckpoint.bootstrapConversationCountByAccountWxid[account.wxid] = bootstrap.conversations.count

                    let previousSequence = self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid[account.wxid]
                    if previousSequence == nil {
                        let unreadConversations = bootstrap.conversations
                            .filter { $0.unreadCount > 0 }
                            .sorted {
                                ($0.lastMessageTime ?? $0.updatedAt ?? .distantPast)
                                    > ($1.lastMessageTime ?? $1.updatedAt ?? .distantPast)
                            }
                        for conversation in unreadConversations.prefix(50) {
                            guard !Task.isCancelled else { return }
                            let pageSize = max(20, min(100, conversation.unreadCount + 10))
                            let history = try await OpenApiChatSynchronizer.loadHistory(
                                for: account,
                                conversation: conversation,
                                pageSize: pageSize
                            )
                            self.applyOpenApiChatMessages(
                                history.messages,
                                account: account,
                                knownConversations: bootstrap.conversations
                            )
                        }
                    }

                    var nextSequence = previousSequence ?? bootstrap.baselineSequence
                    let drainedChanges = try await self.drainOpenApiChatChanges(
                        for: account,
                        afterSequence: nextSequence,
                        knownConversations: bootstrap.conversations
                    )
                    nextSequence = drainedChanges.nextSequence
                    self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid[account.wxid] = nextSequence
                    self.openApiChatSyncCheckpoint.lastErrorByAccountWxid.removeValue(forKey: account.wxid)
                    didReachBackend = true
                    self.persistOpenApiChatSyncCheckpoint()
                } catch {
                    if self.isExpiredOpenApiChatCursorError(error) {
                        self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid.removeValue(forKey: account.wxid)
                    }
                    self.openApiChatSyncCheckpoint.lastErrorByAccountWxid[account.wxid] = error.localizedDescription
                    self.persistOpenApiChatSyncCheckpoint()
                }
            }

            if didReachBackend {
                self.hasCompletedOpenApiChatBootstrap = true
                self.openApiChatSyncCheckpoint.lastSuccessfulSyncAt = Date()
                self.persistOpenApiChatSyncCheckpoint()
                self.unreadPreviewMessages.removeAll()
                self.refreshUIAfterOpenApiMessageSync()
            }
        }
    }

    func scheduleOpenApiChatMessageSyncWhenIdle(delay: TimeInterval = 0.7) {
        openApiChatSyncStartWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self,
                  self.viewIfLoaded?.window != nil
            else { return }
            self.openApiChatSyncStartWorkItem = nil
            if self.isConnectionScrollActive() {
                self.scheduleOpenApiChatMessageSyncWhenIdle(delay: 0.35)
                return
            }
            self.scheduleOpenApiChatMessageSync()
        }
        openApiChatSyncStartWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: workItem)
    }

    func startOpenApiChatPolling() {
        guard openApiChatPollTimer == nil, !openApiAccountContextsByID.isEmpty else { return }
        openApiChatPollTimer = Timer.scheduledTimer(
            withTimeInterval: Self.openApiChatPollingInterval,
            repeats: true
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.pollOpenApiChatChanges()
            }
        }
        RunLoop.main.add(openApiChatPollTimer!, forMode: .common)
    }

    func pollOpenApiChatChanges() {
        guard openApiChatSyncTask == nil,
              !isOpenApiChatSyncing,
              viewIfLoaded?.window != nil
        else { return }

        let accounts = openApiAccountContextsByID.values.sorted { $0.wxid < $1.wxid }
        guard !accounts.isEmpty else { return }
        guard accounts.allSatisfy({ openApiChatSyncCheckpoint.nextSequenceByAccountWxid[$0.wxid] != nil }) else {
            scheduleOpenApiChatMessageSync()
            return
        }
        openApiChatSyncTask = Task { @MainActor [weak self] in
            guard let self else { return }
            self.isOpenApiChatSyncing = true
            defer {
                self.isOpenApiChatSyncing = false
                self.openApiChatSyncTask = nil
            }

            var didChangeMessages = false
            var didReachBackend = false
            for account in accounts {
                guard !Task.isCancelled else { return }
                guard let sequence = self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid[account.wxid],
                      let conversations = self.openApiChatSyncCheckpoint.conversationsByAccountWxid[account.wxid]
                else {
                    continue
                }
                do {
                    let drainedChanges = try await self.drainOpenApiChatChanges(
                        for: account,
                        afterSequence: sequence,
                        knownConversations: conversations
                    )
                    self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid[account.wxid] = drainedChanges.nextSequence
                    self.openApiChatSyncCheckpoint.lastErrorByAccountWxid.removeValue(forKey: account.wxid)
                    didChangeMessages = didChangeMessages || drainedChanges.changedMessageCount > 0
                    didReachBackend = true
                } catch {
                    if self.isExpiredOpenApiChatCursorError(error) {
                        self.openApiChatSyncCheckpoint.nextSequenceByAccountWxid.removeValue(forKey: account.wxid)
                    }
                    self.openApiChatSyncCheckpoint.lastErrorByAccountWxid[account.wxid] = error.localizedDescription
                }
            }
            if didReachBackend {
                self.openApiChatSyncCheckpoint.lastSuccessfulSyncAt = Date()
            }
            self.persistOpenApiChatSyncCheckpoint()
            if didChangeMessages {
                self.refreshUIAfterOpenApiMessageSync()
            }
        }
    }

    func syncOpenApiHistoryForSelectedConversation(
        _ conversationID: UUID?,
        loadsOlderMessages: Bool = false
    ) {
        guard let conversationID else { return }
        let isGroup = state.participants.first(where: { $0.id == conversationID }).map(isGroupConversation) ?? false
        let accountIDs: [UUID]
        if isGroup {
            accountIDs = state.currentUsers.map(\.id).filter {
                openApiRelatedAccountIDsByConversationID[conversationID]?.contains($0) == true
            }
        } else {
            accountIDs = [state.selectedAccountID]
        }

        for accountID in accountIDs {
            guard let account = openApiAccountContextsByID[accountID],
                  let descriptor = openApiChatDescriptor(conversationID: conversationID, account: account)
            else { continue }
            let syncKey = "\(account.wxid)|\(descriptor.conversationWxid)"
            if loadsOlderMessages, openApiHistoryExhaustedKeys.contains(syncKey) { continue }
            guard openApiHistorySyncKeys.insert(syncKey).inserted else { continue }

            Task { @MainActor [weak self] in
                guard let self else { return }
                defer { self.openApiHistorySyncKeys.remove(syncKey) }
                let groupMemberHydrationKey = "\(account.wxid)|\(descriptor.conversationWxid)"
                let shouldHydrateGroupMembers = descriptor.isGroup
                    && !loadsOlderMessages
                    && !self.openApiHydratedGroupMemberKeys.contains(groupMemberHydrationKey)
                    && self.openApiGroupMemberHydrationInFlightKeys.insert(groupMemberHydrationKey).inserted
                defer {
                    if shouldHydrateGroupMembers {
                        self.openApiGroupMemberHydrationInFlightKeys.remove(groupMemberHydrationKey)
                    }
                }
                do {
                    let cursor = loadsOlderMessages ? self.openApiHistoryNextCursorByKey[syncKey] : nil
                    if loadsOlderMessages, cursor == nil { return }
                    if shouldHydrateGroupMembers,
                       let group = self.state.participants.first(where: { $0.id == conversationID }) {
                        let groupContext = self.openApiConversationContextsByAccountID[conversationID]?[account.participantID]
                            ?? self.openApiConversationContextsByID[conversationID]
                        do {
                            let memberContexts = try await OpenApiIMSynchronizer.loadChatroomMembers(
                                for: account,
                                chatRoomID: descriptor.conversationWxid,
                                // Fetch the complete member window for the currently
                                // opened group so historical senders can be rebound.
                                // This remains on-demand; startup does not fan out.
                                limit: 500
                            )
                            if !memberContexts.isEmpty {
                                self.recordOpenApiGroupMemberSyncDiagnostic(
                                    account: account,
                                    chatRoomID: descriptor.conversationWxid,
                                    result: "selected-success",
                                    memberCount: memberContexts.count,
                                    members: memberContexts
                                )
                                self.applyOpenApiGroupMembers(memberContexts, group: group, owner: account)
                            } else if let groupContext {
                                let fallback = self.fallbackOpenApiTwoPersonGroupMembers(
                                    account: account,
                                    context: groupContext
                                )
                                if !fallback.isEmpty {
                                    self.recordOpenApiGroupMemberSyncDiagnostic(
                                        account: account,
                                        chatRoomID: descriptor.conversationWxid,
                                        result: "selected-empty-response-fallback",
                                        memberCount: fallback.count,
                                        members: fallback
                                    )
                                    self.applyOpenApiGroupMembers(fallback, group: group, owner: account)
                                } else {
                                    self.recordOpenApiGroupMemberSyncDiagnostic(
                                        account: account,
                                        chatRoomID: descriptor.conversationWxid,
                                        result: "selected-empty-response",
                                        memberCount: 0
                                    )
                                }
                            }
                        } catch {
                            let fallback = groupContext.map {
                                self.fallbackOpenApiTwoPersonGroupMembers(
                                    account: account,
                                    context: $0
                                )
                            } ?? []
                            if !fallback.isEmpty {
                                self.recordOpenApiGroupMemberSyncDiagnostic(
                                    account: account,
                                    chatRoomID: descriptor.conversationWxid,
                                    result: "selected-request-failed-fallback",
                                    memberCount: fallback.count,
                                    error: error
                                )
                                self.applyOpenApiGroupMembers(fallback, group: group, owner: account)
                            } else {
                                self.recordOpenApiGroupMemberSyncDiagnostic(
                                    account: account,
                                    chatRoomID: descriptor.conversationWxid,
                                    result: "selected-request-failed",
                                    memberCount: 0,
                                    error: error
                                )
                            }
                        }
                    }
                    self.messageCollectionView.layoutIfNeeded()
                    let oldContentHeight = self.messageCollectionView.collectionViewLayout.collectionViewContentSize.height
                    let oldContentOffsetY = self.messageCollectionView.contentOffset.y
                    let history = try await OpenApiChatSynchronizer.loadHistory(
                        for: account,
                        conversation: descriptor,
                        cursor: cursor,
                        pageSize: 100
                    )
                    if history.hasMore, let nextCursor = history.nextCursor, !nextCursor.isEmpty {
                        self.openApiHistoryNextCursorByKey[syncKey] = nextCursor
                        self.openApiHistoryExhaustedKeys.remove(syncKey)
                    } else {
                        self.openApiHistoryNextCursorByKey.removeValue(forKey: syncKey)
                        self.openApiHistoryExhaustedKeys.insert(syncKey)
                    }
                    let imported = self.applyOpenApiChatMessages(
                        history.messages,
                        account: account,
                        knownConversations: self.openApiChatSyncCheckpoint.conversationsByAccountWxid[account.wxid] ?? []
                    )
                    if imported > 0 {
                        self.persistOpenApiChatSyncCheckpoint()
                        self.refreshUIAfterOpenApiMessageSync(
                            scrollToBottom: !loadsOlderMessages,
                            preserving: loadsOlderMessages ? (oldContentHeight, oldContentOffsetY) : nil
                        )
                    }
                } catch {
                    self.openApiChatSyncCheckpoint.lastErrorByAccountWxid[account.wxid] = error.localizedDescription
                    self.persistOpenApiChatSyncCheckpoint()
                }
            }
        }
    }

    func loadOlderOpenApiHistoryIfNeeded() {
        guard !isHomeTimeline,
              let conversationID = state.selectedFriendID
        else {
            openApiHistoryPaginationTriggerConversationID = nil
            return
        }
        if messageCollectionView.contentOffset.y > 260 {
            openApiHistoryPaginationTriggerConversationID = nil
            return
        }
        guard messageCollectionView.contentOffset.y < 160,
              openApiHistoryPaginationTriggerConversationID != conversationID
        else { return }
        openApiHistoryPaginationTriggerConversationID = conversationID
        syncOpenApiHistoryForSelectedConversation(conversationID, loadsOlderMessages: true)
    }

    private func openApiChatDescriptor(
        conversationID: UUID,
        account: OpenApiWeChatAccountContext
    ) -> OpenApiChatConversationDescriptor? {
        if let descriptor = openApiChatSyncCheckpoint.conversationsByAccountWxid[account.wxid]?
            .first(where: { $0.participantID == conversationID }) {
            return descriptor
        }
        guard let context = openApiConversationContext(
            conversationID: conversationID,
            accountID: account.participantID
        ) else { return nil }
        return OpenApiChatConversationDescriptor(
            backendID: Int64(context.backendID) ?? 0,
            accountWxid: account.wxid,
            conversationWxid: context.wxid,
            conversationType: context.kind == .chatroom ? 2 : 1,
            displayName: context.displayName,
            displayAvatar: context.avatarURL,
            unreadCount: 0,
            messageCount: 0,
            isPinned: false,
            isMuted: false,
            lastMessageContent: "",
            lastMessageTime: nil,
            updatedAt: nil
        )
    }

    private func drainOpenApiChatChanges(
        for account: OpenApiWeChatAccountContext,
        afterSequence: Int64,
        knownConversations: [OpenApiChatConversationDescriptor]
    ) async throws -> (nextSequence: Int64, changedMessageCount: Int) {
        var sequence = afterSequence
        var changedMessageCount = 0
        for _ in 0..<20 {
            let changes = try await OpenApiChatSynchronizer.loadChanges(
                for: account,
                afterSequence: sequence
            )
            changedMessageCount += applyOpenApiChatMessages(
                changes.messages,
                account: account,
                knownConversations: knownConversations
            )
            let advancedSequence = max(sequence, changes.nextSequence)
            if !changes.hasMore || advancedSequence == sequence {
                return (advancedSequence, changedMessageCount)
            }
            sequence = advancedSequence
        }
        return (sequence, changedMessageCount)
    }

    @discardableResult
    private func applyOpenApiChatMessages(
        _ payloads: [OpenApiChatMessagePayload],
        account: OpenApiWeChatAccountContext,
        knownConversations: [OpenApiChatConversationDescriptor]
    ) -> Int {
        guard !payloads.isEmpty else { return 0 }
        let descriptorsByBackendID = Dictionary(
            knownConversations.map { ($0.backendID, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        let descriptorsByWxid = Dictionary(
            knownConversations.map { ($0.conversationWxid, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        var changedCount = 0

        for payload in payloads {
            let descriptor = resolvedOpenApiChatConversation(
                for: payload,
                account: account,
                byBackendID: descriptorsByBackendID,
                byWxid: descriptorsByWxid
            )
            guard let descriptor else { continue }
            registerOpenApiChatConversations([descriptor], for: account)
            let messageID = resolvedOpenApiChatMessageID(
                payload,
                account: account,
                conversation: descriptor
            )

            if payload.isDeleted {
                let oldCount = state.messages.count
                state.messages.removeAll { $0.id == messageID }
                changedCount += oldCount == state.messages.count ? 0 : 1
                continue
            }

            var message = makeOpenApiChatMessage(
                payload,
                messageID: messageID,
                account: account,
                conversation: descriptor
            )
            if let index = state.messages.firstIndex(where: { $0.id == messageID }) {
                if message.unansweredMetadata == nil,
                   let existingMetadata = state.messages[index].unansweredMetadata {
                    message = message.replacingUnansweredMetadata(existingMetadata)
                }
                if !openApiChatMessagesHaveSameContent(state.messages[index], message) {
                    state.messages[index] = message
                    changedCount += 1
                }
            } else {
                state.messages.append(message)
                changedCount += 1
            }
        }

        guard changedCount > 0 else { return 0 }
        state.messages.sort(by: messageSortAscending)
        openApiChatSyncCheckpoint.importedMessageCount += changedCount
        persistMessages()
        hydrateOpenApiGroupMessageSenders()
        return changedCount
    }

    private func registerOpenApiChatConversations(
        _ conversations: [OpenApiChatConversationDescriptor],
        for account: OpenApiWeChatAccountContext
    ) {
        for descriptor in conversations {
            let participantID = descriptor.participantID
            let kind: OpenApiConversationKind = descriptor.isGroup ? .chatroom : .contact
            let existingContext = openApiConversationContextsByAccountID[participantID]?[account.participantID]
            let context = existingContext ?? OpenApiConversationContext(
                participantID: participantID,
                ownerWxid: account.wxid,
                wxid: descriptor.conversationWxid,
                backendID: descriptor.backendID == 0 ? "" : "\(descriptor.backendID)",
                displayName: descriptor.displayName.isEmpty ? descriptor.conversationWxid : descriptor.displayName,
                friendNo: "",
                avatarURL: descriptor.displayAvatar,
                remark: "",
                source: "chat-bootstrap",
                sourceExt: "",
                customerLevel: "",
                sourceChannel: "",
                profileKey: "",
                phone: "",
                notes: "",
                kind: kind,
                memberCount: nil
            )
            openApiConversationContextsByAccountID[participantID, default: [:]][account.participantID] = context
            openApiConversationContextsByID[participantID, default: context] = context
            openApiRelatedAccountIDsByConversationID[participantID, default: []].insert(account.participantID)

            if descriptor.isGroup {
                var memberIDs = explicitGroupMemberIDsByGroupID[participantID] ?? []
                if !memberIDs.contains(account.participantID) {
                    memberIDs.append(account.participantID)
                    explicitGroupMemberIDsByGroupID[participantID] = memberIDs
                }
            }

            if !state.participants.contains(where: { $0.id == participantID }) {
                state.participants.append(ChatParticipant(
                    id: participantID,
                    displayName: context.displayName,
                    tintColor: context.tintColor,
                    initials: context.initials,
                    isCurrentUser: false,
                    avatarURL: context.avatar,
                    kind: descriptor.isGroup ? .group : .contact
                ))
            }
            if state.contactCards[participantID] == nil {
                state.contactCards[participantID] = ContactCardProfile(
                    accountID: participantID,
                    displayName: context.displayName,
                    role: descriptor.isGroup ? "OpenAPI 群聊" : "OpenAPI 好友",
                    company: "只发 SCRM",
                    wechatID: descriptor.conversationWxid,
                    phone: "",
                    bio: "来自 OpenAPI 聊天同步。",
                    styleIndex: safeHashIndex(descriptor.conversationWxid, modulo: 4)
                )
            }
        }
    }

    private func resolvedOpenApiChatConversation(
        for payload: OpenApiChatMessagePayload,
        account: OpenApiWeChatAccountContext,
        byBackendID: [Int64: OpenApiChatConversationDescriptor],
        byWxid: [String: OpenApiChatConversationDescriptor]
    ) -> OpenApiChatConversationDescriptor? {
        if let backendID = payload.conversationBackendID,
           let descriptor = byBackendID[backendID] {
            return descriptor
        }

        let extensionWxid = openApiMessageExtension(
            payload,
            keys: ["conversationWxid", "conversationId", "talker", "roomId", "chatRoomId"]
        )
        let directionalWxid = payload.senderWxid == account.wxid
            ? payload.receiverWxid
            : payload.senderWxid
        let candidates = [extensionWxid, payload.receiverWxid, directionalWxid]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != account.wxid }
        if let descriptor = candidates.compactMap({ byWxid[$0] }).first {
            return descriptor
        }

        guard let wxid = candidates.first else { return nil }
        let isGroup = wxid.lowercased().hasSuffix("@chatroom") || payload.chatType == 2
        return OpenApiChatConversationDescriptor(
            backendID: payload.conversationBackendID ?? 0,
            accountWxid: account.wxid,
            conversationWxid: wxid,
            conversationType: isGroup ? 2 : 1,
            displayName: wxid,
            displayAvatar: "",
            unreadCount: 0,
            messageCount: 0,
            isPinned: false,
            isMuted: false,
            lastMessageContent: payload.content,
            lastMessageTime: payload.sentAt,
            updatedAt: payload.updatedAt
        )
    }

    private func makeOpenApiChatMessage(
        _ payload: OpenApiChatMessagePayload,
        messageID: UUID,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiChatConversationDescriptor
    ) -> ChatMessage {
        let isOutgoing = payload.senderWxid == account.wxid
            || (payload.senderWxid.isEmpty && payload.direction == 1)
        let sender = openApiMessageSender(
            payload,
            account: account,
            conversation: conversation,
            isOutgoing: isOutgoing
        )
        let detectedType = openApiChatMessageType(payload)
        let sentAt = payload.sentAt ?? payload.receivedAt ?? payload.createdAt ?? payload.updatedAt ?? Date()
        let body = openApiChatMessageBody(payload, type: detectedType)
        let type: ChatMessageType
        switch body {
        case "[图片]": type = .image
        case "[表情]", "[动画表情]": type = .stickerGif
        case "[视频]": type = .video
        case "[语音]": type = .voice
        default: type = detectedType
        }
        let attachmentURL = openApiMessageAttachmentURL(payload)
        let detail: String
        if payload.isRevoked {
            detail = ""
        } else if type == .voice, !payload.voiceText.isEmpty {
            detail = payload.voiceText
        } else if type == .file {
            detail = payload.media.first?.fileExtension.uppercased() ?? ""
        } else {
            detail = ""
        }
        return ChatMessage(
            id: messageID,
            conversationID: conversation.participantID,
            type: payload.isRevoked ? .system : type,
            sender: sender,
            body: payload.isRevoked ? "消息已撤回" : body,
            detail: detail,
            isOutgoing: payload.isRevoked ? false : isOutgoing,
            presentation: isOutgoing ? .bare : .avatarOnly,
            timestamp: ChatMessage.displayTimestamp(for: sentAt),
            sentAt: sentAt,
            backendMessageID: payload.messageServerID.map(String.init),
            attachmentURL: attachmentURL,
            isGroupConversation: conversation.isGroup,
            richElements: payload.isRevoked ? [] : richElements(from: body),
            recipientAccountID: account.participantID,
            unansweredMetadata: conversation.isGroup
                ? openApiUnansweredMetadata(payload, account: account)
                : nil
        )
    }

    private func openApiUnansweredMetadata(
        _ payload: OpenApiChatMessagePayload,
        account: OpenApiWeChatAccountContext
    ) -> UnansweredMessageMetadata? {
        let mentions = openApiMessageExtension(
            payload,
            keys: ["mentionWxids", "mentionedWxids", "mentions", "atWxids", "atUserList"]
        )
        let assignees = openApiMessageExtension(
            payload,
            keys: ["assigneeWxids", "assignees", "ownerWxids", "responsibleWxids"]
        )
        let requiresReplyText = openApiMessageExtension(
            payload,
            keys: ["requiresReply", "needReply", "replyRequired"]
        ).lowercased()
        let requiresReply = ["1", "true", "yes"].contains(requiresReplyText)
        let mentionedAccountIDs = mentions.contains(account.wxid) ? [account.participantID] : []
        let assignedAccountIDs = assignees.contains(account.wxid) ? [account.participantID] : []
        let threadText = openApiMessageExtension(
            payload,
            keys: ["threadId", "topicId", "threadKey"]
        )
        let itemText = openApiMessageExtension(
            payload,
            keys: ["itemId", "taskId", "businessId", "replyToMessageId", "quotedMessageId"]
        )
        let threadID = threadText.isEmpty ? nil : threadText
        let itemID = itemText.isEmpty ? nil : itemText

        guard requiresReply
                || !mentionedAccountIDs.isEmpty
                || !assignedAccountIDs.isEmpty
                || threadID != nil
                || itemID != nil
        else { return nil }
        return UnansweredMessageMetadata(
            mentionedAccountIDs: mentionedAccountIDs,
            assignedAccountIDs: assignedAccountIDs,
            threadID: threadID,
            itemID: itemID,
            requiresReply: requiresReply
        )
    }

    private func resolvedOpenApiChatMessageID(
        _ payload: OpenApiChatMessagePayload,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiChatConversationDescriptor
    ) -> UUID {
        let backendStableID = payload.stableID(accountWxid: account.wxid)
        let isOutgoing = payload.senderWxid == account.wxid
            || (payload.senderWxid.isEmpty && payload.direction == 1)
        guard isOutgoing else { return backendStableID }

        for candidate in [payload.clientMessageID, payload.localMessageID] {
            if let uuid = UUID(uuidString: candidate), state.messages.contains(where: { $0.id == uuid }) {
                return uuid
            }
        }
        if let serverID = payload.messageServerID {
            let serverIDText = "\(serverID)"
            if let record = openApiDeliveryStatusesByMessageID.values.first(where: {
                $0.conversationID == conversation.participantID && $0.note.contains(serverIDText)
            }) {
                return record.messageID
            }
        }

        let backendSentAt = payload.sentAt ?? payload.createdAt ?? payload.receivedAt
        let normalizedBody = openApiChatMessageBody(payload, type: openApiChatMessageType(payload))
        if let local = state.messages.last(where: { message in
            guard message.conversationID == conversation.participantID,
                  message.isOutgoing,
                  message.sender.id == account.participantID,
                  (isAutoReplyPrediction(message)
                    ? suggestedReplyText(fromAutoReplyBody: message.body)
                    : message.body) == normalizedBody
            else { return false }
            guard let backendSentAt else { return true }
            return abs(message.sentAt.timeIntervalSince(backendSentAt)) <= 120
        }) {
            return local.id
        }
        return backendStableID
    }

    private func openApiMessageSender(
        _ payload: OpenApiChatMessagePayload,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiChatConversationDescriptor,
        isOutgoing: Bool
    ) -> ChatParticipant {
        if isOutgoing,
           let accountParticipant = state.participants.first(where: { $0.id == account.participantID }) {
            return accountParticipant
        }
        if !conversation.isGroup,
           let participant = state.participants.first(where: { $0.id == conversation.participantID }) {
            return participant
        }

        let parsedContent = OpenApiChatContentParser.parse(payload.content, chatType: payload.chatType)
        let senderWxid = parsedContent.senderWxid.isEmpty
            ? (payload.senderWxid.isEmpty ? "unknown" : payload.senderWxid)
            : parsedContent.senderWxid
        let senderID = OpenApiStableID.uuid(
            namespace: "openapi-group-member",
            key: "\(conversation.conversationWxid)|\(senderWxid)"
        )
        if let participant = state.participants.first(where: { $0.id == senderID }) {
            return participant
        }
        let displayName = openApiMessageExtension(
            payload,
            keys: ["senderDisplayName", "senderNickname", "memberName", "nickname", "displayName"]
        )
        let senderRemark = openApiMessageExtension(
            payload,
            keys: ["senderRemark", "senderRemarks", "memberRemark", "memberRemarks", "remark", "remarks"]
        )
        let senderFriendNo = openApiMessageExtension(
            payload,
            keys: ["senderFriendNo", "friendNo", "friend_no", "wechatNo", "wechat_no", "wechatAlias", "alias"]
        )
        let avatarText = openApiMessageExtension(
            payload,
            keys: ["senderAvatar", "senderAvatarUrl", "avatarUrl", "headImgUrl"]
        )
        let messageJSON = openApiMessageJSON(payload)
        let jsonDisplayName = [
            messageJSON?["senderDisplayName"] as? String,
            messageJSON?["senderNickname"] as? String,
            messageJSON?["nickname"] as? String,
            messageJSON?["displayName"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["displayName"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["nickname"] as? String
        ].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty }) ?? ""
        let jsonAvatarText = messageJSON.map(OpenApiDisplay.groupMemberAvatarURLString(from:)) ?? ""
        let contactContext = openApiConversationContextsByID.values.first {
            $0.kind == .contact
                && $0.ownerWxid.caseInsensitiveCompare(account.wxid) == .orderedSame
                && $0.wxid.caseInsensitiveCompare(senderWxid) == .orderedSame
        }
        func isPresentableSenderName(_ value: String) -> Bool {
            let cleaned = OpenApiDisplay.cleaned(value)
            return !cleaned.isEmpty
                && cleaned != "群成员"
                && cleaned.lowercased() != "unknown"
                && !cleaned.lowercased().hasPrefix("wxid_")
                && !cleaned.hasSuffix("@chatroom")
                && cleaned.caseInsensitiveCompare(senderWxid) != .orderedSame
        }
        let contactNames = [
            contactContext?.remark ?? "",
            contactContext?.friendNo ?? "",
            contactContext?.displayName ?? ""
        ]
        let jsonRemark = [
            messageJSON?["senderRemark"] as? String,
            messageJSON?["senderRemarks"] as? String,
            messageJSON?["remark"] as? String,
            messageJSON?["remarks"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["remark"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["remarks"] as? String
        ].compactMap { $0 }.first ?? ""
        let jsonFriendNo = [
            messageJSON?["senderFriendNo"] as? String,
            messageJSON?["friendNo"] as? String,
            messageJSON?["friend_no"] as? String,
            messageJSON?["wechatNo"] as? String,
            messageJSON?["wechatAlias"] as? String,
            messageJSON?["alias"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["friendNo"] as? String,
            (messageJSON?["sender"] as? [String: Any])?["wechatNo"] as? String
        ].compactMap { $0 }.first ?? ""
        let apiName = (contactNames + [senderRemark, senderFriendNo, jsonRemark, jsonFriendNo, displayName, jsonDisplayName])
            .map(OpenApiDisplay.cleaned)
            .first(where: isPresentableSenderName)
        let fallbackName: String
        if senderWxid.hasSuffix("@chatroom") || senderWxid == conversation.conversationWxid {
            fallbackName = "群通知"
        } else if senderWxid.isEmpty
                    || senderWxid.lowercased() == "unknown"
                    || senderWxid.lowercased().hasPrefix("wxid_") {
            // Keep an unresolved backend identifier out of visible UI. The
            // member endpoint will bind a real remark, WeChat number or nickname.
            fallbackName = ""
        } else {
            fallbackName = senderWxid
        }
        let resolvedName = apiName ?? fallbackName
        let participant = ChatParticipant(
            id: senderID,
            displayName: resolvedName,
            tintColor: OpenApiDisplay.color(for: "\(conversation.conversationWxid)|\(senderWxid)"),
            initials: OpenApiDisplay.initials(from: resolvedName, fallback: "员"),
            isCurrentUser: false,
            avatarURL: OpenApiDisplay.avatarURL(from: avatarText)
                ?? OpenApiDisplay.avatarURL(from: jsonAvatarText)
                ?? contactContext?.avatar,
            kind: .groupMember
        )
        state.participants.append(participant)
        var memberIDs = explicitGroupMemberIDsByGroupID[conversation.participantID] ?? []
        if !memberIDs.contains(senderID) {
            memberIDs.append(senderID)
            explicitGroupMemberIDsByGroupID[conversation.participantID] = memberIDs
        }
        return participant
    }

    private func openApiChatMessageType(_ payload: OpenApiChatMessagePayload) -> ChatMessageType {
        if let json = openApiMessageJSON(payload) {
            if json["members"] != nil { return .groupNotice }
            if json["appMessageItems"] != nil { return .article }
            if json["PagePath"] != nil || json["SourceUsername"] != nil { return .miniProgram }
            if json["mediaList"] != nil { return .channelsVideo }
            let typeText = [json["TypeStr"], json["typeStr"]]
                .compactMap { $0 as? String }
                .joined(separator: " ")
            if typeText.contains("文件") { return .file }
            if typeText.contains("视频") { return .video }
            if typeText.contains("小程序") { return .miniProgram }
            if typeText.contains("链接") { return .webLink }
            if json["url"] != nil || json["Url"] != nil { return .webLink }
        }
        switch payload.messageType {
        case 1: return .text
        case 3: return .image
        case 34: return .voice
        case 37, 42: return .contactCard
        case 43, 62: return .video
        case 47: return .stickerGif
        case 48: return .location
        case 49:
            if payload.content.contains("<type>33</type>") || payload.content.contains("<type>36</type>") {
                return .miniProgram
            }
            if payload.content.contains("<type>6</type>") { return .file }
            if payload.content.contains("<type>19</type>") { return .mergedForward }
            if payload.content.contains("<type>2000</type>") { return .transfer }
            if payload.content.contains("<type>2001</type>") { return .redPacket }
            return .webLink
        case 50: return .voiceCall
        case 10000, 10002: return .system
        default:
            return payload.content.isEmpty ? .system : .text
        }
    }

    private func openApiChatMessageBody(
        _ payload: OpenApiChatMessagePayload,
        type: ChatMessageType
    ) -> String {
        let content = OpenApiChatContentParser.parse(payload.content, chatType: payload.chatType).content
        if let json = openApiMessageJSON(payload) {
            if let items = json["appMessageItems"] as? [[String: Any]],
               let firstTitle = items.first?["title"] as? String,
               !firstTitle.isEmpty {
                return items.count > 1 ? "\(firstTitle) 等 \(items.count) 篇" : firstTitle
            }
            for key in ["title", "Title", "content", "des", "Des", "displayName"] {
                if let text = json[key] as? String,
                   !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    return text
                }
            }
            return type == .groupNotice ? "[群通知]" : "[卡片消息]"
        }
        if !content.isEmpty {
            return content
        }
        if type == .voice, !payload.voiceText.isEmpty { return payload.voiceText }
        switch type {
        case .image, .capturedPhoto: return "[图片]"
        case .voice: return "[语音]"
        case .video: return "[视频]"
        case .file: return "[文件]"
        case .stickerGif, .emoji: return "[表情]"
        case .location, .liveLocation: return "[位置]"
        case .contactCard: return "[名片]"
        case .miniProgram: return "[小程序]"
        case .webLink, .article: return "[链接]"
        default: return "[消息]"
        }
    }

    private func openApiMessageAttachmentURL(_ payload: OpenApiChatMessagePayload) -> URL? {
        var value = openApiMessageExtension(
            payload,
            keys: ["url", "mediaUrl", "downloadUrl", "fileUrl", "imageUrl", "videoUrl", "voiceUrl"]
        )
        if value.isEmpty {
            value = payload.media.lazy.map(\.url).first { !$0.isEmpty } ?? ""
        }
        if value.isEmpty, let json = openApiMessageJSON(payload) {
            value = (json["url"] as? String)
                ?? (json["Url"] as? String)
                ?? (json["Thumb"] as? String)
                ?? ""
        }
        if value.isEmpty {
            let content = OpenApiChatContentParser.parse(payload.content, chatType: payload.chatType).content
                .replacingOccurrences(of: "&amp;", with: "&")
            if let range = content.range(of: #"https?://[^\s\"'<>]+"#, options: .regularExpression) {
                value = String(content[range])
            }
        }
        guard let url = URL(string: value), ["http", "https", "file"].contains(url.scheme?.lowercased() ?? "") else {
            return nil
        }
        return url
    }

    /// Fetches a media URL only after the user opens a historical media message
    /// that arrived without one. This keeps bootstrap/history startup bounded.
    @discardableResult
    func resolveOpenApiMediaIfNeeded(for message: ChatMessage) -> Bool {
        let isMediaMessage: Bool
        switch message.type {
        case .image, .capturedPhoto, .stickerGif, .video, .voice, .file:
            isMediaMessage = true
        default:
            isMediaMessage = false
        }
        guard isMediaMessage, message.attachmentURL == nil else { return false }
        guard let backendMessageID = message.backendMessageID,
              !backendMessageID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              backendMessageID != "0"
        else {
            return false
        }

        let accountID = message.recipientAccountID ?? state.selectedAccountID
        guard let account = openApiAccountContextsByID[accountID] else {
            showNotice("当前消息没有可用的 OpenAPI 帐号")
            return true
        }
        guard openApiConversationContextsByAccountID[message.conversationID]?[account.participantID] != nil
                || openApiConversationContextsByID[message.conversationID] != nil
        else {
            showNotice("当前消息没有可用的 OpenAPI 会话")
            return true
        }

        showNotice("正在读取消息媒体")
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let resource = try await OpenApiMessageService().fetchMedia(
                    account: OpenApiSocialAccount(
                        deviceUUID: account.clientUuid,
                        weChatID: account.wxid
                    ),
                    messageID: backendMessageID
                )
                guard let index = self.state.messages.firstIndex(where: { $0.id == message.id }) else { return }
                let updated = self.replacingMessage(message, attachmentURL: resource.url)
                self.state.messages[index] = updated
                self.persistMessageMutations([.update(updated)])
                self.invalidateVisibleDataCaches()
                self.reloadRenderedMessageItem(messageID: updated.id)
                self.performMessageAction(updated)
            } catch {
                self.showNotice("媒体接口暂不可用：\(error.localizedDescription)")
            }
        }
        return true
    }

    private func openApiMessageExtension(
        _ payload: OpenApiChatMessagePayload,
        keys: [String]
    ) -> String {
        let normalized = Dictionary(
            payload.extensions.map { ($0.key.lowercased(), $0.value) },
            uniquingKeysWith: { current, _ in current }
        )
        for key in keys {
            let value = normalized[key.lowercased()]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !value.isEmpty { return value }
        }
        return ""
    }

    private func openApiMessageJSON(_ payload: OpenApiChatMessagePayload) -> [String: Any]? {
        let text = OpenApiChatContentParser.parse(payload.content, chatType: payload.chatType).content
        guard text.hasPrefix("{"),
              let data = text.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return object
    }

    private func openApiChatMessagesHaveSameContent(_ lhs: ChatMessage, _ rhs: ChatMessage) -> Bool {
        lhs.conversationID == rhs.conversationID
            && lhs.sender.id == rhs.sender.id
            && lhs.type == rhs.type
            && lhs.body == rhs.body
            && lhs.detail == rhs.detail
            && lhs.isOutgoing == rhs.isOutgoing
            && lhs.sentAt == rhs.sentAt
            && lhs.attachmentURL == rhs.attachmentURL
            && lhs.recipientAccountID == rhs.recipientAccountID
            && lhs.unansweredMetadata == rhs.unansweredMetadata
    }

    func normalizePersistedOpenApiGroupMessages() {
        state.messages = state.messages.map { message in
            let normalizedMessage: ChatMessage
            switch message.body {
            case "[图片]": normalizedMessage = message.replacingType(.image)
            case "[表情]", "[动画表情]": normalizedMessage = message.replacingType(.stickerGif)
            case "[视频]": normalizedMessage = message.replacingType(.video)
            case "[语音]": normalizedMessage = message.replacingType(.voice)
            default: normalizedMessage = message
            }
            guard normalizedMessage.isGroupConversation else { return normalizedMessage }
            if normalizedMessage.isOutgoing {
                let currentName = normalizedMessage.sender.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                guard currentName.hasPrefix("wxid_") || currentName.hasSuffix("@chatroom") else { return normalizedMessage }
                let account = normalizedMessage.recipientAccountID.flatMap { openApiAccountContextsByID[$0] }
                    ?? openApiAccountContextsByID[normalizedMessage.sender.id]
                let sender = account.flatMap { account in
                    state.participants.first(where: { $0.id == account.participantID })
                } ?? ChatParticipant(
                    id: normalizedMessage.sender.id,
                    displayName: "我",
                    tintColor: normalizedMessage.sender.tintColor,
                    initials: "我",
                    isCurrentUser: true,
                    avatarURL: normalizedMessage.sender.avatarURL,
                    kind: normalizedMessage.sender.kind
                )
                return normalizedMessage.replacingSender(sender)
            }
            let message = normalizedMessage
            let parsed = OpenApiChatContentParser.parse(message.body, chatType: 2)
            if parsed.senderWxid.isEmpty {
                let currentName = message.sender.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                let fallbackName: String?
                if currentName.hasSuffix("@chatroom") {
                    fallbackName = "群通知"
                } else if currentName.hasPrefix("wxid_") || currentName == "unknown" {
                    fallbackName = ""
                } else {
                    fallbackName = nil
                }
                guard let fallbackName else { return message }
                let sender = ChatParticipant(
                    id: message.sender.id,
                    displayName: fallbackName,
                    tintColor: message.sender.tintColor,
                    initials: OpenApiDisplay.initials(from: fallbackName, fallback: "员"),
                    isCurrentUser: false,
                    avatarURL: message.sender.avatarURL,
                    kind: .groupMember
                )
                return message.replacingSender(sender)
            }
            guard !parsed.senderWxid.isEmpty,
                  let accountID = message.recipientAccountID,
                  let account = openApiAccountContextsByID[accountID],
                  let context = openApiConversationContextsByAccountID[message.conversationID]?[accountID]
                    ?? openApiConversationContextsByID[message.conversationID]
            else { return message }

            let conversation = OpenApiChatConversationDescriptor(
                backendID: Int64(context.backendID) ?? 0,
                accountWxid: account.wxid,
                conversationWxid: context.wxid,
                conversationType: 2,
                displayName: context.displayName,
                displayAvatar: context.avatarURL,
                unreadCount: 0,
                messageCount: 0,
                isPinned: false,
                isMuted: false,
                lastMessageContent: message.body,
                lastMessageTime: message.sentAt,
                updatedAt: message.sentAt
            )
            let payload = OpenApiChatMessagePayload(
                messageID: 0,
                messageServerID: nil,
                conversationBackendID: Int64(context.backendID),
                senderWxid: context.wxid,
                receiverWxid: account.wxid,
                chatType: 2,
                messageType: 1,
                content: message.body,
                direction: 0,
                sendStatus: nil,
                readStatus: nil,
                isRevoked: false,
                isDeleted: false,
                localMessageID: "",
                clientMessageID: "",
                sentAt: message.sentAt,
                receivedAt: nil,
                readAt: nil,
                revokedAt: nil,
                createdAt: message.sentAt,
                updatedAt: message.sentAt,
                media: [],
                extensions: [:],
                voiceText: ""
            )
            let parsedType = openApiChatMessageType(payload)
            let type: ChatMessageType
            switch message.body {
            case "[图片]": type = .image
            case "[表情]", "[动画表情]": type = .stickerGif
            case "[视频]": type = .video
            case "[语音]": type = .voice
            default: type = message.type == .text ? parsedType : message.type
            }
            let body = openApiChatMessageBody(payload, type: type)
            return ChatMessage(
                id: message.id,
                conversationID: message.conversationID,
                type: type,
                sender: openApiMessageSender(
                    payload,
                    account: account,
                    conversation: conversation,
                    isOutgoing: false
                ),
                body: body,
                detail: message.detail,
                isOutgoing: false,
                presentation: message.presentation,
                timestamp: message.timestamp,
                sentAt: message.sentAt,
                backendMessageID: message.backendMessageID,
                attachmentURL: openApiMessageAttachmentURL(payload) ?? message.attachmentURL,
                isAI: message.isAI,
                isGroupConversation: true,
                richElements: richElements(from: body),
                contactCard: message.contactCard,
                contactCardAccountID: message.contactCardAccountID,
                quotedMessageID: message.quotedMessageID,
                mergedForwardMessages: message.mergedForwardMessages,
                recipientAccountID: message.recipientAccountID,
                unansweredMetadata: message.unansweredMetadata
            )
        }
    }

    private func recordOpenApiGroupMemberSyncDiagnostic(
        account: OpenApiWeChatAccountContext,
        chatRoomID: String,
        result: String,
        memberCount: Int,
        members: [OpenApiChatRoomMemberContext] = [],
        error: Error? = nil
    ) {
        let errorText = error?.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let encodedError = errorText.replacingOccurrences(of: "|", with: "/")
        let memberSummary = members.prefix(4).map {
            let name = OpenApiDisplay.cleaned($0.displayName)
            let wxid = OpenApiDisplay.cleaned($0.memberWxid)
            return "\(wxid),\(name),avatar=\(!$0.avatarURL.isEmpty)"
        }.joined(separator: ";")
        let value = [
            ISO8601DateFormatter().string(from: Date()),
            account.wxid,
            chatRoomID,
            result,
            "members=\(memberCount)",
            memberSummary,
            encodedError
        ].joined(separator: "|")
        // Keep the last result in the phone database so a real-device export
        // can distinguish an empty response from a failed request.
        ChatSQLiteStore.shared.setString(
            value,
            forKey: Self.openApiGroupMemberDiagnosticsStorageKey
        )
    }

    private func fallbackOpenApiTwoPersonGroupMembers(
        account: OpenApiWeChatAccountContext,
        context: OpenApiConversationContext
    ) -> [OpenApiChatRoomMemberContext] {
        guard context.memberCount == 2 else { return [] }
        let marker = context.notes
            .split(separator: ";")
            .first(where: { $0.hasPrefix("ownerMemberWxid=") })
            .map { String($0.dropFirst("ownerMemberWxid=".count)) }
            .map(OpenApiDisplay.cleaned) ?? ""
        guard !marker.isEmpty,
              marker.caseInsensitiveCompare(account.wxid) != .orderedSame,
              let contact = openApiConversationContextsByID.values.first(where: {
                  $0.kind == .contact
                      && $0.ownerWxid.caseInsensitiveCompare(account.wxid) == .orderedSame
                      && $0.wxid.caseInsensitiveCompare(marker) == .orderedSame
              })
        else { return [] }

        return [
            OpenApiChatRoomMemberContext(
                ownerWxid: account.wxid,
                chatRoomID: context.wxid,
                memberWxid: contact.wxid,
                friendNo: contact.friendNo,
                nickname: contact.displayName,
                displayName: contact.displayName,
                avatarURL: contact.avatarURL,
                remarks: contact.remark,
                memberRole: 0,
                isOwner: false,
                isAdmin: false
            )
        ]
    }

    func hydrateOpenApiGroupMessageSenders() {
        // Group membership is an on-demand enrichment. Do not fan out one
        // members request per historical group during snapshot/bootstrap.
        guard let selectedConversationID = state.selectedFriendID else { return }
        let candidates = state.messages.compactMap { message -> (String, ChatParticipant, OpenApiWeChatAccountContext, OpenApiConversationContext)? in
            guard message.isGroupConversation,
                  message.conversationID == selectedConversationID,
                  !message.isOutgoing,
                  let accountID = message.recipientAccountID,
                  let account = openApiAccountContextsByID[accountID],
                  let group = state.participants.first(where: { $0.id == message.conversationID }),
                  let context = openApiConversationContextsByAccountID[message.conversationID]?[accountID]
                    ?? openApiConversationContextsByID[message.conversationID]
            else { return nil }
            let key = "\(account.wxid)|\(context.wxid)"
            guard !openApiHydratedGroupMemberKeys.contains(key),
                  openApiGroupMemberHydrationInFlightKeys.insert(key).inserted
            else { return nil }
            return (key, group, account, context)
        }
        let uniqueCandidates = Dictionary(candidates.map { ($0.0, $0) }, uniquingKeysWith: { current, _ in current })
            .values
            .sorted { $0.0 < $1.0 }
        guard !uniqueCandidates.isEmpty else { return }

        Task { @MainActor [weak self] in
            guard let self else { return }
            defer {
                for (key, _, _, _) in uniqueCandidates {
                    self.openApiGroupMemberHydrationInFlightKeys.remove(key)
                }
            }
            for (_, group, account, context) in uniqueCandidates {
                guard !Task.isCancelled else { return }
                do {
                    let members = try await OpenApiIMSynchronizer.loadChatroomMembers(
                        for: account,
                        chatRoomID: context.wxid,
                        limit: 500
                    )
                    if !members.isEmpty {
                        self.recordOpenApiGroupMemberSyncDiagnostic(
                            account: account,
                            chatRoomID: context.wxid,
                            result: "success",
                            memberCount: members.count,
                            members: members
                        )
                        self.applyOpenApiGroupMembers(members, group: group, owner: account)
                    } else {
                        let fallback = self.fallbackOpenApiTwoPersonGroupMembers(
                            account: account,
                            context: context
                        )
                        if !fallback.isEmpty {
                            self.recordOpenApiGroupMemberSyncDiagnostic(
                                account: account,
                                chatRoomID: context.wxid,
                                result: "empty-response-fallback",
                                memberCount: fallback.count,
                                members: fallback
                            )
                            self.applyOpenApiGroupMembers(fallback, group: group, owner: account)
                        } else {
                            self.recordOpenApiGroupMemberSyncDiagnostic(
                                account: account,
                                chatRoomID: context.wxid,
                                result: "empty-response",
                                memberCount: 0
                            )
                        }
                    }
                } catch {
                    let fallback = self.fallbackOpenApiTwoPersonGroupMembers(
                        account: account,
                        context: context
                    )
                    if !fallback.isEmpty {
                        self.recordOpenApiGroupMemberSyncDiagnostic(
                            account: account,
                            chatRoomID: context.wxid,
                            result: "request-failed-fallback",
                            memberCount: fallback.count,
                            error: error
                        )
                        self.applyOpenApiGroupMembers(fallback, group: group, owner: account)
                    } else {
                        self.recordOpenApiGroupMemberSyncDiagnostic(
                            account: account,
                            chatRoomID: context.wxid,
                            result: "request-failed",
                            memberCount: 0,
                            error: error
                        )
                    }
                    continue
                }
            }
        }
    }

    private func persistOpenApiChatSyncCheckpoint() {
        ChatSQLiteStore.shared.setCodableAsync(
            openApiChatSyncCheckpoint,
            forKey: Self.openApiChatSyncCheckpointStorageKey
        )
    }

    func refreshUIAfterOpenApiMessageSync(
        scrollToBottom: Bool = false,
        preserving scrollAnchor: (contentHeight: CGFloat, contentOffsetY: CGFloat)? = nil
    ) {
        if isHomeTimeline {
            guard !isUnreadFoldExpansionInProgress else {
                needsUnreadTimelineRefreshAfterExpansion = true
                return
            }
            let oldMessages = renderedMessagesCache
            let oldLeftItems = visibleLeftItemsCache
            let oldOffsetY = messageCollectionView.contentOffset.y
            let groupAnchor = unreadFoldPostExpansionAnchor
                ?? unreadTimelineLayoutCache.flatMap {
                    UnreadTimelineGroupViewportAnchor.captureVisible(
                        snapshot: $0,
                        contentOffsetY: oldOffsetY
                    )
                }
            invalidateVisibleDataCaches()
            // Existing heights are content-keyed. Keeping them avoids a full
            // timeline remeasurement when polling adds only a few messages.
            guard !applyUnreadTimelineExternalRefresh(
                oldMessages: oldMessages,
                oldLeftItems: oldLeftItems,
                groupAnchor: groupAnchor,
                fallbackContentOffsetY: oldOffsetY
            ) else { return }

            leftCollectionView.reloadData()
            messageCollectionView.reloadData()
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.messageCollectionView.layoutIfNeeded()
                self.leftCollectionView.layoutIfNeeded()
                self.updateLeftCollectionBounceInsets()
                self.syncUnreadTimelineScroll(from: self.messageCollectionView)
                self.scheduleConnectionUpdate()
            }
            return
        }
        invalidateVisibleDataCaches()
        schedulePendingReplyIndexRebuild()
        clearMessageHeightCache()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        messageCollectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.messageCollectionView.layoutIfNeeded()
            if let scrollAnchor {
                let newContentHeight = self.messageCollectionView.collectionViewLayout.collectionViewContentSize.height
                let minimumOffsetY = -self.messageCollectionView.adjustedContentInset.top
                self.messageCollectionView.setContentOffset(
                    CGPoint(
                        x: self.messageCollectionView.contentOffset.x,
                        y: max(minimumOffsetY, scrollAnchor.contentOffsetY + newContentHeight - scrollAnchor.contentHeight)
                    ),
                    animated: false
                )
            } else if scrollToBottom, let indexPath = self.lastRenderedMessageIndexPath() {
                self.messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: false)
            }
            self.updateConnections()
        }
    }

    private func isExpiredOpenApiChatCursorError(_ error: Error) -> Bool {
        guard case OpenApiHTTPClient.ClientError.badStatus(let status, _) = error else { return false }
        return status == 410
    }
}
