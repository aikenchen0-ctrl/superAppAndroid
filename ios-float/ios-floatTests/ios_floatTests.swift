import XCTest
import UIKit
@testable import ios_float

final class ios_floatTests: XCTestCase {
    func testOpenApiContactDisplayNamePrefersRemarkThenWechatNumber() {
        XCTAssertEqual(
            OpenApiDisplay.contactDisplayName(remark: "客户备注", friendNo: "wechat_no", nickname: "昵称"),
            "客户备注"
        )
        XCTAssertEqual(
            OpenApiDisplay.contactDisplayName(remark: "", friendNo: "wechat_no", nickname: "昵称"),
            "wechat_no"
        )
        XCTAssertEqual(
            OpenApiDisplay.contactDisplayName(remark: "", friendNo: "", nickname: "真实昵称"),
            "真实昵称"
        )
    }

    func testOpenApiGroupMemberDisplayNameNeverUsesBackendPlaceholder() {
        XCTAssertEqual(
            OpenApiDisplay.groupMemberDisplayName(remarks: "群备注", friendNo: "wechat_no", nickname: "昵称"),
            "群备注"
        )
        XCTAssertEqual(
            OpenApiDisplay.groupMemberDisplayName(remarks: "", friendNo: "wechat_no", nickname: "昵称"),
            "wechat_no"
        )
        XCTAssertEqual(
            OpenApiDisplay.groupMemberDisplayName(remarks: "", friendNo: "", nickname: "真实昵称"),
            "真实昵称"
        )
        XCTAssertEqual(
            OpenApiDisplay.groupMemberDisplayName(remarks: "", friendNo: "", nickname: "wxid_internal"),
            ""
        )
    }

    func testOpenApiAvatarMappingPrefersSemanticContactAndMemberFields() {
        let contact = OpenApiDisplay.contactAvatarURLString(from: [
            "icon": "https://example.test/generic.png",
            "contact": ["headImgUrl": "https://example.test/contact.png"]
        ])
        let member = OpenApiDisplay.groupMemberAvatarURLString(from: [
            "imageUrl": "https://example.test/generic.png",
            "memberInfo": ["avatarUrl": "https://example.test/member.png"]
        ])

        XCTAssertEqual(contact, "https://example.test/contact.png")
        XCTAssertEqual(member, "https://example.test/member.png")
    }

    func testOpenApiSnapshotKeepsHydratedGroupMemberPresentation() throws {
        let account = OpenApiWeChatAccountContext(
            participantID: UUID(),
            wxid: "owner_wxid",
            nickname: "主帐号",
            clientUuid: "device",
            accountStatus: 1,
            avatarURL: "https://example.test/account.png"
        )
        let group = OpenApiConversationContext(
            participantID: OpenApiStableID.uuid(namespace: "openapi-chatroom", key: "room@chatroom"),
            ownerWxid: account.wxid,
            wxid: "room@chatroom",
            backendID: "42",
            displayName: "群聊",
            friendNo: "",
            avatarURL: "",
            remark: "",
            source: "",
            sourceExt: "",
            customerLevel: "",
            sourceChannel: "",
            profileKey: "",
            phone: "",
            notes: "",
            kind: .chatroom,
            memberCount: 2
        )
        let accountParticipant = ChatParticipant(
            id: account.participantID,
            displayName: account.nickname,
            tintColor: account.tintColor,
            initials: account.initials,
            isCurrentUser: true,
            avatarURL: account.avatar,
            kind: .account
        )
        let groupParticipant = ChatParticipant(
            id: group.participantID,
            displayName: group.displayName,
            tintColor: group.tintColor,
            initials: group.initials,
            avatarURL: group.avatar,
            kind: .group
        )
        let memberID = OpenApiStableID.uuid(
            namespace: "openapi-group-member",
            key: "room@chatroom|member_wxid"
        )
        let memberAvatar = try XCTUnwrap(URL(string: "https://example.test/member.png"))
        let member = ChatParticipant(
            id: memberID,
            displayName: "群内备注",
            tintColor: .systemBlue,
            initials: "群",
            avatarURL: memberAvatar,
            kind: .groupMember
        )
        let contactMemberID = OpenApiStableID.uuid(
            namespace: "openapi-contact",
            key: "contact_wxid"
        )
        let contactMemberAvatar = try XCTUnwrap(URL(string: "https://example.test/contact-member.png"))
        let contactMember = ChatParticipant(
            id: contactMemberID,
            displayName: "群内联系人备注",
            tintColor: .systemIndigo,
            initials: "联",
            avatarURL: contactMemberAvatar,
            kind: .contact
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: group.participantID,
            type: .text,
            sender: member,
            body: "成员消息",
            detail: "",
            isOutgoing: false,
            presentation: .avatarAndName,
            timestamp: "12:00",
            sentAt: Date(),
            isGroupConversation: true,
            recipientAccountID: account.participantID
        )

        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.state = ChatDemoState(
            participants: [accountParticipant, groupParticipant, member, contactMember],
            messages: [message],
            currentUserID: account.participantID,
            selectedAccountID: account.participantID,
            contactCards: [:],
            selectedFriendID: group.participantID,
            rightToolItems: []
        )
        controller.isMessagePersistenceReady = true
        controller.explicitGroupMemberIDsByGroupID[group.participantID] = [
            account.participantID,
            memberID,
            contactMemberID
        ]
        let hydrationKey = "\(account.wxid)|\(group.wxid)"
        controller.openApiHydratedGroupMemberKeys = [hydrationKey]

        let snapshot = OpenApiIMSnapshot(
            accounts: [account],
            contacts: [
                OpenApiConversationContext(
                    participantID: contactMemberID,
                    ownerWxid: account.wxid,
                    wxid: "contact_wxid",
                    backendID: "43",
                    displayName: "快照联系人名称",
                    friendNo: "contact_no",
                    avatarURL: "https://example.test/snapshot-contact.png",
                    remark: "",
                    source: "",
                    sourceExt: "",
                    customerLevel: "",
                    sourceChannel: "",
                    profileKey: "",
                    phone: "",
                    notes: "",
                    kind: .contact,
                    memberCount: nil
                )
            ],
            chatrooms: [group],
            friendRequests: [],
            taskResults: []
        )
        XCTAssertTrue(controller.applyOpenApiIMSnapshot(snapshot, shouldPersistSnapshot: false))

        let retainedMember = try XCTUnwrap(
            controller.state.participants.first { $0.id == memberID }
        )
        XCTAssertEqual(retainedMember.kind, .groupMember)
        XCTAssertEqual(retainedMember.displayName, "群内备注")
        XCTAssertEqual(retainedMember.avatarURL, memberAvatar)
        let retainedContactMember = try XCTUnwrap(
            controller.state.participants.first { $0.id == contactMemberID }
        )
        XCTAssertEqual(retainedContactMember.kind, .contact)
        XCTAssertEqual(retainedContactMember.displayName, "群内联系人备注")
        XCTAssertEqual(retainedContactMember.avatarURL, contactMemberAvatar)
        XCTAssertEqual(controller.state.messages.first?.sender.displayName, "群内备注")
        XCTAssertEqual(controller.state.messages.first?.sender.avatarURL, memberAvatar)
        XCTAssertEqual(controller.explicitGroupMemberIDsByGroupID[group.participantID], [
            account.participantID,
            memberID,
            contactMemberID
        ])
        XCTAssertTrue(controller.openApiHydratedGroupMemberKeys.contains(hydrationKey))
    }

    func testAutoReplyDraftStateControlsWhetherDraftCanBeSent() {
        let controller = ChatWindowViewController()
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )

        func draft(state: String?) -> ChatMessage {
            ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: .text,
                sender: account,
                body: "回复正文",
                detail: ["AI自动回复预测", state.map { "草稿状态：\($0)" }]
                    .compactMap { $0 }
                    .joined(separator: "\n"),
                isOutgoing: true,
                presentation: .avatarOnly,
                timestamp: "",
                sentAt: Date(),
                isAI: true,
                recipientAccountID: account.id
            )
        }

        XCTAssertTrue(controller.canSendAutoReplyDraft(draft(state: nil)))
        XCTAssertTrue(controller.canSendAutoReplyDraft(draft(state: "ready")))
        XCTAssertTrue(controller.canSendAutoReplyDraft(draft(state: "sendFailed")))
        XCTAssertFalse(controller.canSendAutoReplyDraft(draft(state: "generating")))
        XCTAssertFalse(controller.canSendAutoReplyDraft(draft(state: "generationFailed")))
        XCTAssertFalse(controller.canSendAutoReplyDraft(draft(state: "sending")))
        XCTAssertFalse(controller.canSendAutoReplyDraft(draft(state: "sendUnconfirmed")))
    }

    func testKnownMessageMutationKeepsPlannerInSync() {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let original = ChatMessage(
            id: UUID(),
            conversationID: UUID(),
            type: .text,
            sender: account,
            body: "原消息",
            detail: "",
            isOutgoing: true,
            presentation: .avatarOnly,
            timestamp: "",
            sentAt: Date(),
            recipientAccountID: account.id
        )
        var planner = MessageMutationPlanner()

        planner.applyKnownMutations([.insert(original)])
        XCTAssertTrue(planner.plan(for: [original]).isEmpty)

        let updated = ChatMessage(
            id: original.id,
            conversationID: original.conversationID,
            type: original.type,
            sender: original.sender,
            body: "更新消息",
            detail: original.detail,
            isOutgoing: original.isOutgoing,
            presentation: original.presentation,
            timestamp: original.timestamp,
            sentAt: original.sentAt,
            recipientAccountID: original.recipientAccountID
        )
        planner.applyKnownMutations([.update(updated)])
        XCTAssertTrue(planner.plan(for: [updated]).isEmpty)

        planner.applyKnownMutations([
            .delete(messageID: updated.id, affectedConversationID: updated.conversationID)
        ])
        XCTAssertTrue(planner.plan(for: []).isEmpty)
    }

    func testIncrementalMessageInsertionKeepsCollectionStateConsistent() {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(),
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let first = ChatMessage(
            id: UUID(),
            conversationID: friend.id,
            type: .text,
            sender: friend,
            body: "第一条",
            detail: "",
            isOutgoing: false,
            presentation: .avatarOnly,
            timestamp: "",
            sentAt: Date(timeIntervalSince1970: 1),
            recipientAccountID: account.id
        )
        let second = ChatMessage(
            id: UUID(),
            conversationID: friend.id,
            type: .text,
            sender: account,
            body: "第二条",
            detail: "",
            isOutgoing: true,
            presentation: .avatarOnly,
            timestamp: "",
            sentAt: Date(timeIntervalSince1970: 2),
            recipientAccountID: account.id
        )
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.state = ChatDemoState(
            participants: [account, friend],
            messages: [first],
            currentUserID: account.id,
            selectedAccountID: account.id,
            contactCards: [:],
            selectedFriendID: friend.id,
            rightToolItems: []
        )
        controller.invalidateVisibleDataCaches()
        controller.messageCollectionView.reloadData()
        controller.messageCollectionView.layoutIfNeeded()
        let previousIDs = controller.renderedMessages.map(\.id)

        controller.state.messages.append(second)
        controller.invalidateVisibleDataCaches()
        controller.insertRenderedMessageItemIfPossible(
            messageID: second.id,
            previousMessageIDs: previousIDs,
            scrollToInsertedMessage: false
        )

        let completed = expectation(description: "batch insertion completed")
        DispatchQueue.main.async {
            XCTAssertEqual(controller.messageCollectionView.numberOfItems(inSection: 0), 2)
            XCTAssertEqual(controller.renderedMessages.map(\.id), [first.id, second.id])
            completed.fulfill()
        }
        wait(for: [completed], timeout: 1)
    }

    func testOpenApiOfflineErrorIsEligibleForConnectionRecovery() {
        let controller = ChatWindowViewController()
        let offlineError = NSError(
            domain: "OpenAPI",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "发送文本失败：设备未在线或连接不存在"]
        )
        let unknownResultError = NSError(
            domain: "OpenAPI",
            code: 2,
            userInfo: [NSLocalizedDescriptionKey: "等待手机端回包超时，结果未知"]
        )
        let noOnlineWeChatError = NSError(
            domain: "OpenAPI",
            code: 3,
            userInfo: [NSLocalizedDescriptionKey: "发送文本失败：设备没有在线微信账号，无法发送消息"]
        )

        XCTAssertTrue(controller.isOpenApiDeviceOfflineError(offlineError))
        XCTAssertTrue(controller.isOpenApiDeviceOfflineError(noOnlineWeChatError))
        XCTAssertFalse(controller.isOpenApiDeviceOfflineError(unknownResultError))
        XCTAssertTrue(controller.isOpenApiTaskResultUnknown(unknownResultError))
        XCTAssertTrue(controller.openApiTaskResultMessageIsUnknown(
            "手机端可能仍在继续执行，请勿立即重复发送同一内容"
        ))
    }

    func testOpenApiDeliveryStatusPresentationAndTaskHistory() {
        XCTAssertEqual(
            OpenApiDeliveryStatusPresentation.text(state: .pending, note: "正在发送"),
            "发送中"
        )
        XCTAssertEqual(
            OpenApiDeliveryStatusPresentation.text(state: .pending, note: "已提交，等待任务终态"),
            "等待确认"
        )
        XCTAssertEqual(
            OpenApiDeliveryStatusPresentation.text(state: .failed, note: "设备离线"),
            "失败，长按重试"
        )
        XCTAssertNil(OpenApiDeliveryStatusPresentation.text(state: .succeeded, note: "完成"))
        XCTAssertEqual(
            OpenApiDeliveryStatusPresentation.mergedTaskIDs(
                ["old-task", "shared-task"],
                ["shared-task", "new-task", ""]
            ),
            ["old-task", "shared-task", "new-task"]
        )
    }

    func testFailedAutoReplyDraftUsesDraftRetryFlow() {
        let controller = ChatWindowViewController()
        let account = ChatParticipant(
            id: UUID(), displayName: "主帐号", tintColor: .systemGreen,
            initials: "主", isCurrentUser: true, kind: .account
        )
        let draft = ChatMessage(
            id: UUID(), conversationID: UUID(), type: .text, sender: account,
            body: "AI建议回复：收到", detail: "AI自动回复预测\n草稿状态：sendFailed",
            isOutgoing: true, presentation: .avatarOnly, timestamp: "", sentAt: Date(),
            isAI: true, recipientAccountID: account.id
        )
        let normalMessage = ChatMessage(
            id: UUID(), conversationID: draft.conversationID, type: .text, sender: account,
            body: "普通回复", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(), recipientAccountID: account.id
        )

        XCTAssertTrue(controller.usesAutoReplyDraftRetryFlow(for: draft))
        XCTAssertFalse(controller.usesAutoReplyDraftRetryFlow(for: normalMessage))
    }

    func testPendingReplyPresentationLimitsDirectConversationToThreeMessages() {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(),
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let messages = (0..<8).map { index in
            ChatMessage(
                id: UUID(),
                conversationID: friend.id,
                type: .text,
                sender: friend,
                body: "消息 \(index)",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                recipientAccountID: account.id
            )
        }

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: [account.id],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.map(\.body), (0..<3).map { "消息 \($0)" })
        XCTAssertEqual(presentation.hiddenDirectMessageCountByConversationID[friend.id], 5)
        XCTAssertEqual(presentation.directMessageCountByConversationID[friend.id], 8)
    }

    func testPendingReplyPresentationFiltersDirectMessagesByAccount() {
        let firstAccountID = UUID()
        let secondAccountID = UUID()
        let friendID = UUID()
        let friend = ChatParticipant(
            id: friendID,
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let first = ChatMessage(
            id: UUID(), conversationID: friendID, type: .text, sender: friend,
            body: "帐号一", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            recipientAccountID: firstAccountID
        )
        let second = ChatMessage(
            id: UUID(), conversationID: friendID, type: .text, sender: friend,
            body: "帐号二", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            recipientAccountID: secondAccountID
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [first, second],
            allMessages: [first, second],
            currentAccountIDs: [firstAccountID, secondAccountID],
            accountFilterID: secondAccountID,
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.map(\.id), [second.id])
    }

    func testPendingReplyPresentationFiltersGroupItemsByDirectedAccount() {
        let firstAccountID = UUID()
        let secondAccountID = UUID()
        let groupID = UUID()
        let member = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemBlue,
            initials: "员",
            kind: .groupMember
        )
        let first = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "帐号一事项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            isGroupConversation: true, recipientAccountID: firstAccountID,
            unansweredMetadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [firstAccountID], threadID: "first"
            )
        )
        let second = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "帐号二事项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            isGroupConversation: true, recipientAccountID: secondAccountID,
            unansweredMetadata: UnansweredMessageMetadata(
                assignedAccountIDs: [secondAccountID], threadID: "second"
            )
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [first, second],
            allMessages: [first, second],
            currentAccountIDs: [firstAccountID, secondAccountID],
            accountFilterID: firstAccountID,
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.map(\.id), [first.id])
    }

    func testPendingReplyHomeUsesSameRulesForAllAndSingleAccountScopes() {
        let firstAccount = ChatParticipant(
            id: UUID(), displayName: "帐号一", tintColor: .systemGreen,
            initials: "一", isCurrentUser: true, kind: .account
        )
        let secondAccount = ChatParticipant(
            id: UUID(), displayName: "帐号二", tintColor: .systemOrange,
            initials: "二", isCurrentUser: true, kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(), displayName: "好友", tintColor: .systemBlue,
            initials: "友", kind: .contact
        )
        let group = ChatParticipant(
            id: UUID(), displayName: "群聊", tintColor: .systemPurple,
            initials: "群", kind: .group
        )
        let member = ChatParticipant(
            id: UUID(), displayName: "群成员", tintColor: .systemTeal,
            initials: "员", kind: .groupMember
        )
        func message(
            _ type: ChatMessageType = .text,
            conversationID: UUID,
            sender: ChatParticipant,
            body: String,
            accountID: UUID,
            isGroup: Bool = false,
            metadata: UnansweredMessageMetadata? = nil,
            time: TimeInterval
        ) -> ChatMessage {
            ChatMessage(
                id: UUID(), conversationID: conversationID, type: type,
                sender: sender, body: body, detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: time),
                isGroupConversation: isGroup,
                recipientAccountID: accountID,
                unansweredMetadata: metadata
            )
        }
        let friendForFirst = message(
            conversationID: friend.id, sender: friend, body: "好友发给帐号一",
            accountID: firstAccount.id, time: 1
        )
        let friendForSecond = message(
            conversationID: friend.id, sender: friend, body: "好友发给帐号二",
            accountID: secondAccount.id, time: 2
        )
        let groupForFirst = message(
            conversationID: group.id, sender: member, body: "群里@帐号一",
            accountID: firstAccount.id, isGroup: true,
            metadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [firstAccount.id], threadID: "mention-first"
            ), time: 3
        )
        let ordinaryGroupMessage = message(
            conversationID: group.id, sender: member, body: "普通群消息",
            accountID: secondAccount.id, isGroup: true, time: 4
        )
        let directedGroupNotice = message(
            .groupNotice, conversationID: group.id, sender: member,
            body: "不应进入未回页的群公告", accountID: secondAccount.id,
            isGroup: true,
            metadata: UnansweredMessageMetadata(
                assignedAccountIDs: [secondAccount.id], threadID: "notice-second"
            ), time: 5
        )
        let controller = ChatWindowViewController()
        controller.state = ChatDemoState(
            participants: [firstAccount, secondAccount, friend, group, member],
            messages: [
                friendForFirst, friendForSecond, groupForFirst,
                ordinaryGroupMessage, directedGroupNotice
            ],
            currentUserID: firstAccount.id,
            selectedAccountID: firstAccount.id,
            contactCards: [:], selectedFriendID: nil, rightToolItems: []
        )
        controller.invalidateVisibleDataCaches()

        controller.setUnansweredTimelineRoute(scope: .all, focus: nil)
        let allIDs = Set(controller.pendingReplyHomePresentation().messages.map(\.id))
        XCTAssertEqual(allIDs, Set([friendForFirst.id, friendForSecond.id, groupForFirst.id]))

        controller.setUnansweredTimelineRoute(scope: .account(firstAccount.id), focus: nil)
        let firstAccountIDs = Set(controller.pendingReplyHomePresentation().messages.map(\.id))
        XCTAssertEqual(firstAccountIDs, Set([friendForFirst.id, groupForFirst.id]))

        controller.setUnansweredTimelineRoute(scope: .account(secondAccount.id), focus: nil)
        let secondAccountIDs = Set(controller.pendingReplyHomePresentation().messages.map(\.id))
        XCTAssertEqual(secondAccountIDs, Set([friendForSecond.id]))
    }

    func testPendingReplyPresentationKeepsSameFriendSeparateAcrossAccounts() {
        let firstAccountID = UUID()
        let secondAccountID = UUID()
        let friend = ChatParticipant(
            id: UUID(), displayName: "共同好友", tintColor: .systemBlue,
            initials: "友", kind: .contact
        )
        let first = ChatMessage(
            id: UUID(), conversationID: friend.id, type: .text, sender: friend,
            body: "发给帐号一", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            recipientAccountID: firstAccountID
        )
        let second = ChatMessage(
            id: UUID(), conversationID: friend.id, type: .text, sender: friend,
            body: "发给帐号二", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            recipientAccountID: secondAccountID
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [first, second],
            allMessages: [first, second],
            currentAccountIDs: [firstAccountID, secondAccountID],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.items.count, 2)
        XCTAssertEqual(
            Set(presentation.items.flatMap(\.targetAccountIDs)),
            Set([firstAccountID, secondAccountID])
        )
        XCTAssertNotEqual(
            presentation.item(forMessageID: first.id)?.id,
            presentation.item(forMessageID: second.id)?.id
        )
    }

    func testPendingReplyPresentationShowsThreeMessagesForEachAccountOfSameFriend() {
        let firstAccountID = UUID()
        let secondAccountID = UUID()
        let friend = ChatParticipant(
            id: UUID(), displayName: "共同好友", tintColor: .systemBlue,
            initials: "友", kind: .contact
        )
        let firstAccountMessages = (0..<5).map { index in
            ChatMessage(
                id: UUID(), conversationID: friend.id, type: .text, sender: friend,
                body: "帐号一-\(index)", detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index * 2 + 1)),
                recipientAccountID: firstAccountID
            )
        }
        let secondAccountMessages = (0..<4).map { index in
            ChatMessage(
                id: UUID(), conversationID: friend.id, type: .text, sender: friend,
                body: "帐号二-\(index)", detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index * 2 + 2)),
                recipientAccountID: secondAccountID
            )
        }
        let messages = firstAccountMessages + secondAccountMessages

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: [firstAccountID, secondAccountID],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(
            presentation.messages.map(\.body),
            ["帐号一-0", "帐号一-1", "帐号一-2", "帐号二-0", "帐号二-1", "帐号二-2"]
        )
        XCTAssertEqual(presentation.hiddenDirectMessageCountByConversationID[friend.id], 3)
        XCTAssertEqual(presentation.directMessageCountByConversationID[friend.id], 9)
        XCTAssertEqual(
            presentation.messages.compactMap {
                presentation.item(forMessageID: $0.id)?.targetAccountIDs.first
            },
            [firstAccountID, firstAccountID, firstAccountID,
             secondAccountID, secondAccountID, secondAccountID]
        )
    }

    func testPendingReplyPresentationPreservesExactMultiAccountGroupTargets() {
        let firstAccountID = UUID()
        let secondAccountID = UUID()
        let groupID = UUID()
        let member = ChatParticipant(
            id: UUID(), displayName: "群成员", tintColor: .systemOrange,
            initials: "员", kind: .groupMember
        )
        let message = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "请两个帐号一起确认", detail: "", isOutgoing: false,
            presentation: .bare, timestamp: "", sentAt: Date(),
            isGroupConversation: true, recipientAccountID: firstAccountID,
            unansweredMetadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [firstAccountID, secondAccountID],
                threadID: "shared-thread"
            )
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [message],
            allMessages: [message],
            currentAccountIDs: [firstAccountID, secondAccountID],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.items.count, 1)
        XCTAssertEqual(
            Set(presentation.items[0].targetAccountIDs),
            Set([firstAccountID, secondAccountID])
        )
        XCTAssertEqual(presentation.item(forMessageID: message.id)?.id, presentation.items[0].id)
    }

    func testPendingReplyPresentationExpandsGroupItemsInPlace() {
        let accountID = UUID()
        let groupID = UUID()
        let member = ChatParticipant(
            id: UUID(), displayName: "群成员", tintColor: .systemOrange,
            initials: "员", kind: .groupMember
        )
        let messages = (0..<5).map { index in
            ChatMessage(
                id: UUID(), conversationID: groupID, type: .text, sender: member,
                body: "事项 \(index)", detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                isGroupConversation: true, recipientAccountID: accountID,
                unansweredMetadata: UnansweredMessageMetadata(
                    mentionedAccountIDs: [accountID], threadID: "thread-\(index)"
                )
            )
        }

        let collapsed = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: [accountID],
            directVisibleLimits: [:]
        )
        let expanded = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: [accountID],
            directVisibleLimits: [:],
            groupVisibleLimits: [groupID: 6]
        )

        XCTAssertEqual(collapsed.items.count, 3)
        XCTAssertEqual(collapsed.hiddenGroupItemCountByConversationID[groupID], 2)
        XCTAssertEqual(expanded.items.count, 5)
        XCTAssertNil(expanded.hiddenGroupItemCountByConversationID[groupID])
    }

    func testHandledGroupRepresentativeClearsTheWholeItem() {
        let accountID = UUID()
        let groupID = UUID()
        let member = ChatParticipant(
            id: UUID(), displayName: "群成员", tintColor: .systemOrange,
            initials: "员", kind: .groupMember
        )
        let first = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "事项开始", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            isGroupConversation: true, recipientAccountID: accountID,
            unansweredMetadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [accountID], threadID: "same-thread"
            )
        )
        let handled = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "事项已处理", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            isGroupConversation: true, recipientAccountID: accountID,
            unansweredMetadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [accountID], threadID: "same-thread",
                handledAt: Date(timeIntervalSince1970: 3)
            )
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [first, handled],
            allMessages: [first, handled],
            currentAccountIDs: [accountID],
            directVisibleLimits: [:]
        )

        XCTAssertTrue(presentation.items.isEmpty)
        XCTAssertTrue(presentation.messages.isEmpty)
    }

    func testUnansweredRoutePlannerRestoresSourceScopeAndFocus() {
        let accountID = UUID()
        let conversationID = UUID()
        let messageID = UUID()
        let item = UnansweredItem(
            id: UnansweredItemID(
                conversationID: conversationID,
                discriminator: "direct:\(accountID.uuidString)"
            ),
            kind: .direct,
            conversationID: conversationID,
            representativeMessageID: messageID,
            messageIDs: [messageID],
            targetAccountIDs: [accountID]
        )
        let route = UnansweredRoutePlanner.conversation(
            scope: .account(accountID),
            item: item,
            messageID: messageID,
            accountID: accountID,
            candidateItemIDs: [],
            contentOffsetY: 240
        )

        XCTAssertEqual(
            UnansweredRoutePlanner.timelineAfterBack(from: route),
            .unanswered(
                scope: .account(accountID),
                focus: UnansweredTimelineFocus(itemID: item.id, messageID: messageID)
            )
        )
        XCTAssertEqual(
            UnansweredRoutePlanner.timelineSelectingAccount(
                tappedAccountID: accountID
            ),
            .unanswered(scope: .account(accountID), focus: nil)
        )
    }

    func testAccountDirectoryRoutePreservesConversationReturnDestination() {
        let accountID = UUID()
        let conversationID = UUID()
        let directory = ChatWindowRoute.accountDirectory(accountID: accountID)
        let conversation = ChatWindowRoute.conversation(
            accountID: accountID,
            conversationID: conversationID,
            unansweredOrigin: nil,
            directoryOriginAccountID: directory.accountDirectoryID
        )

        XCTAssertEqual(directory.accountDirectoryID, accountID)
        XCTAssertEqual(conversation.accountDirectoryID, accountID)
        XCTAssertNil(conversation.unansweredOrigin)
    }

    func testTimelineBackEntersDirectoryOnlyFromExplicitAccountScope() {
        let accountID = UUID()

        XCTAssertNil(UnansweredRoutePlanner.destinationAfterTimelineBack(from: .all))
        XCTAssertEqual(
            UnansweredRoutePlanner.destinationAfterTimelineBack(from: .account(accountID)),
            .accountDirectory(accountID: accountID)
        )
    }

    func testAccountSelectionRoutesToUnansweredTimelineOrDirectory() {
        let accountID = UUID()

        XCTAssertEqual(
            UnansweredRoutePlanner.destinationSelectingAccount(
                tappedAccountID: accountID,
                hasUnansweredMessages: true
            ),
            .unanswered(scope: .account(accountID), focus: nil)
        )
        XCTAssertEqual(
            UnansweredRoutePlanner.destinationSelectingAccount(
                tappedAccountID: accountID,
                hasUnansweredMessages: false
            ),
            .accountDirectory(accountID: accountID)
        )
    }

    func testUnansweredCompletionRequiresConfirmedMatchingReply() {
        let account = ChatParticipant(
            id: UUID(), displayName: "主帐号", tintColor: .systemGreen,
            initials: "主", isCurrentUser: true, kind: .account
        )
        let conversationID = UUID()
        let focus = UnansweredTimelineFocus(
            itemID: UnansweredItemID(
                conversationID: conversationID,
                discriminator: "direct:\(account.id.uuidString)"
            ),
            messageID: UUID()
        )
        let origin = UnansweredNavigationOrigin(
            scope: .account(account.id),
            sourceFocus: focus,
            accountID: account.id,
            candidateItemIDs: [],
            contentOffsetY: 120,
            completesItemOnSend: true
        )
        let reply = ChatMessage(
            id: UUID(), conversationID: conversationID, type: .text, sender: account,
            body: "真实回复", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(), recipientAccountID: account.id
        )
        let failedDraft = ChatMessage(
            id: UUID(), conversationID: conversationID, type: .text, sender: account,
            body: "AI 草稿", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(), isAI: true, recipientAccountID: account.id
        )
        let wrongConversation = ChatMessage(
            id: UUID(), conversationID: UUID(), type: .text, sender: account,
            body: "其他会话", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(), recipientAccountID: account.id
        )

        XCTAssertTrue(UnansweredReplyCompletionPolicy.shouldComplete(
            message: reply,
            origin: origin,
            isAutoReplyPrediction: false
        ))
        XCTAssertFalse(UnansweredReplyCompletionPolicy.shouldComplete(
            message: failedDraft,
            origin: origin,
            isAutoReplyPrediction: false
        ))
        XCTAssertFalse(UnansweredReplyCompletionPolicy.shouldComplete(
            message: wrongConversation,
            origin: origin,
            isAutoReplyPrediction: false
        ))
        XCTAssertFalse(UnansweredReplyCompletionPolicy.shouldComplete(
            message: reply,
            origin: origin,
            isAutoReplyPrediction: true
        ))
        let browsingOrigin = UnansweredNavigationOrigin(
            scope: origin.scope,
            sourceFocus: origin.sourceFocus,
            accountID: origin.accountID,
            candidateItemIDs: origin.candidateItemIDs,
            contentOffsetY: origin.contentOffsetY,
            completesItemOnSend: false
        )
        XCTAssertFalse(UnansweredReplyCompletionPolicy.shouldComplete(
            message: reply,
            origin: browsingOrigin,
            isAutoReplyPrediction: false
        ))
    }

    func testPendingReplyIndexKeepsItemUntilOutgoingDeliveryIsConfirmed() {
        let accountID = UUID()
        let conversationID = UUID()
        let friend = ChatParticipant(
            id: conversationID,
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let account = ChatParticipant(
            id: accountID,
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let incoming = ChatMessage(
            id: UUID(), conversationID: conversationID, type: .text, sender: friend,
            body: "待回复", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            recipientAccountID: accountID
        )
        let outgoing = ChatMessage(
            id: UUID(), conversationID: conversationID, type: .text, sender: account,
            body: "回复", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            recipientAccountID: accountID
        )
        let key: (ChatMessage) -> PendingReplyConversationAccountKey? = { message in
            message.recipientAccountID.map {
                PendingReplyConversationAccountKey(conversationID: message.conversationID, accountID: $0)
            }
        }
        var deliveryConfirmed = false
        var index = PendingReplyIndex()
        index.rebuild(
            messages: [incoming],
            keyForMessage: key,
            isRelevant: { !$0.isOutgoing || deliveryConfirmed },
            isIncoming: { !$0.isOutgoing }
        )

        index.apply(
            [.insert(outgoing)],
            keyForMessage: key,
            isRelevant: { !$0.isOutgoing || deliveryConfirmed },
            isIncoming: { !$0.isOutgoing }
        )
        XCTAssertEqual(index.latestMessages(limit: 10).map(\.id), [incoming.id])

        deliveryConfirmed = true
        index.apply(
            [.update(outgoing)],
            keyForMessage: key,
            isRelevant: { !$0.isOutgoing || deliveryConfirmed },
            isIncoming: { !$0.isOutgoing }
        )
        XCTAssertTrue(index.latestMessages(limit: 10).isEmpty)
    }

    func testUnansweredReplyAdvancePlannerPreservesCandidateOrder() {
        let participant = ChatParticipant(
            id: UUID(), displayName: "好友", tintColor: .systemBlue,
            initials: "友", kind: .contact
        )
        let first = ChatMessage(
            id: UUID(), conversationID: participant.id, type: .text, sender: participant,
            body: "第一项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1)
        )
        let second = ChatMessage(
            id: UUID(), conversationID: participant.id, type: .text, sender: participant,
            body: "第二项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2)
        )

        let nextID = UnansweredReplyAdvancePlanner.nextMessageID(
            candidateMessageIDs: [UUID(), second.id, first.id],
            availableMessages: [first, second]
        )

        XCTAssertEqual(nextID, second.id)
        XCTAssertNil(UnansweredReplyAdvancePlanner.nextMessageID(
            candidateMessageIDs: [],
            availableMessages: []
        ))
    }

    func testDelayedOpenApiTaskSuccessRefreshesPendingReplyIndex() {
        let controller = ChatWindowViewController()
        let account = ChatParticipant(
            id: UUID(), displayName: "主帐号", tintColor: .systemGreen,
            initials: "主", isCurrentUser: true, kind: .account
        )
        let message = ChatMessage(
            id: UUID(), conversationID: UUID(), type: .text, sender: account,
            body: "稍后确认的回复", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(), recipientAccountID: account.id
        )
        let taskID = "delayed-task"
        controller.state.messages = [message]
        controller.openApiDeliveryStatusesByMessageID[message.id] = OpenApiDeliveryStatusRecord(
            messageID: message.id,
            conversationID: message.conversationID,
            taskIDs: [taskID],
            stateRawValue: OpenApiVisibleTaskState.pending.rawValue,
            note: "待确认",
            updatedAt: Date()
        )
        var index = PendingReplyIndex()
        index.rebuild(
            messages: [message],
            keyForMessage: { candidate in
                candidate.recipientAccountID.map {
                    PendingReplyConversationAccountKey(
                        conversationID: candidate.conversationID,
                        accountID: $0
                    )
                }
            },
            isRelevant: { _ in true },
            isIncoming: { !$0.isOutgoing }
        )
        controller.pendingReplyIndex = index
        let previousMutationCount = index.incrementalMutationCount

        controller.applyOpenApiTaskResults([
            OpenApiTaskResultContext(
                participantID: UUID(),
                ownerWxid: "owner",
                conversationWxid: "friend",
                taskID: taskID,
                state: .succeeded,
                status: "success",
                resultCode: "0",
                message: "",
                deviceUuid: "device",
                receivedAt: Date(),
                contentType: "text",
                msgSvrId: "server-message",
                rawHidden: false,
                avatarURL: ""
            )
        ])

        XCTAssertEqual(controller.latestOpenApiSendState(for: message), .succeeded)
        XCTAssertGreaterThan(
            controller.pendingReplyIndex.incrementalMutationCount,
            previousMutationCount
        )
    }

    func testUnansweredEmptyStateOnlyAppearsAfterLoadedTimelineBecomesEmpty() {
        XCTAssertTrue(UnansweredEmptyStatePolicy.shouldShow(
            isHomeTimeline: true,
            messageCount: 0,
            bootstrapState: .loaded,
            hasLoadedSnapshot: true
        ))
        XCTAssertFalse(UnansweredEmptyStatePolicy.shouldShow(
            isHomeTimeline: true,
            messageCount: 0,
            bootstrapState: .loading,
            hasLoadedSnapshot: false
        ))
        XCTAssertFalse(UnansweredEmptyStatePolicy.shouldShow(
            isHomeTimeline: false,
            messageCount: 0,
            bootstrapState: .loaded,
            hasLoadedSnapshot: true
        ))
        XCTAssertFalse(UnansweredEmptyStatePolicy.shouldShow(
            isHomeTimeline: true,
            messageCount: 1,
            bootstrapState: .loaded,
            hasLoadedSnapshot: true
        ))
    }

    func testPendingReplyPresentationUsesOneRepresentativeAndThreeItemsPerGroup() {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let group = ChatParticipant(
            id: UUID(),
            displayName: "工作群",
            tintColor: .systemOrange,
            initials: "群",
            kind: .group
        )
        let member = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemBlue,
            initials: "员",
            kind: .groupMember
        )
        var messages: [ChatMessage] = [
            ChatMessage(
                id: UUID(),
                conversationID: group.id,
                type: .text,
                sender: member,
                body: "普通群聊噪声",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: 0),
                isGroupConversation: true,
                recipientAccountID: account.id
            )
        ]
        for index in 0..<4 {
            messages.append(ChatMessage(
                id: UUID(),
                conversationID: group.id,
                type: .text,
                sender: member,
                body: "事项 \(index) 初始",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index * 2 + 1)),
                isGroupConversation: true,
                recipientAccountID: account.id,
                unansweredMetadata: UnansweredMessageMetadata(
                    mentionedAccountIDs: [account.id],
                    threadID: "thread-\(index)"
                )
            ))
        }
        messages.append(ChatMessage(
            id: UUID(),
            conversationID: group.id,
            type: .text,
            sender: member,
            body: "事项 3 最新代表",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "",
            sentAt: Date(timeIntervalSince1970: 20),
            isGroupConversation: true,
            recipientAccountID: account.id,
            unansweredMetadata: UnansweredMessageMetadata(
                mentionedAccountIDs: [account.id],
                threadID: "thread-3"
            )
        ))

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: [account.id],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.count, 3)
        XCTAssertFalse(presentation.messages.contains { $0.body == "普通群聊噪声" })
        XCTAssertTrue(presentation.messages.contains { $0.body == "事项 3 最新代表" })
        XCTAssertFalse(presentation.messages.contains { $0.body == "事项 3 初始" })
        XCTAssertEqual(presentation.hiddenGroupItemCountByConversationID[group.id], 1)
    }

    func testUnreadDirectFoldExpandsAllMessages() throws {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(),
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let messages = (0..<8).map { index in
            ChatMessage(
                id: UUID(),
                conversationID: friend.id,
                type: .text,
                sender: friend,
                body: "消息 \(index)",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                recipientAccountID: account.id
            )
        }
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.state = ChatDemoState(
            participants: [account, friend],
            messages: messages,
            currentUserID: account.id,
            selectedAccountID: account.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.hasCompletedOpenApiChatBootstrap = true
        controller.invalidateVisibleDataCaches()

        XCTAssertEqual(controller.renderedMessages.count, 3)
        let fold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: friend.id)
        )
        XCTAssertEqual(fold.title, "展开 5 条未回复")

        controller.handleUnreadTimelineFoldTap(fold)

        XCTAssertEqual(controller.renderedMessages.count, 8)
        XCTAssertNil(controller.unreadTimelineFoldPresentation(conversationID: friend.id))
    }

    func testUnreadDirectFoldIsIndependentForEachAccountOfSameFriend() throws {
        let firstAccount = ChatParticipant(
            id: UUID(), displayName: "主帐号一", tintColor: .systemRed,
            initials: "一", isCurrentUser: true, kind: .account
        )
        let secondAccount = ChatParticipant(
            id: UUID(), displayName: "主帐号二", tintColor: .systemGreen,
            initials: "二", isCurrentUser: true, kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(), displayName: "共同好友", tintColor: .systemBlue,
            initials: "友", kind: .contact
        )
        let firstMessages = (0..<5).map { index in
            ChatMessage(
                id: UUID(), conversationID: friend.id, type: .text, sender: friend,
                body: "帐号一-\(index)", detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index * 2 + 1)),
                recipientAccountID: firstAccount.id
            )
        }
        let secondMessages = (0..<5).map { index in
            ChatMessage(
                id: UUID(), conversationID: friend.id, type: .text, sender: friend,
                body: "帐号二-\(index)", detail: "", isOutgoing: false,
                presentation: .bare, timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index * 2 + 2)),
                recipientAccountID: secondAccount.id
            )
        }
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        controller.state = ChatDemoState(
            participants: [firstAccount, secondAccount, friend],
            messages: firstMessages + secondMessages,
            currentUserID: firstAccount.id,
            selectedAccountID: firstAccount.id,
            contactCards: [:], selectedFriendID: nil, rightToolItems: []
        )
        controller.hasCompletedOpenApiChatBootstrap = true
        controller.invalidateVisibleDataCaches()
        controller.view.layoutIfNeeded()

        XCTAssertEqual(controller.renderedMessages.count, 6)
        let firstFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(for: firstMessages[2])
        )
        let secondFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(for: secondMessages[2])
        )
        XCTAssertEqual(firstFold.accountID, firstAccount.id)
        XCTAssertEqual(secondFold.accountID, secondAccount.id)
        XCTAssertEqual(firstFold.title, "展开 2 条未回复")
        XCTAssertEqual(secondFold.title, "展开 2 条未回复")
        XCTAssertEqual(
            controller.unreadTimelineLayoutSnapshot()?.messageFoldBottomOffsets
                .compactMap { $0 }.count,
            2
        )

        controller.handleUnreadTimelineFoldTap(firstFold)

        XCTAssertEqual(controller.renderedMessages.count, 8)
        XCTAssertNil(controller.unreadTimelineFoldPresentation(for: firstMessages[4]))
        XCTAssertEqual(
            controller.unreadTimelineFoldPresentation(for: secondMessages[2])?.accountID,
            secondAccount.id
        )
        XCTAssertEqual(
            controller.unreadTimelineLayoutSnapshot()?.messageFoldBottomOffsets
                .compactMap { $0 }.count,
            1
        )
    }

    func testUnreadDirectFoldExpandsLargeConversationInBatches() throws {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let friend = ChatParticipant(
            id: UUID(),
            displayName: "好友",
            tintColor: .systemBlue,
            initials: "友",
            kind: .contact
        )
        let messages = (0..<45).map { index in
            ChatMessage(
                id: UUID(),
                conversationID: friend.id,
                type: .text,
                sender: friend,
                body: index.isMultiple(of: 3) ? "短消息" : "批量展开消息 \(index)",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                recipientAccountID: account.id
            )
        }
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.state = ChatDemoState(
            participants: [account, friend],
            messages: messages,
            currentUserID: account.id,
            selectedAccountID: account.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.hasCompletedOpenApiChatBootstrap = true
        controller.invalidateVisibleDataCaches()
        controller.messageCollectionView.reloadData()
        controller.leftCollectionView.reloadData()
        controller.view.layoutIfNeeded()
        let initialOffsetY = controller.messageCollectionView.contentOffset.y
        let fold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: friend.id)
        )
        XCTAssertEqual(fold.title, "展开 42 条未回复")

        func waitForExpansion(_ label: String) {
            let completed = expectation(description: label)
            var pollCompletion: (() -> Void)!
            pollCompletion = {
                if !controller.isUnreadFoldExpansionInProgress {
                    completed.fulfill()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.03, execute: pollCompletion)
                }
            }
            pollCompletion()
            wait(for: [completed], timeout: 3)
        }

        controller.handleUnreadTimelineFoldTap(fold)
        waitForExpansion("first unread fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 18)
        let secondFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: friend.id)
        )
        XCTAssertEqual(secondFold.title, "展开 27 条未回复")

        controller.handleUnreadTimelineFoldTap(secondFold)
        waitForExpansion("second unread fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 33)
        let finalFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: friend.id)
        )
        XCTAssertEqual(finalFold.title, "展开 12 条未回复")

        controller.handleUnreadTimelineFoldTap(finalFold)
        waitForExpansion("final unread fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 45)
        XCTAssertNil(controller.unreadTimelineFoldPresentation(conversationID: friend.id))
        XCTAssertEqual(controller.messageCollectionView.contentOffset.y, initialOffsetY, accuracy: 0.5)
    }

    func testUnreadFoldTitleCapsDisplayedCountAtNinetyNinePlus() {
        let controller = ChatWindowViewController()

        XCTAssertEqual(controller.unreadTimelineFoldTitle(hiddenCount: 99), "展开 99 条未回复")
        XCTAssertEqual(controller.unreadTimelineFoldTitle(hiddenCount: 100), "展开 99+ 条未回复")
    }

    func testUnreadGroupFoldExpandsItemsInBatchesAndRemovesControl() throws {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let group = ChatParticipant(
            id: UUID(),
            displayName: "群聊",
            tintColor: .systemBlue,
            initials: "群",
            kind: .group
        )
        let member = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemOrange,
            initials: "员",
            kind: .contact
        )
        let messages = (0..<39).map { index in
            ChatMessage(
                id: UUID(),
                conversationID: group.id,
                type: .text,
                sender: member,
                body: "群事项 \(index)",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                isGroupConversation: true,
                recipientAccountID: account.id,
                unansweredMetadata: UnansweredMessageMetadata(
                    mentionedAccountIDs: [account.id],
                    threadID: "thread-\(index)"
                )
            )
        }
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.state = ChatDemoState(
            participants: [account, group, member],
            messages: messages,
            currentUserID: account.id,
            selectedAccountID: account.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.hasCompletedOpenApiChatBootstrap = true
        controller.invalidateVisibleDataCaches()

        XCTAssertEqual(controller.renderedMessages.count, 3)
        let fold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: group.id)
        )
        XCTAssertEqual(fold.title, "展开 36 条未回复")

        func waitForExpansion(_ label: String) {
            let completed = expectation(description: label)
            var pollCompletion: (() -> Void)!
            pollCompletion = {
                if !controller.isUnreadFoldExpansionInProgress {
                    completed.fulfill()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.03, execute: pollCompletion)
                }
            }
            pollCompletion()
            wait(for: [completed], timeout: 3)
        }

        controller.handleUnreadTimelineFoldTap(fold)
        waitForExpansion("first group fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 18)
        XCTAssertFalse(controller.expandedUnreadConversationIDs.contains(group.id))
        let secondFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: group.id)
        )
        XCTAssertEqual(secondFold.title, "展开 21 条未回复")

        controller.handleUnreadTimelineFoldTap(secondFold)
        waitForExpansion("second group fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 33)
        let finalFold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: group.id)
        )
        XCTAssertEqual(finalFold.title, "展开 6 条未回复")

        controller.handleUnreadTimelineFoldTap(finalFold)
        waitForExpansion("final group fold batch finishes")

        XCTAssertEqual(controller.renderedMessages.count, 39)
        XCTAssertNil(controller.unreadTimelineFoldPresentation(conversationID: group.id))
        XCTAssertTrue(controller.expandedUnreadConversationIDs.contains(group.id))
    }

    func testGroupReplyClosesOnlyItsMatchingPendingItem() {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "主帐号",
            tintColor: .systemGreen,
            initials: "主",
            isCurrentUser: true,
            kind: .account
        )
        let groupID = UUID()
        let member = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemBlue,
            initials: "员",
            kind: .groupMember
        )
        let firstMetadata = UnansweredMessageMetadata(
            mentionedAccountIDs: [account.id],
            threadID: "first"
        )
        let secondMetadata = UnansweredMessageMetadata(
            mentionedAccountIDs: [account.id],
            threadID: "second"
        )
        let first = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "第一事项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 1),
            isGroupConversation: true, recipientAccountID: account.id,
            unansweredMetadata: firstMetadata
        )
        let second = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: member,
            body: "第二事项", detail: "", isOutgoing: false, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 2),
            isGroupConversation: true, recipientAccountID: account.id,
            unansweredMetadata: secondMetadata
        )
        let reply = ChatMessage(
            id: UUID(), conversationID: groupID, type: .text, sender: account,
            body: "回复第一事项", detail: "", isOutgoing: true, presentation: .bare,
            timestamp: "", sentAt: Date(timeIntervalSince1970: 3),
            isGroupConversation: true, recipientAccountID: account.id,
            unansweredMetadata: firstMetadata
        )

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: [first, second],
            allMessages: [first, second, reply],
            currentAccountIDs: [account.id],
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.map(\.id), [second.id])
    }

    func testOpenApiGroupContentParserSeparatesSenderFromJSONCard() {
        let parsed = OpenApiChatContentParser.parse(
            "zq332868773:{\"Title\":\"王虹、邓煜为啥能获菲尔兹奖？\",\"Type\":5}",
            chatType: 2
        )

        XCTAssertEqual(parsed.senderWxid, "zq332868773")
        XCTAssertEqual(parsed.content, "{\"Title\":\"王虹、邓煜为啥能获菲尔兹奖？\",\"Type\":5}")
    }

    func testOpenApiGroupContentParserSeparatesSenderFromText() {
        let parsed = OpenApiChatContentParser.parse(
            "wxid_ezwxebf6lco722:\n军工龙头又涨停了",
            chatType: 2
        )

        XCTAssertEqual(parsed.senderWxid, "wxid_ezwxebf6lco722")
        XCTAssertEqual(parsed.content, "军工龙头又涨停了")
    }

    func testOpenApiDirectContentParserDoesNotStripPlainTextColon() {
        let parsed = OpenApiChatContentParser.parse("提醒:明天开会", chatType: 1)

        XCTAssertEqual(parsed.senderWxid, "")
        XCTAssertEqual(parsed.content, "提醒:明天开会")
    }

    func testOpenApiConversationIdentityIsCanonicalAcrossAccounts() {
        let first = OpenApiChatConversationDescriptor(
            backendID: 10,
            accountWxid: "wxid_account_a",
            conversationWxid: "12345@chatroom",
            conversationType: 2,
            displayName: "群聊",
            displayAvatar: "",
            unreadCount: 1,
            messageCount: 2,
            isPinned: false,
            isMuted: false,
            lastMessageContent: "消息",
            lastMessageTime: nil,
            updatedAt: nil
        )
        let second = OpenApiChatConversationDescriptor(
            backendID: 88,
            accountWxid: "wxid_account_b",
            conversationWxid: "12345@chatroom",
            conversationType: 2,
            displayName: "另一个帐号下的群名",
            displayAvatar: "",
            unreadCount: 4,
            messageCount: 9,
            isPinned: true,
            isMuted: true,
            lastMessageContent: "另一条消息",
            lastMessageTime: nil,
            updatedAt: nil
        )

        XCTAssertEqual(first.participantID, second.participantID)
        XCTAssertTrue(first.isGroup)
        XCTAssertTrue(second.isGroup)
    }

    func testOpenApiMessageIdentityIsStableAndAccountScoped() {
        let payload = OpenApiChatMessagePayload(
            messageID: 123,
            messageServerID: 456,
            conversationBackendID: 10,
            senderWxid: "wxid_friend",
            receiverWxid: "wxid_account",
            chatType: 1,
            messageType: 1,
            content: "你好",
            direction: 0,
            sendStatus: nil,
            readStatus: 0,
            isRevoked: false,
            isDeleted: false,
            localMessageID: "",
            clientMessageID: "",
            sentAt: nil,
            receivedAt: nil,
            readAt: nil,
            revokedAt: nil,
            createdAt: nil,
            updatedAt: nil,
            media: [],
            extensions: [:],
            voiceText: ""
        )

        XCTAssertEqual(payload.stableID(accountWxid: "wxid_a"), payload.stableID(accountWxid: "wxid_a"))
        XCTAssertNotEqual(payload.stableID(accountWxid: "wxid_a"), payload.stableID(accountWxid: "wxid_b"))
    }

    func testMessageTypeCatalogContainsThirtyTypes() {
        XCTAssertEqual(ChatMessageType.allCases.count, 30)
        XCTAssertEqual(ChatMessageType.text.rawValue, 1)
        XCTAssertEqual(ChatMessageType.system.rawValue, 30)
    }

    func testEveryMessageTypeHasDistinctRenderingTemplate() {
        let templates = ChatMessageType.allCases.map(\.template)
        XCTAssertEqual(Set(templates.map(\.id)).count, ChatMessageType.allCases.count)
        XCTAssertTrue(templates.allSatisfy { !$0.primaryText.isEmpty })
        XCTAssertTrue(templates.allSatisfy { !$0.symbolName.isEmpty })
    }

    func testMessageTemplatesReserveEnoughHeightForPreviewContent() {
        let templates = ChatMessageType.allCases.map(\.template)
        XCTAssertTrue(templates.allSatisfy { $0.preferredHeight >= $0.minimumReadableHeight })
    }

    func testFloatingChatActivityStateDescribesMinimizedChat() {
        let state = FloatingChatActivityState(
            title: "\u{60ac}\u{6d6e}\u{804a}\u{5929}",
            subtitle: "\u{70b9}\u{51fb}\u{7ee7}\u{7eed}",
            unreadCount: 3,
            messages: [
                FloatingChatActivityMessage(sender: "\u{6211}", text: "\u{4f60}\u{597d}", isOutgoing: true)
            ],
            draft: ""
        )

        XCTAssertEqual(state.title, "\u{60ac}\u{6d6e}\u{804a}\u{5929}")
        XCTAssertEqual(state.subtitle, "\u{70b9}\u{51fb}\u{7ee7}\u{7eed}")
        XCTAssertEqual(state.unreadCount, 3)
        XCTAssertEqual(state.messages.count, 1)
    }

    func testDemoStateBuildsSidebarAnchorsToMessages() {
        let state = ChatDataFactory.makeDemoState()
        XCTAssertGreaterThanOrEqual(state.messages.count, 30)
        XCTAssertGreaterThanOrEqual(state.friends.count, 20)
        XCTAssertEqual(state.leftItems.count, state.friends.count)
        XCTAssertFalse(state.leftItems.isEmpty)
        XCTAssertFalse(state.rightAccountItems.isEmpty)
        XCTAssertFalse(state.rightToolItems.isEmpty)

        for item in state.leftItems + state.rightAccountItems {
            XCTAssertNotNil(state.indexOfMessage(id: item.targetMessageID))
        }
    }

    func testRightRailSeparatesAccountsFromTools() {
        let state = ChatDataFactory.makeDemoState()
        XCTAssertEqual(state.rightAccountItems.count, state.currentUsers.count)
        XCTAssertTrue(state.rightAccountItems.allSatisfy { $0.kind == .account })
        XCTAssertTrue(state.rightToolItems.allSatisfy { $0.kind == .tool })
    }

    func testRightAccountCollectionClipsCellsWhenToolRailExpands() {
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()

        XCTAssertTrue(controller.rightAccountCollectionView.clipsToBounds)
    }

    func testRightAccountBorderColorsAreDistinct() {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        controller.hasLoadedUnansweredAccountColorAssignments = true
        controller.unansweredAccountColorIndexByID = Dictionary(
            uniqueKeysWithValues: controller.state.currentUsers.map { ($0.id, 0) }
        )
        let colors = controller.state.currentUsers.map { controller.unansweredAccountColor(for: $0.id) }

        for (index, color) in colors.enumerated() {
            XCTAssertFalse(colors.dropFirst(index + 1).contains { color.isEqual($0) })
        }
        for (index, color) in colors.enumerated() {
            var red: CGFloat = 0
            var green: CGFloat = 0
            var blue: CGFloat = 0
            XCTAssertTrue(color.getRed(&red, green: &green, blue: &blue, alpha: nil))
            for otherColor in colors.dropFirst(index + 1) {
                var otherRed: CGFloat = 0
                var otherGreen: CGFloat = 0
                var otherBlue: CGFloat = 0
                XCTAssertTrue(
                    otherColor.getRed(
                        &otherRed,
                        green: &otherGreen,
                        blue: &otherBlue,
                        alpha: nil
                    )
                )
                let distance = hypot(hypot(red - otherRed, green - otherGreen), blue - otherBlue)
                XCTAssertGreaterThan(distance, 0.24)
            }
        }
        XCTAssertEqual(
            Set(controller.unansweredAccountColorIndexByID.values).count,
            controller.state.currentUsers.count
        )
    }

    func testRightAccountAvatarPresentationInDirectAndGroupDetails() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 3)
        controller.state.selectedAccountID = accounts[0].id

        func configuredCell(for item: SidebarItem) throws -> (SidebarCell, UIView) {
            let cell = SidebarCell(frame: CGRect(x: 0, y: 0, width: 50, height: 44))
            controller.configureSidebarCell(cell, with: item, in: controller.rightAccountCollectionView)
            let avatarTile = try XCTUnwrap(cell.contentView.subviews.first)
            return (cell, avatarTile)
        }

        let directFriend = try XCTUnwrap(
            controller.state.friends.first { !controller.isGroupConversation($0) }
        )
        controller.state.selectedFriendID = directFriend.id
        let directItems = controller.state.rightAccountItems
        let selectedDirectItem = try XCTUnwrap(directItems.first { $0.participantID == accounts[0].id })
        let otherDirectItem = try XCTUnwrap(directItems.first { $0.participantID == accounts[1].id })
        let selectedDirect = try configuredCell(for: selectedDirectItem)
        let otherDirect = try configuredCell(for: otherDirectItem)
        XCTAssertEqual(selectedDirect.0.contentView.alpha, 1, accuracy: 0.01)
        XCTAssertEqual(selectedDirect.1.layer.borderWidth, 3, accuracy: 0.01)
        XCTAssertGreaterThan(selectedDirect.0.contentView.layer.shadowOpacity, 0)
        XCTAssertEqual(otherDirect.0.contentView.alpha, 1, accuracy: 0.01)
        XCTAssertEqual(otherDirect.1.layer.borderWidth, 0, accuracy: 0.01)
        XCTAssertNil(selectedDirect.0.accountConnectionDotColor())
        XCTAssertNil(otherDirect.0.accountConnectionDotColor())

        let group = try XCTUnwrap(
            controller.state.friends.first { controller.isGroupConversation($0) }
        )
        controller.explicitGroupMemberIDsByGroupID[group.id] = [accounts[0].id, accounts[1].id]
        controller.state.selectedFriendID = group.id
        let groupItems = controller.state.rightAccountItems
        let selectedGroupItem = try XCTUnwrap(groupItems.first { $0.participantID == accounts[0].id })
        let relatedGroupItem = try XCTUnwrap(groupItems.first { $0.participantID == accounts[1].id })
        let unrelatedGroupItem = try XCTUnwrap(groupItems.first { $0.participantID == accounts[2].id })
        let selectedGroup = try configuredCell(for: selectedGroupItem)
        let relatedGroup = try configuredCell(for: relatedGroupItem)
        let unrelatedGroup = try configuredCell(for: unrelatedGroupItem)
        XCTAssertEqual(selectedGroup.0.contentView.alpha, 1, accuracy: 0.01)
        XCTAssertEqual(selectedGroup.1.layer.borderWidth, 3, accuracy: 0.01)
        XCTAssertGreaterThan(selectedGroup.0.contentView.layer.shadowOpacity, 0)
        XCTAssertEqual(relatedGroup.0.contentView.alpha, 1, accuracy: 0.01)
        XCTAssertEqual(relatedGroup.1.layer.borderWidth, 0, accuracy: 0.01)
        XCTAssertEqual(unrelatedGroup.0.contentView.alpha, 0.26, accuracy: 0.01)
        XCTAssertEqual(unrelatedGroup.1.layer.borderWidth, 0, accuracy: 0.01)
        XCTAssertTrue(
            try XCTUnwrap(selectedGroup.0.accountConnectionDotColor())
                .isEqual(controller.unansweredAccountColor(for: accounts[0].id))
        )
        XCTAssertTrue(
            try XCTUnwrap(relatedGroup.0.accountConnectionDotColor())
                .isEqual(controller.unansweredAccountColor(for: accounts[1].id))
        )
        XCTAssertNil(unrelatedGroup.0.accountConnectionDotColor())
    }

    func testSelectingFriendFiltersVisibleConversation() throws {
        var state = ChatDataFactory.makeDemoState()
        let friend = try XCTUnwrap(state.friends.first)
        let toolCount = state.rightToolItems.count
        state.selectedFriendID = friend.id

        XCTAssertFalse(state.visibleMessages.isEmpty)
        XCTAssertTrue(state.visibleMessages.allSatisfy { $0.conversationID == friend.id })
        XCTAssertEqual(state.rightAccountItems.count, state.currentUsers.count)
        XCTAssertEqual(state.rightAccountItems.first?.participantID, state.currentUserID)
        XCTAssertEqual(state.rightToolItems.count, toolCount)
    }

    func testConnectionOverlayUsesSolidAnchors() {
        let overlay = ConnectionOverlayView()
        overlay.anchors = [
            ConnectionAnchor(
                from: CGPoint(x: 0, y: 20),
                to: CGPoint(x: 120, y: 40),
                color: .systemBlue,
                direction: .leftToMessage
            )
        ]

        XCTAssertEqual(overlay.anchors.first?.direction, .leftToMessage)
        XCTAssertFalse(overlay.usesAnimatedDashFlow)
    }

    func testGroupDetailOutgoingMessageUsesAccountDotWithoutRightConnection() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 2)
        let senderAccount = accounts[0]
        let owningAccount = accounts[1]
        let group = try XCTUnwrap(
            controller.state.friends.first { controller.isGroupConversation($0) }
        )
        controller.explicitGroupMemberIDsByGroupID[group.id] = [senderAccount.id, owningAccount.id]
        controller.state.selectedFriendID = group.id

        let message = ChatMessage(
            id: UUID(),
            conversationID: group.id,
            type: .text,
            sender: owningAccount,
            body: "主帐号发送的群消息",
            detail: "",
            isOutgoing: true,
            presentation: .avatarAndName,
            timestamp: "12:00",
            sentAt: Date(),
            isGroupConversation: false,
            recipientAccountID: owningAccount.id
        )
        XCTAssertFalse(controller.shouldDrawRightAccountConnection(for: message))
        XCTAssertNil(controller.messageConnectionGroupID(for: message))
        XCTAssertEqual(controller.outgoingGroupAccountID(for: message), owningAccount.id)

        let accountColor = controller.unansweredAccountColor(for: owningAccount.id)
        XCTAssertFalse(accountColor.isEqual(controller.unansweredAccountColor(for: senderAccount.id)))
        let width: CGFloat = 320
        let height = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: width,
            showsGroupAvatar: false,
            showsGroupName: true
        )
        let bubble = ChatMessageBubbleView(frame: CGRect(x: 0, y: 0, width: width, height: height))
        bubble.configure(
            with: message,
            showsGroupAvatar: false,
            showsGroupName: true,
            unansweredRecipientColor: accountColor
        )
        bubble.setOutgoingGroupAccountPresentation(true)
        bubble.layoutIfNeeded()

        let bubbleFrame = bubble.recipientWatermarkBubbleFrame(in: bubble)
        let dotFrame = try XCTUnwrap(bubble.recipientColorDotFrame(in: bubble))
        let metadataFrame = try XCTUnwrap(bubble.metadataFrame(in: bubble))
        let nameFrame = try XCTUnwrap(bubble.inlineNameFrame(in: bubble))
        XCTAssertEqual(dotFrame.width, 9, accuracy: 0.01)
        XCTAssertEqual(dotFrame.height, 9, accuracy: 0.01)
        XCTAssertEqual(dotFrame.midX, bubbleFrame.maxX, accuracy: 0.5)
        XCTAssertEqual(dotFrame.midY, bubbleFrame.midY, accuracy: 0.5)
        XCTAssertTrue(try XCTUnwrap(bubble.recipientColorDotColor()).isEqual(accountColor))
        XCTAssertEqual(metadataFrame.maxX, nameFrame.maxX, accuracy: 0.5)
    }

    func testGroupDetailMessageDotRejectsAccountOutsideCurrentGroup() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 4)
        let relatedAccounts = Array(accounts.prefix(3))
        let unrelatedAccount = accounts[3]
        let group = try XCTUnwrap(
            controller.state.friends.first { controller.isGroupConversation($0) }
        )
        controller.explicitGroupMemberIDsByGroupID[group.id] = relatedAccounts.map(\.id)
        controller.state.selectedAccountID = relatedAccounts[1].id
        controller.state.selectedFriendID = group.id

        let message = ChatMessage(
            id: UUID(),
            conversationID: group.id,
            type: .text,
            sender: unrelatedAccount,
            body: "群外帐号不应产生紫色消息点",
            detail: "接收帐号ID：\(unrelatedAccount.id.uuidString)",
            isOutgoing: true,
            presentation: .avatarAndName,
            timestamp: "12:00",
            isGroupConversation: true,
            recipientAccountID: unrelatedAccount.id
        )

        XCTAssertNil(controller.outgoingGroupAccountID(for: message))
    }

    func testGroupDetailMessageDotDoesNotChangeWithSelectedAccount() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 3)
        let relatedAccounts = Array(accounts.prefix(3))
        let group = try XCTUnwrap(
            controller.state.friends.first { controller.isGroupConversation($0) }
        )
        controller.explicitGroupMemberIDsByGroupID[group.id] = relatedAccounts.map(\.id)
        controller.state.selectedFriendID = group.id

        let message = ChatMessage(
            id: UUID(),
            conversationID: group.id,
            type: .text,
            sender: group,
            body: "固定绑定第三个主帐号",
            detail: "接收帐号ID：\(relatedAccounts[2].id.uuidString)",
            isOutgoing: true,
            presentation: .avatarAndName,
            timestamp: "12:00",
            isGroupConversation: true
        )

        controller.state.selectedAccountID = relatedAccounts[0].id
        let beforeSwitch = controller.outgoingGroupAccountID(for: message)
        controller.state.selectedAccountID = relatedAccounts[1].id
        let afterSwitch = controller.outgoingGroupAccountID(for: message)

        XCTAssertEqual(beforeSwitch, relatedAccounts[2].id)
        XCTAssertEqual(afterSwitch, relatedAccounts[2].id)
        XCTAssertEqual(beforeSwitch, afterSwitch)
    }

    func testUnreadTimelineFixtureUsesFixedTimestamps() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let first = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let second = ChatDataFactory.makeUnreadTimelineTestData(accounts: Array(accounts.reversed()))

        XCTAssertEqual(first.messages.map(\.id), second.messages.map(\.id))
        XCTAssertEqual(first.messages.map(\.sentAt), second.messages.map(\.sentAt))
        XCTAssertEqual(
            try XCTUnwrap(first.messages.first).sentAt,
            Date(timeIntervalSince1970: 1_784_786_400)
        )
        XCTAssertEqual(first.conversationKindsByID, second.conversationKindsByID)
        XCTAssertEqual(first.relatedAccountIDsByConversationID, second.relatedAccountIDsByConversationID)
        XCTAssertEqual(first.groupMemberIDsByGroupID, second.groupMemberIDsByGroupID)
    }

    func testUnreadTimelineFixtureSatisfiesBackendDataContract() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let participantByID = Dictionary(uniqueKeysWithValues: fixture.participants.map { ($0.id, $0) })
        let accountIDs = Set(accounts.map(\.id))

        XCTAssertEqual(participantByID.count, fixture.participants.count)
        XCTAssertEqual(Set(fixture.messages.map(\.id)).count, fixture.messages.count)
        XCTAssertFalse(fixture.conversationKindsByID.isEmpty)
        XCTAssertTrue(fixture.conversationKindsByID.values.contains(.contact))
        XCTAssertTrue(fixture.conversationKindsByID.values.contains(.chatroom))

        for (conversationID, kind) in fixture.conversationKindsByID {
            let conversation = try XCTUnwrap(participantByID[conversationID])
            XCTAssertEqual(conversation.kind == .group, kind == .chatroom)
            let relatedIDs = try XCTUnwrap(fixture.relatedAccountIDsByConversationID[conversationID])
            XCTAssertFalse(relatedIDs.isEmpty)
            XCTAssertTrue(Set(relatedIDs).isSubset(of: accountIDs))
            XCTAssertEqual(
                Set(relatedIDs),
                Set(fixture.messages.filter { $0.conversationID == conversationID }.compactMap(\.recipientAccountID))
            )
            if kind == .chatroom {
                let memberIDs = try XCTUnwrap(fixture.groupMemberIDsByGroupID[conversationID])
                XCTAssertFalse(memberIDs.isEmpty)
                XCTAssertTrue(memberIDs.allSatisfy { participantByID[$0]?.kind == .groupMember })
            }
        }

        for message in fixture.messages {
            let kind = try XCTUnwrap(fixture.conversationKindsByID[message.conversationID])
            XCTAssertNotNil(participantByID[message.conversationID])
            XCTAssertTrue(participantByID[message.sender.id] != nil || accountIDs.contains(message.sender.id))
            XCTAssertEqual(message.isGroupConversation, kind == .chatroom)
            let ownerID = try XCTUnwrap(message.recipientAccountID)
            XCTAssertTrue(accountIDs.contains(ownerID))
            XCTAssertTrue(fixture.relatedAccountIDsByConversationID[message.conversationID]?.contains(ownerID) == true)
            if message.isOutgoing {
                XCTAssertEqual(message.sender.id, ownerID)
            } else if kind == .chatroom {
                XCTAssertTrue(fixture.groupMemberIDsByGroupID[message.conversationID]?.contains(message.sender.id) == true)
            } else {
                XCTAssertEqual(message.sender.id, message.conversationID)
            }

            for element in message.richElements {
                guard let urlString = richElementURL(element) else { continue }
                let url = try XCTUnwrap(URL(string: urlString))
                XCTAssertEqual(url.scheme, "https")
                XCTAssertNotEqual(url.host, "demo.invalid")
            }
        }
    }

    func testDemoFixtureIsStableAndUsesTypedOwnership() throws {
        let first = ChatDataFactory.makeDemoState()
        let second = ChatDataFactory.makeDemoState()
        let participantByID = Dictionary(uniqueKeysWithValues: first.participants.map { ($0.id, $0) })
        let accountIDs = Set(first.currentUsers.map(\.id))

        XCTAssertEqual(first.participants.map(\.id), second.participants.map(\.id))
        XCTAssertEqual(first.messages.map(\.id), second.messages.map(\.id))
        XCTAssertEqual(first.messages.map(\.sentAt), second.messages.map(\.sentAt))
        XCTAssertTrue(first.participants.contains { $0.kind == .contact })
        XCTAssertTrue(first.participants.contains { $0.kind == .group })
        XCTAssertTrue(first.participants.contains { $0.kind == .assistant })

        for message in first.messages {
            let conversation = try XCTUnwrap(participantByID[message.conversationID])
            XCTAssertEqual(message.isGroupConversation, conversation.kind == .group)
            let recipientAccountID = try XCTUnwrap(message.recipientAccountID)
            XCTAssertTrue(accountIDs.contains(recipientAccountID))
            if message.isOutgoing {
                XCTAssertEqual(message.sender.id, recipientAccountID)
            }
            for element in message.richElements {
                guard let urlString = richElementURL(element) else { continue }
                let url = try XCTUnwrap(URL(string: urlString))
                XCTAssertEqual(url.scheme, "https")
                XCTAssertFalse(url.host?.hasSuffix(".invalid") == true)
                XCTAssertFalse(url.host?.hasSuffix(".ai") == true)
            }
        }
    }

    func testParticipantKindDecodesOldRowsWithoutKind() throws {
        let account = ChatParticipant(
            id: UUID(),
            displayName: "兼容帐号",
            tintColor: .systemBlue,
            initials: "兼",
            isCurrentUser: true
        )
        let encoded = try JSONEncoder().encode(account)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        object.removeValue(forKey: "kind")
        let legacyData = try JSONSerialization.data(withJSONObject: object)

        XCTAssertEqual(try JSONDecoder().decode(ChatParticipant.self, from: legacyData).kind, .account)
    }

    func testSQLiteRoundTripPreservesTypedIdentityAndOwnership() throws {
        let databaseURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("chat-contract-\(UUID().uuidString).sqlite")
        defer {
            try? FileManager.default.removeItem(at: databaseURL)
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-wal"))
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-shm"))
        }

        let state = ChatDataFactory.makeDemoState()
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: state.currentUsers)
        let participants = state.currentUsers + fixture.participants
        let store = ChatSQLiteStore(databaseURL: databaseURL)
        store.replaceMessages(fixture.messages)
        store.replaceIMCache(
            participants: participants,
            messages: fixture.messages,
            contactCards: state.contactCards,
            selectedAccountID: state.selectedAccountID
        )
        store.waitForPendingWrites()
        let loaded = store.loadMessages(participants: participants)

        XCTAssertEqual(loaded.map(\.id), fixture.messages.map(\.id))
        XCTAssertEqual(loaded.map(\.conversationID), fixture.messages.map(\.conversationID))
        XCTAssertEqual(loaded.map(\.sender.id), fixture.messages.map(\.sender.id))
        XCTAssertEqual(loaded.map(\.recipientAccountID), fixture.messages.map(\.recipientAccountID))
        XCTAssertEqual(loaded.map(\.isGroupConversation), fixture.messages.map(\.isGroupConversation))
        XCTAssertEqual(
            loaded.compactMap { message in
                participants.first(where: { $0.id == message.conversationID })?.kind
            },
            fixture.messages.compactMap { message in
                participants.first(where: { $0.id == message.conversationID })?.kind
            }
        )
    }

    func testIncrementalMessageInsertCreatesMissingConversation() throws {
        let databaseURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("chat-incremental-conversation-\(UUID().uuidString).sqlite")
        defer {
            try? FileManager.default.removeItem(at: databaseURL)
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-wal"))
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-shm"))
        }

        let conversationID = UUID()
        let accountID = UUID()
        let sender = ChatParticipant(
            id: conversationID,
            displayName: "首次来信好友",
            tintColor: .systemGreen,
            initials: "首",
            isCurrentUser: false,
            kind: .contact
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .text,
            sender: sender,
            body: "第一条消息",
            detail: "",
            isOutgoing: false,
            presentation: .avatarOnly,
            timestamp: "",
            sentAt: Date(),
            recipientAccountID: accountID
        )
        let store = ChatSQLiteStore(databaseURL: databaseURL)

        store.applyMessageMutations([.insert(message)])
        store.waitForPendingWrites()

        XCTAssertTrue(store.hasConversation(id: conversationID))
        XCTAssertEqual(store.loadMessages(participants: [sender]).map(\.id), [message.id])
    }

    func testOpeningExistingDatabaseRepairsMissingConversation() throws {
        let databaseURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("chat-orphan-repair-\(UUID().uuidString).sqlite")
        defer {
            try? FileManager.default.removeItem(at: databaseURL)
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-wal"))
            try? FileManager.default.removeItem(at: URL(fileURLWithPath: databaseURL.path + "-shm"))
        }

        let conversationID = UUID()
        let sender = ChatParticipant(
            id: conversationID,
            displayName: "历史孤立好友",
            tintColor: .systemBlue,
            initials: "历",
            isCurrentUser: false,
            kind: .contact
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .text,
            sender: sender,
            body: "历史消息",
            detail: "",
            isOutgoing: false,
            presentation: .avatarOnly,
            timestamp: "",
            sentAt: Date(),
            recipientAccountID: UUID()
        )
        let oldStore = ChatSQLiteStore(databaseURL: databaseURL)
        oldStore.replaceMessages([message])
        oldStore.waitForPendingWrites()
        XCTAssertFalse(oldStore.hasConversation(id: conversationID))

        let migratedStore = ChatSQLiteStore(databaseURL: databaseURL)

        XCTAssertTrue(migratedStore.hasConversation(id: conversationID))
    }

    private func richElementURL(_ element: BubbleRichElement) -> String? {
        switch element {
        case .blueLink(_, let url, _), .taggedFile(_, let url, _),
             .image(_, let url, _, _), .inlineCard(_, _, _, let url),
             .location(_, _, let url), .file(_, _, let url, _, _):
            return url
        case .text, .mention, .aiToken, .quote:
            return nil
        }
    }

    func testPartialGroupRestoresOutgoingMessageOwnersAndStableColors() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 4)
        let groupID = ChatDataFactory.unreadTimelinePartialAccountGroupID
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        controller.state.participants.append(contentsOf: fixture.participants)
        let owningAccounts = [accounts[0], accounts[1], accounts[2]]
        let oldMessages = owningAccounts.enumerated().flatMap { accountIndex, account in
            (0..<2).map { messageIndex in
                ChatMessage(
                    id: UUID(),
                    conversationID: groupID,
                    type: .text,
                    sender: account,
                    body: "旧消息 \(accountIndex)-\(messageIndex)",
                    detail: "",
                    isOutgoing: true,
                    presentation: .avatarAndName,
                    timestamp: "12:00",
                    sentAt: Date(timeIntervalSince1970: 1_784_780_000 + Double(accountIndex * 10 + messageIndex)),
                    isGroupConversation: true
                )
            }
        }
        controller.state.messages = oldMessages

        controller.installUnreadTimelineTestDataIfNeeded()

        let group = try XCTUnwrap(controller.state.participants.first { $0.id == groupID })
        controller.state.selectedFriendID = group.id
        XCTAssertEqual(controller.currentAccountIDsInGroup(group), Set(owningAccounts.map(\.id)))
        for oldMessage in oldMessages {
            let migrated = try XCTUnwrap(controller.state.messages.first { $0.id == oldMessage.id })
            XCTAssertEqual(migrated.recipientAccountID, oldMessage.sender.id)
            XCTAssertEqual(controller.outgoingGroupAccountID(for: migrated), oldMessage.sender.id)
            XCTAssertEqual(controller.groupAccountID(for: migrated, in: group), oldMessage.sender.id)
        }
        let colors = owningAccounts.map { controller.unansweredAccountColor(for: $0.id) }
        XCTAssertFalse(colors[0].isEqual(colors[1]))
        XCTAssertFalse(colors[1].isEqual(colors[2]))
        XCTAssertFalse(colors[0].isEqual(colors[2]))
    }

    func testGroupMessagesDoNotChangeWhenSelectedAccountChanges() throws {
        let controller = ChatWindowViewController()
        controller.state = ChatDataFactory.makeDemoState()
        let accounts = controller.state.currentUsers
        XCTAssertGreaterThanOrEqual(accounts.count, 3)
        let group = try XCTUnwrap(
            controller.state.friends.first { controller.isGroupConversation($0) }
        )
        controller.explicitGroupMemberIDsByGroupID[group.id] = Array(accounts.prefix(3).map(\.id))
        controller.state.selectedFriendID = group.id
        let messages = Array(accounts.prefix(3)).enumerated().map { index, account in
            ChatMessage(
                id: UUID(),
                conversationID: group.id,
                type: .text,
                sender: account,
                body: "帐号 \(index) 的固定消息",
                detail: "AI自动回复预测\n接收帐号ID：\(account.id.uuidString)",
                isOutgoing: true,
                presentation: .avatarAndName,
                timestamp: "12:00",
                sentAt: Date(timeIntervalSince1970: 1_784_780_000 + Double(index)),
                isGroupConversation: true,
                recipientAccountID: account.id
            )
        }
        controller.state.messages = messages

        controller.state.selectedAccountID = accounts[0].id
        let firstSelectionIDs = controller.scopedVisibleMessagesForCurrentConversation().map(\.id)
        controller.state.selectedAccountID = accounts[1].id
        let secondSelectionIDs = controller.scopedVisibleMessagesForCurrentConversation().map(\.id)

        XCTAssertEqual(firstSelectionIDs, messages.map(\.id))
        XCTAssertEqual(secondSelectionIDs, messages.map(\.id))
    }

    func testSharedDirectFriendKeepsMessageOwningAccountIsolated() throws {
        let controller = ChatWindowViewController()
        let firstAccount = ChatParticipant(
            id: UUID(), displayName: "帐号 A", tintColor: .systemBlue,
            initials: "A", isCurrentUser: true, kind: .account
        )
        let secondAccount = ChatParticipant(
            id: UUID(), displayName: "帐号 B", tintColor: .systemGreen,
            initials: "B", isCurrentUser: true, kind: .account
        )
        let sharedFriend = ChatParticipant(
            id: UUID(), displayName: "共同好友", tintColor: .systemOrange,
            initials: "友", isCurrentUser: false, kind: .contact
        )
        let firstMessage = ChatMessage(
            id: UUID(), conversationID: sharedFriend.id, type: .text,
            sender: sharedFriend, body: "发给帐号 A", detail: "",
            isOutgoing: false, presentation: .avatarOnly, timestamp: "",
            sentAt: Date(timeIntervalSince1970: 100), recipientAccountID: firstAccount.id
        )
        let secondMessage = ChatMessage(
            id: UUID(), conversationID: sharedFriend.id, type: .text,
            sender: sharedFriend, body: "发给帐号 B", detail: "",
            isOutgoing: false, presentation: .avatarOnly, timestamp: "",
            sentAt: Date(timeIntervalSince1970: 200), recipientAccountID: secondAccount.id
        )
        controller.state = ChatDataFactory.makeDemoState()
        controller.state.participants = [firstAccount, secondAccount, sharedFriend]
        controller.state.messages = [firstMessage, secondMessage]
        controller.state.selectedFriendID = sharedFriend.id
        controller.state.selectedAccountID = secondAccount.id
        // Simulate an incomplete relationship snapshot that only retained A.
        controller.openApiRelatedAccountIDsByConversationID[sharedFriend.id] = [firstAccount.id]

        XCTAssertEqual(
            controller.accountIDForDirectConversation(
                sharedFriend,
                preferredAccountID: secondAccount.id
            ),
            secondAccount.id
        )
        XCTAssertEqual(
            controller.scopedVisibleMessagesForCurrentConversation().map(\.id),
            [secondMessage.id]
        )
    }

    func testConnectionOverlayIgnoresNonFiniteAnchors() {
        let overlay = ConnectionOverlayView(frame: CGRect(x: 0, y: 0, width: 320, height: 480))
        overlay.anchors = [
            ConnectionAnchor(
                from: CGPoint(x: CGFloat.nan, y: 20),
                to: CGPoint(x: 120, y: 40),
                color: .systemBlue,
                direction: .leftToMessage
            ),
            ConnectionAnchor(
                from: CGPoint(x: 10, y: 20),
                to: CGPoint(x: 120, y: 40),
                color: .systemGreen,
                direction: .leftToMessage
            )
        ]

        overlay.layoutIfNeeded()

        XCTAssertEqual(overlay.anchors.count, 2)
    }

    func testMaximumBubbleWidthUsesNinetyTwoPercentRatio() {
        let containerWidth: CGFloat = 1_000
        let connectionInset = MessageConnectionLayoutMetrics.resolvedConnectionSideInset(
            in: containerWidth
        )
        XCTAssertEqual(
            MessageConnectionLayoutMetrics.maximumBubbleWidth(in: containerWidth),
            920,
            accuracy: 0.001
        )
        XCTAssertEqual(connectionInset, 11, accuracy: 0.001)
        XCTAssertEqual(MessageConnectionLayoutMetrics.bubbleOppositeSideInset, 11, accuracy: 0.001)
    }

    func testSidebarAvatarCanAlignToFirstUnreadBubbleCenter() throws {
        let item = try XCTUnwrap(ChatDataFactory.makeDemoState().leftItems.first)
        let cell = SidebarCell(frame: CGRect(x: 0, y: 0, width: 50, height: 180))
        cell.configure(with: item, alignsToTop: true, topOffset: 0)
        cell.layoutIfNeeded()

        cell.alignConnectionCenterY(94)

        XCTAssertEqual(cell.connectionAnchorPoint(in: cell).y, 94, accuracy: 0.25)
    }

    func testGroupCheckInMiniProgramHeightContainsRenderedBubble() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let message = try XCTUnwrap(fixture.messages.first { $0.body == "签到小程序" })

        for width: CGFloat in [220, 260, 320] {
            let estimatedHeight = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: width,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            let bubble = ChatMessageBubbleView(frame: CGRect(x: 0, y: 0, width: width, height: 1))
            bubble.configure(
                with: message,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            let fittedHeight = bubble.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            ).height

            XCTAssertGreaterThanOrEqual(
                estimatedHeight,
                fittedHeight,
                "width=\(width), estimated=\(estimatedHeight), fitted=\(fittedHeight)"
            )
        }
    }

    func testUnreadMixedCardsDoNotReserveExcessVerticalSpace() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let targetBodies = [
            "18 秒语音",
            "项目需求文档.pdf",
            "活动页面链接",
            "集合点位置",
            "转账 ¥88.00"
        ]

        for usesUnansweredPresentation in [true, false] {
            for body in targetBodies {
                let message = try XCTUnwrap(fixture.messages.first { $0.body == body })
                for width: CGFloat in [220, 260, 320] {
                    let estimatedHeight = ChatMessageBubbleView.estimatedHeight(
                        for: message,
                        width: width,
                        showsGroupAvatar: false,
                        showsGroupName: true,
                        usesUnansweredPresentation: usesUnansweredPresentation
                    )
                    let bubble = ChatMessageBubbleView(frame: CGRect(x: 0, y: 0, width: width, height: 1))
                    bubble.configure(
                        with: message,
                        showsGroupAvatar: false,
                        showsGroupName: true,
                        usesUnansweredPresentation: usesUnansweredPresentation
                    )
                    let fittedHeight = bubble.systemLayoutSizeFitting(
                        CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                        withHorizontalFittingPriority: .required,
                        verticalFittingPriority: .fittingSizeLevel
                    ).height

                    XCTAssertGreaterThanOrEqual(
                        estimatedHeight,
                        fittedHeight,
                        "home=\(usesUnansweredPresentation), body=\(body), width=\(width), estimated=\(estimatedHeight), fitted=\(fittedHeight)"
                    )
                    XCTAssertLessThanOrEqual(
                        estimatedHeight - fittedHeight,
                        5,
                        "home=\(usesUnansweredPresentation), body=\(body), width=\(width), estimated=\(estimatedHeight), fitted=\(fittedHeight)"
                    )
                }
            }
        }
    }

    func testDirectActivityLinkConnectionUsesCompactBubbleCenter() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let message = try XCTUnwrap(fixture.messages.first { $0.body == "活动页面链接" })
        let width: CGFloat = 320
        let estimatedHeight = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: width,
            showsGroupAvatar: false,
            showsGroupName: true
        )
        let bubble = ChatMessageBubbleView(frame: CGRect(x: 0, y: 0, width: width, height: 1))
        bubble.configure(with: message, showsGroupAvatar: false, showsGroupName: true)
        let fittedHeight = bubble.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        ).height
        let cell = ChatMessageCell(frame: CGRect(x: 0, y: 0, width: width, height: estimatedHeight + 4))
        cell.configure(
            with: message,
            showsGroupAvatar: false,
            showsGroupName: true,
            groupTopSpacing: 4
        )
        cell.layoutIfNeeded()
        cell.contentView.layoutIfNeeded()

        let connectionFrame = try XCTUnwrap(
            cell.cachedConnectionFrame(in: cell, for: message.id, usesOuterBubble: false)
        )
        XCTAssertEqual(connectionFrame.midY, 4 + fittedHeight / 2, accuracy: 2)
        XCTAssertLessThanOrEqual(connectionFrame.height, 40)
    }

    func testMessageCellConnectionFrameRequiresCurrentLaidOutMessage() throws {
        let state = ChatDataFactory.makeDemoState()
        let sender = try XCTUnwrap(state.friends.first)
        let message = ChatMessage(
            id: UUID(),
            conversationID: sender.id,
            type: .text,
            sender: sender,
            body: "连接线保留区测试",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "",
            sentAt: Date()
        )
        let cell = ChatMessageCell(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
        cell.configure(with: message)

        XCTAssertNil(
            cell.cachedConnectionFrame(
                in: cell,
                for: message.id,
                usesOuterBubble: false
            )
        )

        cell.layoutIfNeeded()
        cell.contentView.layoutIfNeeded()

        let frame = try XCTUnwrap(
            cell.cachedConnectionFrame(
                in: cell,
                for: message.id,
                usesOuterBubble: false
            )
        )
        XCTAssertEqual(
            frame.minX,
            MessageConnectionLayoutMetrics.resolvedConnectionSideInset(in: cell.bounds.width),
            accuracy: 1
        )
        XCTAssertNil(
            cell.cachedConnectionFrame(
                in: cell,
                for: UUID(),
                usesOuterBubble: false
            )
        )
    }

    func testGroupMemberAvatarCentersOnBubbleAndKeepsExpectedGap() throws {
        XCTAssertEqual(MessageConnectionLayoutMetrics.groupMemberAvatarToBubbleGap, 4)
        XCTAssertEqual(MessageConnectionLayoutMetrics.groupTrunkToMemberAvatarGap, 4)

        let sender = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemTeal,
            initials: "群"
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: UUID(),
            type: .text,
            sender: sender,
            body: "这是一条用于验证群成员头像与多行消息框严格垂直居中的较长群聊消息。",
            detail: "",
            isOutgoing: false,
            presentation: .avatarAndName,
            timestamp: "12:00",
            sentAt: Date(),
            isGroupConversation: true
        )
        let width: CGFloat = 320
        let height = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: width,
            showsGroupAvatar: true,
            showsGroupName: true
        )
        let cell = ChatMessageCell(frame: CGRect(x: 0, y: 0, width: width, height: height))
        cell.configure(with: message, showsGroupAvatar: true, showsGroupName: true)
        cell.layoutIfNeeded()
        cell.contentView.layoutIfNeeded()

        let bubbleFrame = try XCTUnwrap(
            cell.cachedConnectionFrame(in: cell, for: message.id, usesOuterBubble: true)
        )
        let avatarConnectionPoint = try XCTUnwrap(
            cell.cachedGroupMemberAvatarConnectionPoint(in: cell, for: message.id)
        )
        XCTAssertEqual(avatarConnectionPoint.y, bubbleFrame.midY, accuracy: 0.5)
        XCTAssertEqual(
            avatarConnectionPoint.x
                + MessageConnectionLayoutMetrics.groupMemberAvatarSide
                + MessageConnectionLayoutMetrics.groupMemberAvatarToBubbleGap,
            bubbleFrame.minX,
            accuracy: 0.5
        )

        cell.configure(with: message, showsGroupAvatar: false, showsGroupName: true)
        cell.layoutIfNeeded()
        cell.contentView.layoutIfNeeded()
        XCTAssertNil(
            cell.cachedGroupMemberAvatarConnectionPoint(in: cell, for: message.id)
        )
    }

    func testGroupMemberAvatarCentersOnMiniProgramCard() throws {
        let accounts = ChatDataFactory.makeDemoState().currentUsers
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: accounts)
        let message = try XCTUnwrap(fixture.messages.first { $0.body == "签到小程序" })
        let width: CGFloat = 320
        let height = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: width,
            showsGroupAvatar: true,
            showsGroupName: true
        )
        let cell = ChatMessageCell(frame: CGRect(x: 0, y: 0, width: width, height: height))
        cell.configure(with: message, showsGroupAvatar: true, showsGroupName: true)
        cell.layoutIfNeeded()
        cell.contentView.layoutIfNeeded()

        let bubbleFrame = try XCTUnwrap(
            cell.cachedConnectionFrame(in: cell, for: message.id, usesOuterBubble: true)
        )
        let avatarConnectionPoint = try XCTUnwrap(
            cell.cachedGroupMemberAvatarConnectionPoint(in: cell, for: message.id)
        )
        XCTAssertEqual(avatarConnectionPoint.y, bubbleFrame.midY, accuracy: 0.5)
    }

    func testUnreadRecipientAvatarsStayAnchoredToBubbleBottomRight() throws {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "群成员",
            tintColor: .systemTeal,
            initials: "群"
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: UUID(),
            type: .text,
            sender: sender,
            body: "右下角头像锚点测试",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "12:00",
            sentAt: Date(),
            isGroupConversation: true
        )
        let colors: [UIColor] = [.systemBlue, .systemOrange, .systemPink, .systemGreen, .systemPurple, .systemRed]
        let recipientItems = (0..<6).map { index in
            let id = UUID()
            return SidebarItem(
                id: id,
                kind: .account,
                title: "帐号\(index)",
                symbolName: "person.crop.circle.fill",
                tintColor: colors[index],
                participantID: id,
                targetMessageID: message.id,
                isActive: true
            )
        }
        let bubble = ChatMessageBubbleView(frame: CGRect(x: 0, y: 0, width: 320, height: 96))
        bubble.configure(
            with: message,
            showsGroupAvatar: false,
            showsGroupName: true,
            usesUnansweredPresentation: true,
            unansweredRecipientColor: recipientItems[0].tintColor,
            unansweredRecipientItems: recipientItems
        )
        bubble.layoutIfNeeded()

        let bubbleFrame = bubble.connectionBubbleFrame(in: bubble)
        let watermarkFrame = try XCTUnwrap(bubble.recipientWatermarkFrame(in: bubble))
        let rightmostAvatarFrame = try XCTUnwrap(
            bubble.recipientWatermarkRightmostAvatarFrame(in: bubble)
        )
        XCTAssertEqual(watermarkFrame.maxX, bubbleFrame.maxX, accuracy: 0.5)
        XCTAssertEqual(watermarkFrame.maxY, bubbleFrame.maxY, accuracy: 0.5)
        XCTAssertEqual(rightmostAvatarFrame.maxX, bubbleFrame.maxX, accuracy: 0.5)
        XCTAssertEqual(rightmostAvatarFrame.maxY, bubbleFrame.maxY, accuracy: 0.5)
        XCTAssertEqual(
            watermarkFrame.width,
            UnansweredRecipientAvatarStackView.avatarSide
                + UnansweredRecipientAvatarStackView.visibleOverlapStep * 2,
            accuracy: 0.5
        )

        bubble.setUnansweredRecipientItems([recipientItems[0]])
        bubble.layoutIfNeeded()
        let singleWatermarkFrame = try XCTUnwrap(bubble.recipientWatermarkFrame(in: bubble))
        let singleAvatarFrame = try XCTUnwrap(
            bubble.recipientWatermarkRightmostAvatarFrame(in: bubble)
        )
        XCTAssertEqual(singleWatermarkFrame.maxX, bubbleFrame.maxX, accuracy: 0.5)
        XCTAssertEqual(singleWatermarkFrame.maxY, bubbleFrame.maxY, accuracy: 0.5)
        XCTAssertEqual(singleAvatarFrame.maxX, bubbleFrame.maxX, accuracy: 0.5)
        XCTAssertEqual(singleAvatarFrame.maxY, bubbleFrame.maxY, accuracy: 0.5)
    }

    func testTransparentImagePreviewTrimsLargeEmptyCanvas() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let source = UIGraphicsImageRenderer(
            size: CGSize(width: 120, height: 720),
            format: format
        ).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 16, y: 320, width: 88, height: 58))
        }

        let trimmed = source.trimmedTransparentCanvasIfNeeded()

        XCTAssertLessThan(trimmed.size.height, source.size.height * 0.24)
        XCTAssertGreaterThan(trimmed.size.width, trimmed.size.height)
    }

    func testMediaThumbnailPipelineCoalescesIdenticalRequests() {
        let decodeStarted = expectation(description: "decode started")
        let callbacks = expectation(description: "coalesced callbacks")
        callbacks.expectedFulfillmentCount = 2
        let allowDecodeToFinish = DispatchSemaphore(value: 0)
        let decodedImage = makeSolidImage(size: CGSize(width: 8, height: 8))
        let counterLock = NSLock()
        var decodeCount = 0
        let pipeline = MediaThumbnailPipeline(decoder: { _, _, _ in
            counterLock.lock()
            decodeCount += 1
            counterLock.unlock()
            decodeStarted.fulfill()
            allowDecodeToFinish.wait()
            return decodedImage
        })
        let url = URL(fileURLWithPath: "/tmp/coalesced-\(UUID().uuidString).png")

        let first = pipeline.load(
            url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 80, height: 80),
            scale: 2
        ) { _ in callbacks.fulfill() }
        let second = pipeline.load(
            url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 80, height: 80),
            scale: 2
        ) { _ in callbacks.fulfill() }

        wait(for: [decodeStarted], timeout: 1)
        allowDecodeToFinish.signal()
        wait(for: [callbacks], timeout: 1)
        counterLock.lock()
        let finalDecodeCount = decodeCount
        counterLock.unlock()
        XCTAssertEqual(finalDecodeCount, 1)
        withExtendedLifetime([first, second]) {}
    }

    func testMediaThumbnailPipelineCancellationSuppressesCallback() {
        let decodeStarted = expectation(description: "decode started")
        let callback = expectation(description: "cancelled callback")
        callback.isInverted = true
        let allowDecodeToFinish = DispatchSemaphore(value: 0)
        let decodedImage = makeSolidImage(size: CGSize(width: 8, height: 8))
        let pipeline = MediaThumbnailPipeline(decoder: { _, _, _ in
            decodeStarted.fulfill()
            allowDecodeToFinish.wait()
            return decodedImage
        })
        let request = pipeline.load(
            URL(fileURLWithPath: "/tmp/cancelled-\(UUID().uuidString).png"),
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 80, height: 80),
            scale: 2
        ) { _ in callback.fulfill() }

        wait(for: [decodeStarted], timeout: 1)
        request.cancel()
        allowDecodeToFinish.signal()
        wait(for: [callback], timeout: 0.2)
    }

    func testMediaThumbnailPipelineUsesTargetPixelSizeInCacheKey() {
        let callbacks = expectation(description: "different size callbacks")
        callbacks.expectedFulfillmentCount = 2
        let decodedImage = makeSolidImage(size: CGSize(width: 8, height: 8))
        let dimensionsLock = NSLock()
        var decodedDimensions: [Int] = []
        let pipeline = MediaThumbnailPipeline(decoder: { _, _, maximumPixelDimension in
            dimensionsLock.lock()
            decodedDimensions.append(maximumPixelDimension)
            dimensionsLock.unlock()
            return decodedImage
        })
        let url = URL(fileURLWithPath: "/tmp/sized-\(UUID().uuidString).png")

        let first = pipeline.load(
            url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 32, height: 32),
            scale: 2
        ) { _ in callbacks.fulfill() }
        let second = pipeline.load(
            url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 96, height: 96),
            scale: 2
        ) { _ in callbacks.fulfill() }

        wait(for: [callbacks], timeout: 1)
        dimensionsLock.lock()
        let finalDimensions = decodedDimensions.sorted()
        dimensionsLock.unlock()
        XCTAssertEqual(finalDimensions, [64, 192])
        withExtendedLifetime([first, second]) {}
    }

    func testMediaThumbnailPipelineDownsamplesToRequestedPixelDimension() throws {
        let sourceImage = makeSolidImage(size: CGSize(width: 400, height: 200))
        let sourceData = try XCTUnwrap(sourceImage.pngData())
        let sourceURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("media-pipeline-\(UUID().uuidString).png")
        try sourceData.write(to: sourceURL, options: .atomic)
        defer { try? FileManager.default.removeItem(at: sourceURL) }

        let callback = expectation(description: "downsampled image")
        let pipeline = MediaThumbnailPipeline()
        var decodedSize = CGSize.zero
        let request = pipeline.load(
            sourceURL,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: CGSize(width: 50, height: 50),
            scale: 2
        ) { image in
            if let cgImage = image?.cgImage {
                decodedSize = CGSize(width: cgImage.width, height: cgImage.height)
            }
            callback.fulfill()
        }

        wait(for: [callback], timeout: 2)
        XCTAssertEqual(decodedSize.width, 100)
        XCTAssertEqual(decodedSize.height, 50)
        withExtendedLifetime(request) {}
    }

    func testAvatarPipelineCoalescesFortyReferencesToThreeUniqueURLs() {
        XCTAssertEqual(AvatarPipeline.decodedByteBudget, 24 * 1_024 * 1_024)

        let decodeStarted = expectation(description: "three avatar decodes started")
        decodeStarted.expectedFulfillmentCount = 3
        let callbacks = expectation(description: "forty avatar callbacks")
        callbacks.expectedFulfillmentCount = 40
        let allowDecodesToFinish = DispatchSemaphore(value: 0)
        let decodedImage = makeSolidImage(size: CGSize(width: 16, height: 16))
        let counterLock = NSLock()
        var decodeCount = 0
        let pipeline = AvatarPipeline(decoder: { _, _, _ in
            counterLock.lock()
            decodeCount += 1
            counterLock.unlock()
            decodeStarted.fulfill()
            allowDecodesToFinish.wait()
            return decodedImage
        })
        let urls = (0..<3).map {
            URL(fileURLWithPath: "/tmp/avatar-\($0)-\(UUID().uuidString).png")
        }
        var requests: [MediaThumbnailRequest] = []
        for index in 0..<40 {
            requests.append(
                pipeline.load(
                    urls[index % urls.count],
                    targetSize: CGSize(width: 50, height: 50),
                    scale: 3
                ) { _ in callbacks.fulfill() }
            )
        }

        wait(for: [decodeStarted], timeout: 1)
        for _ in 0..<3 { allowDecodesToFinish.signal() }
        wait(for: [callbacks], timeout: 1)
        counterLock.lock()
        let finalDecodeCount = decodeCount
        counterLock.unlock()
        XCTAssertEqual(finalDecodeCount, 3)
        withExtendedLifetime(requests) {}
    }

    func testUnreadTimelineAvatarPinsInsideItsOwnTallGroup() {
        let group = CGRect(x: 0, y: 100, width: 300, height: 500)
        let avatar = CGRect(x: 0, y: 112, width: 50, height: 50)

        XCTAssertEqual(
            UnreadTimelineAvatarPinning.topOffset(
                groupFrame: group,
                naturalAvatarFrame: avatar,
                viewportTop: 80,
                viewportBottom: 800,
                avatarHeight: 50
            ),
            12
        )
        XCTAssertEqual(
            UnreadTimelineAvatarPinning.topOffset(
                groupFrame: group,
                naturalAvatarFrame: avatar,
                viewportTop: 320,
                viewportBottom: 800,
                avatarHeight: 50
            ),
            220
        )
        XCTAssertEqual(
            UnreadTimelineAvatarPinning.topOffset(
                groupFrame: group,
                naturalAvatarFrame: avatar,
                viewportTop: 590,
                viewportBottom: 800,
                avatarHeight: 50
            ),
            450
        )
    }

    private func makeSolidImage(size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
    }
}
