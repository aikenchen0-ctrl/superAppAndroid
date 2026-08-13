import UIKit

enum ChatDataFactory {
    struct UnreadTimelineFixture {
        let participants: [ChatParticipant]
        let messages: [ChatMessage]
        let conversationKindsByID: [UUID: OpenApiConversationKind]
        let relatedAccountIDsByConversationID: [UUID: [UUID]]
        let groupMemberIDsByGroupID: [UUID: [UUID]]
    }

    static func makeLiveLoadingState() -> ChatDemoState {
#if DEBUG
        if let scenario = requestedPerformanceScenario {
            return makePerformanceScenarioState(scenario)
        }
#endif
        let account = ChatParticipant(
            id: stableParticipantID(prefix: "00000000", index: 0),
            displayName: "同步中",
            tintColor: UIColor(red: 0.18, green: 0.32, blue: 0.36, alpha: 1),
            initials: "同",
            isCurrentUser: true
        )
        let contactCard = ContactCardProfile(
            accountID: account.id,
            displayName: "同步中",
            role: "正在拉取真实帐号",
            company: "只发 SCRM",
            wechatID: "",
            phone: "",
            bio: "启动时用于承接界面状态，真实帐号同步完成后会替换。",
            styleIndex: 0
        )
        return ChatDemoState(
            participants: [account],
            messages: [],
            currentUserID: account.id,
            selectedAccountID: account.id,
            contactCards: [account.id: contactCard],
            selectedFriendID: nil,
            rightToolItems: makeRightToolItems(messages: [])
        )
    }

    static var isPerformanceScenarioRequested: Bool {
#if DEBUG
        requestedPerformanceScenario != nil
#else
        false
#endif
    }

    static var shouldAutoRunPerformanceScenario: Bool {
#if DEBUG
        isPerformanceScenarioRequested
            && ProcessInfo.processInfo.arguments.contains("--performance-auto-run")
#else
        false
#endif
    }

#if DEBUG
    private enum PerformanceScenario: String {
        case aggregate
        case unread
        case conversation
    }

    private static var requestedPerformanceScenario: PerformanceScenario? {
        let arguments = ProcessInfo.processInfo.arguments
        if let inline = arguments.first(where: { $0.hasPrefix("--performance-scenario=") }) {
            return PerformanceScenario(rawValue: String(inline.dropFirst("--performance-scenario=".count)))
        }
        guard let flagIndex = arguments.firstIndex(of: "--performance-scenario"),
              arguments.indices.contains(flagIndex + 1) else {
            return nil
        }
        return PerformanceScenario(rawValue: arguments[flagIndex + 1])
    }

    private static func makePerformanceScenarioState(_ scenario: PerformanceScenario) -> ChatDemoState {
        let accountCount: Int
        let conversationCount: Int
        switch scenario {
        case .aggregate:
            accountCount = 10
            conversationCount = 2_000
        case .unread:
            accountCount = 3
            conversationCount = 200
        case .conversation:
            accountCount = 3
            conversationCount = 1
        }

        let accounts = (0..<accountCount).map { index in
            ChatParticipant(
                id: performanceUUID(namespace: 1, index: index),
                displayName: "性能帐号 \(index + 1)",
                tintColor: performanceColor(index),
                initials: "A\(index + 1)",
                isCurrentUser: true
            )
        }
        let conversations = (0..<conversationCount).map { index in
            let isGroup = scenario == .conversation || index.isMultiple(of: 5)
            return ChatParticipant(
                id: performanceUUID(namespace: 2, index: index),
                displayName: isGroup ? "压力群聊 \(index + 1)" : "压力好友 \(index + 1)",
                tintColor: performanceColor(index + accountCount),
                initials: isGroup ? "群" : "友",
                kind: isGroup ? .group : .contact
            )
        }
        let groupMembers = (0..<12).map { index in
            ChatParticipant(
                id: performanceUUID(namespace: 3, index: index),
                displayName: "群成员 \(index + 1)",
                tintColor: performanceColor(index + accountCount + conversationCount),
                initials: "M\(index + 1)",
                kind: .groupMember
            )
        }

        let messages: [ChatMessage]
        switch scenario {
        case .aggregate:
            messages = makeAggregatePerformanceMessages(
                count: 50_000,
                conversations: conversations,
                accounts: accounts,
                groupMembers: groupMembers
            )
        case .unread:
            messages = makeUnreadPerformanceMessages(
                conversations: conversations,
                accounts: accounts,
                groupMembers: groupMembers
            )
        case .conversation:
            messages = makeConversationPerformanceMessages(
                count: 2_000,
                conversation: conversations[0],
                accounts: accounts,
                groupMembers: groupMembers
            )
        }

        let contactCards = Dictionary(uniqueKeysWithValues: accounts.enumerated().map { index, account in
            (account.id, ContactCardProfile(
                accountID: account.id,
                displayName: account.displayName,
                role: "性能基准帐号",
                company: "只发 SCRM",
                wechatID: "perf_\(index + 1)",
                phone: "",
                bio: "Debug 压力场景",
                styleIndex: index % 4
            ))
        })
        return ChatDemoState(
            participants: accounts + conversations + groupMembers,
            messages: messages,
            currentUserID: accounts[0].id,
            selectedAccountID: accounts[0].id,
            contactCards: contactCards,
            selectedFriendID: scenario == .conversation ? conversations[0].id : nil,
            rightToolItems: makeRightToolItems(messages: messages)
        )
    }

    private static func makeAggregatePerformanceMessages(
        count: Int,
        conversations: [ChatParticipant],
        accounts: [ChatParticipant],
        groupMembers: [ChatParticipant]
    ) -> [ChatMessage] {
        let types: [ChatMessageType] = [.text, .emoji, .image, .stickerGif, .video, .file, .voice, .webLink]
        let baseDate = Date(timeIntervalSince1970: 1_750_000_000)
        return (0..<count).map { index in
            let conversationIndex = index % conversations.count
            let conversation = conversations[conversationIndex]
            let account = accounts[conversationIndex % accounts.count]
            let isGroup = conversationIndex.isMultiple(of: 5)
            let isOutgoing = index.isMultiple(of: 7)
            let sender = isOutgoing
                ? account
                : (isGroup ? groupMembers[index % groupMembers.count] : conversation)
            return performanceMessage(
                index: index,
                conversation: conversation,
                sender: sender,
                account: account,
                type: types[index % types.count],
                isOutgoing: isOutgoing,
                isGroup: isGroup,
                sentAt: baseDate.addingTimeInterval(TimeInterval(index))
            )
        }
    }

    private static func makeUnreadPerformanceMessages(
        conversations: [ChatParticipant],
        accounts: [ChatParticipant],
        groupMembers: [ChatParticipant]
    ) -> [ChatMessage] {
        let types: [ChatMessageType] = [.text, .emoji, .image, .voice, .file]
        let baseDate = Date(timeIntervalSince1970: 1_750_100_000)
        var result: [ChatMessage] = []
        result.reserveCapacity(conversations.count * 6)
        var messageIndex = 0
        for (conversationIndex, conversation) in conversations.enumerated() {
            let account = accounts[conversationIndex % accounts.count]
            let isGroup = conversationIndex.isMultiple(of: 4)
            result.append(performanceMessage(
                index: messageIndex,
                conversation: conversation,
                sender: account,
                account: account,
                type: .text,
                isOutgoing: true,
                isGroup: isGroup,
                sentAt: baseDate.addingTimeInterval(TimeInterval(messageIndex))
            ))
            messageIndex += 1
            for offset in 0..<5 {
                let sender = isGroup ? groupMembers[(conversationIndex + offset) % groupMembers.count] : conversation
                result.append(performanceMessage(
                    index: messageIndex,
                    conversation: conversation,
                    sender: sender,
                    account: accounts[(conversationIndex + offset) % accounts.count],
                    type: types[offset % types.count],
                    isOutgoing: false,
                    isGroup: isGroup,
                    sentAt: baseDate.addingTimeInterval(TimeInterval(messageIndex))
                ))
                messageIndex += 1
            }
        }
        return result
    }

    private static func makeConversationPerformanceMessages(
        count: Int,
        conversation: ChatParticipant,
        accounts: [ChatParticipant],
        groupMembers: [ChatParticipant]
    ) -> [ChatMessage] {
        let types: [ChatMessageType] = [
            .text, .image, .stickerGif, .video, .file, .voice, .capturedPhoto,
            .webLink, .quotedReply, .location, .channelsVideo, .favorite
        ]
        let baseDate = Date(timeIntervalSince1970: 1_750_200_000)
        return (0..<count).map { index in
            let isOutgoing = index.isMultiple(of: 11)
            let account = accounts[index % accounts.count]
            return performanceMessage(
                index: index,
                conversation: conversation,
                sender: isOutgoing ? account : groupMembers[index % groupMembers.count],
                account: account,
                type: types[index % types.count],
                isOutgoing: isOutgoing,
                isGroup: true,
                sentAt: baseDate.addingTimeInterval(TimeInterval(index))
            )
        }
    }

    private static func performanceMessage(
        index: Int,
        conversation: ChatParticipant,
        sender: ChatParticipant,
        account: ChatParticipant,
        type: ChatMessageType,
        isOutgoing: Bool,
        isGroup: Bool,
        sentAt: Date
    ) -> ChatMessage {
        ChatMessage(
            id: performanceUUID(namespace: 4, index: index),
            conversationID: conversation.id,
            type: type,
            sender: sender,
            body: "性能消息 \(index + 1)：\(bodyText(for: type))",
            detail: detailText(for: type),
            isOutgoing: isOutgoing,
            presentation: isGroup ? .avatarAndName : .bare,
            timestamp: "",
            sentAt: sentAt,
            isGroupConversation: isGroup,
            recipientAccountID: account.id
        )
    }

    private static func performanceUUID(namespace: UInt8, index: Int) -> UUID {
        let value = UInt64(index)
        return UUID(uuid: (
            0xF0, namespace, 0, 0, 0, 0, 0, 0,
            UInt8(truncatingIfNeeded: value >> 56),
            UInt8(truncatingIfNeeded: value >> 48),
            UInt8(truncatingIfNeeded: value >> 40),
            UInt8(truncatingIfNeeded: value >> 32),
            UInt8(truncatingIfNeeded: value >> 24),
            UInt8(truncatingIfNeeded: value >> 16),
            UInt8(truncatingIfNeeded: value >> 8),
            UInt8(truncatingIfNeeded: value)
        ))
    }

    private static func performanceColor(_ index: Int) -> UIColor {
        UIColor(
            hue: CGFloat((index * 37) % 360) / 360,
            saturation: 0.46,
            brightness: 0.72,
            alpha: 1
        )
    }
#endif

    static func makeDemoState() -> ChatDemoState {
        let participants = makeParticipants()
        let currentUserID = participants.first(where: \.isCurrentUser)?.id ?? participants[0].id
        let contactCards = makeContactCards(for: participants.filter(\.isCurrentUser))
        let messages = makeCompleteTestMessages(participants: participants, contactCards: contactCards)
        let rightToolItems = makeRightToolItems(messages: messages)
        return ChatDemoState(
            participants: participants,
            messages: messages,
            currentUserID: currentUserID,
            selectedAccountID: currentUserID,
            contactCards: contactCards,
            selectedFriendID: nil,
            rightToolItems: rightToolItems
        )
    }

    // Backend-shaped fixtures. Every relationship is explicit so these records
    // exercise the same IDs and ownership rules as synchronized IM data.
    static func makeUnreadTimelineTestData(
        accounts: [ChatParticipant]
    ) -> UnreadTimelineFixture {
        let accounts = accounts.sorted { $0.id.uuidString < $1.id.uuidString }
        guard let account = accounts.first else {
            return UnreadTimelineFixture(
                participants: [],
                messages: [],
                conversationKindsByID: [:],
                relatedAccountIDsByConversationID: [:],
                groupMemberIDsByGroupID: [:]
            )
        }
        let palette: [(UIColor, String)] = [
            (UIColor(red: 0.18, green: 0.52, blue: 0.66, alpha: 1), "短"),
            (UIColor(red: 0.72, green: 0.42, blue: 0.66, alpha: 1), "多"),
            (UIColor(red: 0.38, green: 0.66, blue: 0.42, alpha: 1), "长"),
            (UIColor(red: 0.86, green: 0.54, blue: 0.22, alpha: 1), "混"),
            (UIColor(red: 0.34, green: 0.48, blue: 0.78, alpha: 1), "一"),
            (UIColor(red: 0.76, green: 0.38, blue: 0.34, alpha: 1), "群"),
            (UIColor(red: 0.50, green: 0.48, blue: 0.72, alpha: 1), "卡"),
            (UIColor(red: 0.24, green: 0.62, blue: 0.58, alpha: 1), "高"),
            (UIColor(red: 0.30, green: 0.56, blue: 0.70, alpha: 1), "限")
        ]
        let names = [
            "测试好友·短消息",
            "测试好友·多条消息",
            "测试好友·长文本",
            "测试好友·混合类型",
            "测试群·单条消息",
            "测试群·连续消息",
            "测试群·卡片消息",
            "测试群·长消息",
            "测试群·关联三个帐号"
        ]
        let groupIndexes = Set(4...8)
        let participants = names.enumerated().map { index, name in
            ChatParticipant(
                id: stableUnreadTimelineParticipantID(index),
                displayName: name,
                tintColor: palette[index].0,
                initials: palette[index].1,
                kind: groupIndexes.contains(index) ? .group : .contact
            )
        }
        let groupMembers = [
            ChatParticipant(
                id: stableUnreadTimelineParticipantID(40),
                displayName: "群成员·小林",
                tintColor: UIColor(red: 0.34, green: 0.64, blue: 0.82, alpha: 1),
                initials: "林",
                kind: .groupMember
            ),
            ChatParticipant(
                id: stableUnreadTimelineParticipantID(41),
                displayName: "群成员·阿敏",
                tintColor: UIColor(red: 0.80, green: 0.46, blue: 0.54, alpha: 1),
                initials: "敏",
                kind: .groupMember
            ),
            ChatParticipant(
                id: stableUnreadTimelineParticipantID(42),
                displayName: "群成员·老周",
                tintColor: UIColor(red: 0.54, green: 0.60, blue: 0.30, alpha: 1),
                initials: "周",
                kind: .groupMember
            )
        ]

        var messages: [ChatMessage] = []
        var messageIndex = 0
        var timestamp = Date(timeIntervalSince1970: 1_784_786_400)

        func nextDate() -> Date {
            defer {
                timestamp = timestamp.addingTimeInterval(15)
                messageIndex += 1
            }
            return timestamp
        }

        func makeMessage(
            conversation: ChatParticipant,
            sender: ChatParticipant,
            type: ChatMessageType,
            body: String,
            detail: String = "",
            isOutgoing: Bool,
            recipientAccountID: UUID? = nil,
            richElements: [BubbleRichElement] = []
        ) -> ChatMessage {
            ChatMessage(
                id: stableUnreadTimelineMessageID(messageIndex),
                conversationID: conversation.id,
                type: type,
                sender: sender,
                body: body,
                detail: detail,
                isOutgoing: isOutgoing,
                presentation: conversation.kind == .group ? .avatarAndName : .bare,
                timestamp: "",
                sentAt: nextDate(),
                isGroupConversation: conversation.kind == .group,
                richElements: richElements,
                recipientAccountID: recipientAccountID,
                unansweredMetadata: conversation.kind == .group && !isOutgoing
                    ? UnansweredMessageMetadata(
                        itemID: "unread-fixture-item-\(messageIndex)",
                        requiresReply: true
                    )
                    : nil
            )
        }

        func appendConversation(
            _ conversation: ChatParticipant,
            incoming: [(ChatParticipant, ChatMessageType, String, String, [BubbleRichElement])],
            recipientAccounts: [ChatParticipant] = []
        ) {
            let resolvedRecipientAccounts = recipientAccounts.isEmpty ? [account] : recipientAccounts
            // This outgoing marker assigns the conversation to the selected
            // account; it is filtered out of the unread timeline.
            messages.append(makeMessage(
                conversation: conversation,
                sender: account,
                type: .text,
                body: "测试数据已回复基准",
                isOutgoing: true,
                recipientAccountID: account.id
            ))
            for (index, incomingMessage) in incoming.enumerated() {
                let (sender, type, body, detail, richElements) = incomingMessage
                messages.append(makeMessage(
                    conversation: conversation,
                    sender: sender,
                    type: type,
                    body: body,
                    detail: detail,
                    isOutgoing: false,
                    recipientAccountID: resolvedRecipientAccounts[index % resolvedRecipientAccounts.count].id,
                    richElements: richElements
                ))
            }
        }

        let friendShort = participants[0]
        let friendMany = participants[1]
        let friendLong = participants[2]
        let friendMixed = participants[3]
        let groupSingle = participants[4]
        let groupMany = participants[5]
        let groupCards = participants[6]
        let groupLong = participants[7]
        let groupPartialAccounts = participants[8]
        let memberA = groupMembers[0]
        let memberB = groupMembers[1]
        let memberC = groupMembers[2]

        appendConversation(friendShort, incoming: [
            (friendShort, .text, "只有一条未回消息", "短消息基准：头像应与这条气泡中心对齐。", [])
        ])
        appendConversation(friendMany, incoming: [
            (friendMany, .text, "第一条，短文本。", "", []),
            (friendMany, .emoji, "😊 👍 🙌", "Emoji 消息高度测试", []),
            (friendMany, .text, "第三条消息比第一条长一些，用来观察头像是否仍然固定在第一条消息的中心位置。", "", []),
            (friendMany, .text, "第四条消息继续向下排列，不应重新计算或移动头像。", "", [])
        ])
        appendConversation(friendLong, incoming: [
            (friendLong, .text, "这是一条很长的未回消息，用来测试首条消息高度明显超过头像高度时的居中关系。\n第二行继续描述现场情况。\n第三行用于观察消息框底部和后续分组间距。", "长文本高度测试", [])
        ])
        appendConversation(friendMixed, incoming: [
            (friendMixed, .voice, "18 秒语音", "语音消息高度测试", []),
            (friendMixed, .file, "项目需求文档.pdf", "文件消息高度测试", [.file(name: "项目需求文档.pdf", format: .pdf, url: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", preview: "布局测试文件", access: .public)]),
            (friendMixed, .webLink, "活动页面链接", "链接卡片高度测试", [.blueLink(label: "打开活动页面", url: "https://example.com/activity", bracketed: false)]),
            (friendMixed, .location, "集合点位置", "上海市黄浦区中山东一路 18 号", [.location(title: "集合点", address: "上海市黄浦区中山东一路 18 号", url: "https://maps.apple.com/?q=上海市黄浦区中山东一路18号")]),
            (friendMixed, .transfer, "转账 ¥88.00", "收款方：测试好友·混合类型\n状态：待收款", [])
        ])
        appendConversation(groupSingle, incoming: [
            (memberA, .text, "群里只有这一条未回消息。", "单条群聊测试", [])
        ], recipientAccounts: accounts)
        appendConversation(groupMany, incoming: [
            (memberA, .text, "群成员 A 的第一条消息。", "", []),
            (memberB, .text, "群成员 B 的第二条消息，名称显示也要保持在消息内部。", "", []),
            (memberC, .voice, "群成员 C 的语音", "群聊多条消息测试", []),
            (memberA, .quotedReply, "引用上一条消息继续说明。", "引用消息测试", [.quote(author: memberB.displayName, text: "群成员 B 的第二条消息")]),
            (memberB, .relay, "接龙：已确认到场名单", "1. 群成员 A\n2. 群成员 B\n3. 群成员 C", [])
        ], recipientAccounts: accounts)
        appendConversation(groupCards, incoming: [
            (memberC, .file, "群聊资料.pdf", "群文件卡片测试", [.file(name: "群聊资料.pdf", format: .pdf, url: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", preview: "群聊资料预览", access: .friends)]),
            (memberA, .location, "群集合点", "上海市静安区南京西路", [.location(title: "群集合点", address: "上海市静安区南京西路", url: "https://maps.apple.com/?q=上海市静安区南京西路")]),
            (memberB, .miniProgram, "签到小程序", "小程序卡片测试", [.inlineCard(kind: .miniProgramLink, title: "现场签到", subtitle: "测试小程序卡片", url: "https://example.com/check-in")]),
            (memberC, .text, "卡片消息结束，下一条应属于下一个群聊。", "", [])
        ], recipientAccounts: accounts)
        appendConversation(groupLong, incoming: [
            (memberA, .text, "这是最后一个群聊的长消息，用来观察多个群聊连续排列时，前一个群聊结束后下一个头像是否从下一行开始，并且仍与它自己的第一条消息中心对齐。\n第二行补充说明。\n第三行补充说明。", "群聊长消息测试", []),
            (memberB, .text, "长消息之后的第二条普通消息。", "", [])
        ], recipientAccounts: accounts)
        appendConversation(groupPartialAccounts, incoming: [
            (memberA, .text, "这个测试群关联右侧前三个主帐号。", "右侧其他帐号应降低透明度", []),
            (memberB, .text, "点击未关联帐号时保持当前群聊并显示提示。", "帐号关联状态测试", []),
            (memberC, .text, "群头像应显示为多个成员头像拼图。", "复合群头像测试", [])
        ], recipientAccounts: Array(accounts.prefix(3)))

        let conversationKindsByID = Dictionary(uniqueKeysWithValues: participants.map {
            ($0.id, $0.kind == .group ? OpenApiConversationKind.chatroom : .contact)
        })
        let relatedAccountIDsByConversationID = Dictionary(uniqueKeysWithValues: participants.map { participant in
            let relatedIDs = messages
                .filter { $0.conversationID == participant.id }
                .compactMap(\.recipientAccountID)
                .reduce(into: [UUID]()) { result, id in
                    guard !result.contains(id) else { return }
                    result.append(id)
                }
            return (participant.id, relatedIDs)
        })
        let groupMemberIDs = groupMembers.map(\.id)
        let groupMemberIDsByGroupID = Dictionary(uniqueKeysWithValues: participants.enumerated().compactMap { index, participant in
            groupIndexes.contains(index) ? (participant.id, groupMemberIDs) : nil
        })
        return UnreadTimelineFixture(
            participants: participants + groupMembers,
            messages: messages,
            conversationKindsByID: conversationKindsByID,
            relatedAccountIDsByConversationID: relatedAccountIDsByConversationID,
            groupMemberIDsByGroupID: groupMemberIDsByGroupID
        )
    }

    private static func stableUnreadTimelineParticipantID(_ index: Int) -> UUID {
        UUID(uuidString: String(format: "70000000-0000-0000-0000-%012x", index + 1)) ?? UUID()
    }

    static var unreadTimelinePartialAccountGroupID: UUID {
        stableUnreadTimelineParticipantID(8)
    }

    private static func stableUnreadTimelineMessageID(_ index: Int) -> UUID {
        UUID(uuidString: String(format: "71000000-0000-0000-0000-%012x", index + 1)) ?? UUID()
    }

    private static func makeParticipants() -> [ChatParticipant] {
        let currentUserData: [(String, UIColor, String)] = [
            ("\u{5f20}\u{4e09}", UIColor(red: 0.45, green: 0.78, blue: 0.10, alpha: 1), "\u{4e09}"),
            ("\u{52a9}\u{7406}\u{53f7}", UIColor(red: 0.12, green: 0.62, blue: 0.42, alpha: 1), "\u{52a9}"),
            ("\u{5ba2}\u{670d}\u{53f7}", UIColor(red: 0.20, green: 0.52, blue: 0.84, alpha: 1), "\u{5ba2}"),
            ("\u{5e97}\u{957f}\u{53f7}", UIColor(red: 0.84, green: 0.48, blue: 0.18, alpha: 1), "\u{5e97}")
        ]
        let friendData: [(String, UIColor, String)] = [
            ("Claude AI", UIColor(red: 1.0, green: 0.50, blue: 0.18, alpha: 1), "AI"),
            ("Codex", UIColor(red: 0.18, green: 0.38, blue: 0.86, alpha: 1), "CX"),
            ("\u{674e}\u{660e}", UIColor(red: 0.18, green: 0.44, blue: 0.54, alpha: 1), "\u{674e}"),
            ("\u{5c0f}\u{738b}", UIColor(red: 0.36, green: 0.66, blue: 0.86, alpha: 1), "\u{738b}"),
            ("\u{8bbe}\u{8ba1}\u{7ec4}", UIColor(red: 0.62, green: 0.50, blue: 0.75, alpha: 1), "\u{8bbe}"),
            ("\u{8fd0}\u{8425}\u{7fa4}", UIColor(red: 0.92, green: 0.62, blue: 0.34, alpha: 1), "\u{8fd0}"),
            ("\u{9648}\u{6d69}", UIColor(red: 0.24, green: 0.58, blue: 0.42, alpha: 1), "\u{9648}"),
            ("\u{5468}\u{654f}", UIColor(red: 0.77, green: 0.42, blue: 0.46, alpha: 1), "\u{5468}"),
            ("\u{8d75}\u{78ca}", UIColor(red: 0.32, green: 0.50, blue: 0.78, alpha: 1), "\u{8d75}"),
            ("\u{5b59}\u{7433}", UIColor(red: 0.50, green: 0.68, blue: 0.28, alpha: 1), "\u{5b59}"),
            ("\u{9ec4}\u{5b81}", UIColor(red: 0.86, green: 0.54, blue: 0.18, alpha: 1), "\u{9ec4}"),
            ("\u{5434}\u{8fea}", UIColor(red: 0.42, green: 0.46, blue: 0.72, alpha: 1), "\u{5434}"),
            ("\u{90d1}\u{6d01}", UIColor(red: 0.16, green: 0.60, blue: 0.66, alpha: 1), "\u{90d1}"),
            ("\u{94b1}\u{96e8}", UIColor(red: 0.74, green: 0.38, blue: 0.64, alpha: 1), "\u{94b1}"),
            ("\u{4ea7}\u{54c1}\u{7ec4}", UIColor(red: 0.20, green: 0.56, blue: 0.86, alpha: 1), "\u{4ea7}"),
            ("\u{6d4b}\u{8bd5}\u{7ec4}", UIColor(red: 0.55, green: 0.58, blue: 0.22, alpha: 1), "\u{6d4b}"),
            ("\u{5ba2}\u{670d}\u{7fa4}", UIColor(red: 0.86, green: 0.42, blue: 0.26, alpha: 1), "\u{5ba2}"),
            ("\u{5e02}\u{573a}\u{7fa4}", UIColor(red: 0.30, green: 0.62, blue: 0.52, alpha: 1), "\u{5e02}"),
            ("\u{8d22}\u{52a1}\u{7fa4}", UIColor(red: 0.70, green: 0.52, blue: 0.18, alpha: 1), "\u{8d22}"),
            ("\u{884c}\u{653f}\u{7fa4}", UIColor(red: 0.48, green: 0.54, blue: 0.64, alpha: 1), "\u{884c}"),
            ("\u{5218}\u{4e00}", UIColor(red: 0.22, green: 0.66, blue: 0.34, alpha: 1), "\u{5218}"),
            ("\u{9a6c}\u{5c0f}\u{4e50}", UIColor(red: 0.64, green: 0.40, blue: 0.78, alpha: 1), "\u{9a6c}")
        ]

        let currentUsers = currentUserData.enumerated().map { index, item in
            let (name, color, initials) = item
            return ChatParticipant(
                id: stableParticipantID(prefix: "10000000", index: index),
                displayName: name,
                tintColor: color,
                initials: initials,
                isCurrentUser: true
            )
        }

        let groupNames: Set<String> = ["设计组", "运营群", "产品组", "测试组", "客服群", "市场群", "财务群", "行政群"]
        let assistantNames: Set<String> = ["Claude AI", "Codex"]
        let friends = friendData.enumerated().map { index, item in
            let (name, color, initials) = item
            return ChatParticipant(
                id: stableParticipantID(prefix: "20000000", index: index),
                displayName: name,
                tintColor: color,
                initials: initials,
                isCurrentUser: false,
                kind: groupNames.contains(name) ? .group : (assistantNames.contains(name) ? .assistant : .contact)
            )
        }

        return currentUsers + friends
    }

    private static func stableParticipantID(prefix: String, index: Int) -> UUID {
        let suffix = String(format: "%012x", index + 1)
        return UUID(uuidString: "\(prefix)-0000-0000-0000-\(suffix)") ?? UUID()
    }

    private static func stableDemoMessageID(_ index: Int) -> UUID {
        UUID(uuidString: String(format: "30000000-0000-0000-0000-%012x", index + 1)) ?? UUID()
    }

    private static func makeContactCards(for accounts: [ChatParticipant]) -> [UUID: ContactCardProfile] {
        let roles = [
            "客户沟通负责人",
            "AI 助理与资料整理",
            "售后客服顾问",
            "门店运营负责人"
        ]
        let companies = [
            "Eason Studio",
            "智能工作台",
            "客户服务中心",
            "线下门店"
        ]
        var cards: [UUID: ContactCardProfile] = [:]
        for (index, account) in accounts.enumerated() {
            cards[account.id] = ContactCardProfile(
                accountID: account.id,
                displayName: account.displayName,
                role: roles[index % roles.count],
                company: companies[index % companies.count],
                wechatID: "wx_\(account.initials.lowercased())_\(index + 1)",
                phone: index == 0 ? "138 0000 0001" : "",
                bio: "用于向好友发送可查看、可保存的个人名片。",
                styleIndex: index % 4
            )
        }
        return cards
    }

    private static func makeCompleteTestMessages(
        participants: [ChatParticipant],
        contactCards: [UUID: ContactCardProfile]
    ) -> [ChatMessage] {
        let currentUsers = participants.filter(\.isCurrentUser)
        let primaryAccount = currentUsers.first ?? participants[0]
        let assistantAccount = currentUsers.dropFirst().first ?? primaryAccount
        let serviceAccount = currentUsers.dropFirst(2).first ?? assistantAccount
        let shopAccount = currentUsers.last ?? primaryAccount
        let friends = participants.filter { !$0.isCurrentUser }
        let liMing = friends.first { $0.displayName == "\u{674e}\u{660e}" } ?? friends[0]
        let xiaoWang = friends.first { $0.displayName == "\u{5c0f}\u{738b}" } ?? liMing
        let chenHao = friends.first { $0.displayName == "\u{9648}\u{6d69}" } ?? liMing
        let zhouMin = friends.first { $0.displayName == "\u{5468}\u{654f}" } ?? liMing
        let operationGroup = friends.first { $0.displayName == "\u{8fd0}\u{8425}\u{7fa4}" }
            ?? friends.first(where: isGroupParticipant)
            ?? liMing
        let designGroup = friends.first { $0.displayName == "\u{8bbe}\u{8ba1}\u{7ec4}" } ?? operationGroup
        let coverageGroup = friends.first { $0.displayName == "\u{6d4b}\u{8bd5}\u{7ec4}" } ?? designGroup
        let claude = friends.first { $0.displayName == "Claude AI" } ?? liMing
        let codex = friends.first { $0.displayName == "Codex" } ?? claude
        let calendar = Calendar(identifier: .gregorian)
        let todayBase = Date(timeIntervalSince1970: 1_784_780_000)
        let yesterdayBase = calendar.date(byAdding: .day, value: -1, to: todayBase) ?? todayBase.addingTimeInterval(-86_400)
        let earlierThisYearBase = calendar.date(byAdding: .day, value: -8, to: todayBase) ?? todayBase.addingTimeInterval(-691_200)
        let previousYearBase = calendar.date(byAdding: .year, value: -1, to: todayBase) ?? todayBase.addingTimeInterval(-31_536_000)
        var serial = 0

        func nextTimestamp() -> (UUID, String, Date) {
            let messageID = stableDemoMessageID(serial)
            let baseDate: Date
            switch serial {
            case 0..<24:
                baseDate = todayBase
            case 24..<32:
                baseDate = yesterdayBase
            case 32..<40:
                baseDate = earlierThisYearBase
            default:
                baseDate = previousYearBase
            }
            let date = baseDate.addingTimeInterval(TimeInterval(serial * 70))
            let text = ChatMessage.displayTimestamp(for: date)
            serial += 1
            return (messageID, text, date)
        }

        func presentation(for sender: ChatParticipant, conversation: ChatParticipant, type: ChatMessageType) -> BubblePresentation {
            if type == .system || type == .groupNotice {
                return .bare
            }
            if sender.isCurrentUser {
                return isGroupParticipant(conversation) ? .nameOnly : .avatarOnly
            }
            return isGroupParticipant(conversation) ? .avatarAndName : .bare
        }

        func message(
            _ type: ChatMessageType,
            conversation: ChatParticipant,
            sender: ChatParticipant,
            body: String,
            detail: String = "",
            isAI: Bool = false,
            richElements: [BubbleRichElement] = [],
            contactCard: ContactCardProfile? = nil,
            contactCardAccountID: UUID? = nil,
            quotedMessageID: UUID? = nil,
            mergedForwardMessages: [ChatMessage] = []
        ) -> ChatMessage {
            let (messageID, time, date) = nextTimestamp()
            return ChatMessage(
                id: messageID,
                conversationID: conversation.id,
                type: type,
                sender: sender,
                body: body,
                detail: detail,
                isOutgoing: sender.isCurrentUser,
                presentation: presentation(for: sender, conversation: conversation, type: type),
                timestamp: time,
                sentAt: date,
                isAI: isAI,
                isGroupConversation: isGroupParticipant(conversation),
                richElements: richElements,
                contactCard: contactCard,
                contactCardAccountID: contactCardAccountID,
                quotedMessageID: quotedMessageID,
                mergedForwardMessages: mergedForwardMessages,
                recipientAccountID: sender.isCurrentUser ? sender.id : primaryAccount.id
            )
        }

        var messages: [ChatMessage] = []

        let baseText = message(
            .text,
            conversation: liMing,
            sender: liMing,
            body: "李明，活动报名页在 https://www.baidu.com ，备用官网 https://cc2.cx ，两边都可以查看。",
            detail: ""
        )
        messages.append(baseText)
        messages.append(message(.emoji, conversation: liMing, sender: primaryAccount, body: "😊 👍 🙌", detail: "上午的现场安排确认了"))
        messages.append(message(.stickerGif, conversation: liMing, sender: liMing, body: "太好了，马上到", detail: "李明发送的动态贴纸"))
        messages.append(message(.image, conversation: liMing, sender: primaryAccount, body: "购物中心北门实拍", detail: "入口指示牌和集合点照片"))
        messages.append(message(.capturedPhoto, conversation: liMing, sender: liMing, body: "展台搭建进度", detail: "现场拍摄，灯箱已经装好"))
        messages.append(message(.voice, conversation: liMing, sender: primaryAccount, body: "12 秒语音", detail: "语音内容：我先去确认电源和签到桌位置"))
        messages.append(message(.video, conversation: liMing, sender: liMing, body: "展台巡场短视频", detail: "李明拍摄的 18 秒现场视频"))
        messages.append(message(.file, conversation: liMing, sender: primaryAccount, body: "周末活动执行方案.pdf", detail: "包含流程、物料、人员分工和风险预案"))
        messages.append(message(.location, conversation: liMing, sender: liMing, body: "上海来福士广场", detail: "上海市黄浦区西藏中路 268 号，可点击打开地图选点"))
        messages.append(message(.liveLocation, conversation: liMing, sender: primaryAccount, body: "实时位置共享中", detail: "张三、李明、小王正在共享到场路线"))

        let profile = contactCards[assistantAccount.id]
        messages.append(message(
            .contactCard,
            conversation: liMing,
            sender: assistantAccount,
            body: profile?.displayName ?? assistantAccount.displayName,
            detail: profile?.summary ?? "个人名片",
            contactCard: profile,
            contactCardAccountID: assistantAccount.id
        ))
        messages.append(message(.groupInvite, conversation: liMing, sender: liMing, body: "邀请加入「周末活动执行群」", detail: "群主：李明\n成员：张三、小王、陈浩、周敏\n用于同步现场执行和收款状态"))
        messages.append(message(.webLink, conversation: liMing, sender: primaryAccount, body: "这是自己的官网 https://cc2.cx", detail: "Eason Studio 官网，包含服务介绍、案例和联系方式"))
        messages.append(message(.article, conversation: liMing, sender: liMing, body: "公众号图文：周末城市活动攻略", detail: "城市活动观察\n本文整理了周末商场活动动线、停车入口、到场时间和互动区域。"))
        messages.append(message(.miniProgram, conversation: liMing, sender: primaryAccount, body: "小程序页面：门店导航", detail: "门店助手 · 来福士北门导航页\n可查看路线、楼层、联系人和停车信息"))
        messages.append(message(.channelsVideo, conversation: liMing, sender: liMing, body: "视频号：开业现场回放", detail: "00:18 竖屏视频，记录展台、人流和签到动线"))
        messages.append(message(.channelsLive, conversation: liMing, sender: primaryAccount, body: "视频号直播：20:00 开场", detail: "直播间：Eason Studio 现场\n预约人数 3286，开播后可进入互动"))
        messages.append(message(.music, conversation: liMing, sender: liMing, body: "音乐分享：路上听", detail: "城市电台\n适合布场前播放的轻快背景音乐"))
        messages.append(message(.favorite, conversation: liMing, sender: primaryAccount, body: "收藏：活动资料包", detail: "含官网链接、执行方案、现场照片和联系人名片"))
        let mergedForwardSeed = [
            baseText,
            message(.webLink, conversation: liMing, sender: primaryAccount, body: "官网入口 https://cc2.cx", detail: "客户确认可访问的公开链接"),
            message(.image, conversation: liMing, sender: liMing, body: "北门集合点照片", detail: "用于转发给兼职同学"),
            message(.video, conversation: liMing, sender: liMing, body: "展台巡场短视频", detail: "现场动线确认"),
            message(.file, conversation: liMing, sender: primaryAccount, body: "周末活动执行方案.pdf", detail: "流程、物料和负责人"),
            message(.music, conversation: liMing, sender: liMing, body: "音乐分享：路上听", detail: "城市电台")
        ]
        messages.append(message(
            .mergedForward,
            conversation: liMing,
            sender: liMing,
            body: "周末活动沟通记录",
            detail: "共 6 条：链接、图片、视频、文件、音乐",
            mergedForwardMessages: mergedForwardSeed
        ))
        messages.append(message(.redPacket, conversation: liMing, sender: primaryAccount, body: "恭喜发财，大吉大利", detail: "红包金额：¥66.00\n状态：等待领取"))
        messages.append(message(.transfer, conversation: liMing, sender: primaryAccount, body: "转账给李明 ¥88.00", detail: "收款方：李明\n金额：¥88.00\n状态：待收款\n备注：周末活动物料费"))
        messages.append(message(.coupon, conversation: liMing, sender: shopAccount, body: "微信卡券：饮品券", detail: "满 30 减 10\n有效期：2026.06.29-2026.07.06\n适用于来福士 B1 门店核销"))
        messages.append(message(.voiceCall, conversation: liMing, sender: liMing, body: "语音通话 03:21", detail: "点击可询问是否再次发起语音通话"))
        messages.append(message(.videoCall, conversation: liMing, sender: primaryAccount, body: "视频通话 01:48", detail: "点击进入完整视频通话页面，可结束通话和切换摄像头"))
        messages.append(message(
            .quotedReply,
            conversation: liMing,
            sender: primaryAccount,
            body: "回复：下午 2 点怎么安排？",
            detail: "引用消息发送后，点击引用内容可跳回原消息",
            quotedMessageID: baseText.id
        ))

        messages.append(message(.system, conversation: operationGroup, sender: primaryAccount, body: "李明加入了群聊", detail: ""))
        messages.append(message(.system, conversation: operationGroup, sender: primaryAccount, body: "小王退出了群聊", detail: ""))
        messages.append(message(.system, conversation: operationGroup, sender: primaryAccount, body: "陈浩领取了张三的红包", detail: ""))
        messages.append(message(.system, conversation: operationGroup, sender: primaryAccount, body: "周敏撤回了一条消息", detail: ""))
        messages.append(message(.groupNotice, conversation: operationGroup, sender: shopAccount, body: "群公告已更新", detail: "请所有成员 18:00 前确认到场时间、物料负责人和收款状态。点击可查看公告详情。"))
        messages.append(message(.text, conversation: operationGroup, sender: assistantAccount, body: "助理号：我来整理群公告和接龙名单，稍后同步给大家。", detail: "右侧帐号测试：助理号属于运营群"))
        messages.append(message(.file, conversation: operationGroup, sender: serviceAccount, body: "客服号：现场问题登记表.xlsx", detail: "右侧帐号测试：客服号也在运营群，可切换后继续留在当前群聊"))
        messages.append(message(.text, conversation: operationGroup, sender: liMing, body: "@小王 北门签到桌你来确认一下，照片发群里。", detail: ""))
        messages.append(message(.text, conversation: operationGroup, sender: xiaoWang, body: "@陈浩 物料车到了以后我同步实时位置。", detail: ""))
        messages.append(message(.text, conversation: operationGroup, sender: chenHao, body: "@周敏 我负责电源和展架，你看下群公告。", detail: ""))
        messages.append(message(.text, conversation: operationGroup, sender: zhouMin, body: "@李明 收到，我把付款名单也核一下。", detail: ""))
        messages.append(message(.relay, conversation: operationGroup, sender: xiaoWang, body: "接龙：周末同行名单", detail: "发起人ID：\(xiaoWang.id.uuidString)\n1. 李明 已确认\n2. 小王 已确认\n3. 陈浩 待确认"))
        messages.append(message(.splitBill, conversation: operationGroup, sender: primaryAccount, body: "群收款 4 人", detail: "群收款总额：¥320.00\n已收 1/4 人\n已支付：张三\n未支付：小王、陈浩、周敏"))

        messages.append(message(.text, conversation: designGroup, sender: liMing, body: "@陈浩 海报竖图按 7.5 行高度预览，链接水印保留。", detail: ""))
        messages.append(message(.text, conversation: designGroup, sender: xiaoWang, body: "@周敏 小程序名片和视频号名片我都放进卡片合集。", detail: ""))
        messages.append(message(.text, conversation: designGroup, sender: primaryAccount, body: "张三：设计组这边我负责最终确认，图片公开范围先按仅好友。", detail: "右侧帐号测试：张三属于设计组"))
        messages.append(message(.image, conversation: designGroup, sender: shopAccount, body: "店长号：门店陈列参考图", detail: "右侧帐号测试：店长号也在设计组，切换时保持当前群聊"))
        messages.append(message(
            .text,
            conversation: designGroup,
            sender: claude,
            body: "AI 结构化文本消息",
            detail: "",
            isAI: true,
            richElements: [
                .text("请看 "),
                .blueLink(label: "拍点链接", url: "https://cc2.cx", bracketed: true),
                .text(" ，资料给 "),
                .mention("设计组"),
                .text(" 跟进。"),
                .taggedFile(label: "门店说明.md", url: "https://cc2.cx", prefix: "#"),
                .text(" "),
                .blueLink(label: "官网", url: "https://cc2.cx", bracketed: false),
                .text(" "),
                .aiToken("ai")
            ]
        ))
        messages.append(message(
            .quotedReply,
            conversation: designGroup,
            sender: chenHao,
            body: "引用 + 图文混排",
            detail: "",
            richElements: [
                .quote(author: "\u{674e}\u{660e}", text: "下午 2 点前确认公开范围，未通过申请的用户只看模糊图。"),
                .text("横图用于群内预览，链接可给 AI 直接访问："),
                .image(name: "门店横图", url: "https://picsum.photos/1200/800", aspect: .horizontal, access: .friends),
                .text("竖图用于截图传播，图上保留水印链接："),
                .image(name: "活动海报竖图", url: "https://picsum.photos/800/1200", aspect: .vertical, access: .recipients)
            ]
        ))
        messages.append(message(
            .contactCard,
            conversation: designGroup,
            sender: claude,
            body: "名片合集",
            detail: "",
            isAI: true,
            richElements: [
                .inlineCard(kind: .enterprise, title: "Eason · 企微", subtitle: "客户成功负责人", url: "https://cc2.cx"),
                .inlineCard(kind: .personal, title: "\u{5c0f}\u{738b}", subtitle: "个人名片", url: "https://cc2.cx"),
                .inlineCard(kind: .officialAccount, title: "城市活动指南", subtitle: "公众号名片", url: "https://cc2.cx"),
                .inlineCard(kind: .miniProgramProfile, title: "门店助手", subtitle: "小程序名片", url: "https://cc2.cx"),
                .inlineCard(kind: .channelsProfile, title: "开业现场", subtitle: "视频号名片", url: "https://cc2.cx")
            ]
        ))
        messages.append(message(
            .miniProgram,
            conversation: designGroup,
            sender: zhouMin,
            body: "位置 + 小程序链接 + 文件预览",
            detail: "",
            richElements: [
                .location(title: "购物中心北门", address: "上海市黄浦区中山东一路 18 号", url: "https://maps.apple.com/?q=上海市黄浦区中山东一路18号"),
                .inlineCard(kind: .miniProgramLink, title: "抖音门店导航页", subtitle: "点击跳转抖音小程序页面", url: "https://douyin.com/mini/store-nav"),
                .file(name: "现场记录.txt", format: .txt, url: "https://cc2.cx", preview: "北门集合，物料 18:00 前到场。", access: .recipients),
                .file(name: "项目需求文档.pdf", format: .pdf, url: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", preview: "目标：补齐 AI 可读链接、截图可访问、App 内授权查看高清文件。", access: .fans),
                .file(name: "接口草案.md", format: .md, url: "https://cc2.cx", preview: "richElements: text/link/mention/card/location/image/file/quote", access: .public),
                .file(name: "合同模板.docx", format: .word, url: "https://cc2.cx", preview: "发布者可设置公开、好友粉丝、好友、收件者可见。", access: .friends),
                .file(name: "现场问题登记表.xlsx", format: .excel, url: "https://cc2.cx", preview: "问题编号、负责人、状态、处理截止时间。", access: .friends),
                .file(name: "活动复盘演示.pptx", format: .powerpoint, url: "https://cc2.cx", preview: "活动目标、现场照片、转化数据和下一步计划。", access: .public)
            ]
        ))
        messages.append(message(
            .text,
            conversation: codex,
            sender: codex,
            body: "Codex 已连接，可以在右下角 AI 面板里切换模型并流式生成草稿。",
            detail: "",
            isAI: true
        ))
        messages.append(message(
            .text,
            conversation: claude,
            sender: claude,
            body: "Claude AI 直接会话，用来测试左侧 AI 置顶、无头像气泡和虚线边框之外的普通 AI 展示。",
            detail: "",
            isAI: true,
            richElements: [
                .text("Claude 可以读取 "),
                .blueLink(label: "官网", url: "https://cc2.cx", bracketed: false),
                .text(" ，也能识别 "),
                .aiToken("ai")
            ]
        ))

        let existingConversationIDs = Set(messages.map(\.conversationID))
        let uncoveredFriends = friends.filter { !existingConversationIDs.contains($0.id) }.prefix(20)
        let sampleTypes: [ChatMessageType] = [
            .text, .image, .voice, .video, .file, .location, .webLink, .miniProgram,
            .redPacket, .transfer, .voiceCall, .videoCall, .favorite, .music,
            .article, .coupon, .mergedForward, .channelsVideo, .channelsLive, .liveLocation
        ]
        for (index, friend) in uncoveredFriends.enumerated() {
            let sampleType = sampleTypes[index % sampleTypes.count]
            let sender = index.isMultiple(of: 2) ? primaryAccount : friend
            messages.append(message(
                sampleType,
                conversation: friend,
                sender: sender,
                body: sampleBody(for: sampleType, friendName: friend.displayName, index: index),
                detail: sampleDetail(for: sampleType, friendName: friend.displayName, index: index)
            ))
            messages.append(message(
                .text,
                conversation: friend,
                sender: index.isMultiple(of: 2) ? friend : assistantAccount,
                body: isGroupParticipant(friend)
                    ? "\(friend.displayName) 已同步今天的负责人和到场时间，群内成员消息会按名字展示。"
                    : "\(friend.displayName) 已收到资料，晚些会回确认结果。",
                detail: ""
            ))
        }

        let coverageSenders = [
            primaryAccount,
            assistantAccount,
            serviceAccount,
            shopAccount,
            liMing,
            xiaoWang,
            chenHao,
            zhouMin,
            claude,
            codex
        ]
        let coverageMergedSeed = Array(messages.prefix(6))
        for (index, type) in ChatMessageType.allCases.enumerated() {
            let sender = type == .system
                ? primaryAccount
                : coverageSenders[index % coverageSenders.count]
            messages.append(message(
                type,
                conversation: coverageGroup,
                sender: sender,
                body: coverageBody(for: type, index: index),
                detail: coverageDetail(for: type, index: index),
                isAI: sender.isAIAccount,
                richElements: coverageRichElements(for: type, index: index),
                contactCard: type == .contactCard ? contactCards[serviceAccount.id] : nil,
                contactCardAccountID: type == .contactCard ? serviceAccount.id : nil,
                quotedMessageID: type == .quotedReply ? baseText.id : nil,
                mergedForwardMessages: type == .mergedForward ? coverageMergedSeed : []
            ))
        }

        return messages.compactMap(sanitizedNonMediaSeedMessage)
    }

    private static let omittedSeedMediaTypes: Set<ChatMessageType> = [
        .stickerGif,
        .image,
        .capturedPhoto,
        .video,
        .channelsVideo,
        .groupNotice,
        .system
    ]

    private static func sanitizedNonMediaSeedMessage(_ message: ChatMessage) -> ChatMessage? {
        guard !omittedSeedMediaTypes.contains(message.type) else { return nil }
        let filteredRichElements = message.richElements.filter { element in
            if case .image = element {
                return false
            }
            return true
        }
        let filteredMergedMessages = message.mergedForwardMessages.compactMap(sanitizedNonMediaSeedMessage)
        let detail: String
        if message.type == .mergedForward, !message.mergedForwardMessages.isEmpty {
            detail = "共 \(filteredMergedMessages.count) 条：链接、文件、音乐等"
        } else {
            detail = message.detail
        }
        return ChatMessage(
            id: message.id,
            conversationID: message.conversationID,
            type: message.type,
            sender: message.sender,
            body: message.body,
            detail: detail,
            isOutgoing: message.isOutgoing,
            presentation: message.presentation,
            timestamp: message.timestamp,
            sentAt: message.sentAt,
            attachmentURL: message.attachmentURL,
            isAI: message.isAI,
            isGroupConversation: message.isGroupConversation,
            richElements: filteredRichElements,
            contactCard: message.contactCard,
            contactCardAccountID: message.contactCardAccountID,
            quotedMessageID: message.quotedMessageID,
            mergedForwardMessages: filteredMergedMessages,
            recipientAccountID: message.recipientAccountID,
            unansweredMetadata: message.unansweredMetadata
        )
    }

    private static func coverageBody(for type: ChatMessageType, index: Int) -> String {
        switch type {
        case .text:
            return "全类型测试文本：访问 https://cc2.cx ，@李明 查看 #测试文档.pdf，✦ai 已标记。"
        case .emoji:
            return "😊 👍 🎉"
        case .stickerGif:
            return "动态 GIF 贴纸：鼓掌庆祝"
        case .image:
            return "公开横图预览：测试组门店外立面"
        case .capturedPhoto:
            return "拍摄照片：现场签到台"
        case .voice:
            return "18 秒语音"
        case .video:
            return "短视频：测试组现场巡检"
        case .file:
            return "全类型测试说明.pdf"
        case .location:
            return "测试组集合点"
        case .liveLocation:
            return "测试组实时位置共享中"
        case .contactCard:
            return "推名片：客服号"
        case .groupInvite:
            return "邀请加入「全类型测试群」"
        case .webLink:
            return "官网链接 https://cc2.cx"
        case .article:
            return "公众号文章：全类型消息验收清单"
        case .miniProgram:
            return "小程序卡片：测试页面"
        case .channelsVideo:
            return "视频号视频：全类型演示"
        case .channelsLive:
            return "视频号直播：测试直播间"
        case .music:
            return "音乐分享：测试歌单"
        case .favorite:
            return "收藏分享：测试资料夹"
        case .mergedForward:
            return "聊天记录合并转发：全类型样例"
        case .redPacket:
            return "全类型测试红包"
        case .transfer:
            return "转账给李明 ¥128.00"
        case .splitBill:
            return "AA 收款 5 人"
        case .coupon:
            return "微信卡券：全类型测试券"
        case .voiceCall:
            return "语音通话 04:12"
        case .videoCall:
            return "视频通话 02:36"
        case .quotedReply:
            return "引用回复：点击引用内容跳回原消息"
        case .relay:
            return "接龙：全类型测试签到"
        case .groupNotice:
            return "群公告：全类型测试说明已更新"
        case .system:
            return "系统提示：张三邀请客服号加入了测试组"
        }
    }

    private static func coverageDetail(for type: ChatMessageType, index: Int) -> String {
        switch type {
        case .image:
            return "https://picsum.photos/1200/800\n可见范围：公开\n点击可全屏预览、识图、找物、查看高清图"
        case .capturedPhoto:
            return "https://picsum.photos/800/1200\n可见范围：仅收件者\n未授权时显示模糊，可申请查看"
        case .video:
            return "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4\n支持播放、暂停、倍速、前进和后退"
        case .file:
            return "PDF · https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf\n预览：覆盖全部消息类型、链接、权限、预览和转发交互。"
        case .location:
            return "上海市黄浦区西藏中路 268 号\n点击可打开地图选择和搜索位置"
        case .liveLocation:
            return "张三、助理号、李明正在共享位置\n可查看实时位置页面"
        case .contactCard:
            return "客服号 · 客户服务中心\n点击消息查看详细个人名片"
        case .groupInvite:
            return "群主：张三\n成员：张三、助理号、客服号、李明、小王\n点击可接受加入群聊"
        case .webLink:
            return "Eason Studio 官网 · 点击蓝色链接会提示跳转到对应 URL"
        case .article:
            return "城市活动观察\n包含消息类型验收、截图保护、文件授权和 AI 读取链接。"
        case .miniProgram:
            return "门店助手 · 测试页面\n点击跳转对应小程序/外部 App"
        case .channelsVideo:
            return "00:26 竖屏视频，打开后进入视频号 UI 并自动播放"
        case .channelsLive:
            return "直播间：全类型测试\n预约人数 1286，点击进入直播卡片"
        case .music:
            return "测试歌单\n点击后可播放音乐"
        case .favorite:
            return "收藏类别：图片与视频、文件、链接、文本、聊天记录\n点击可查看收藏详情"
        case .mergedForward:
            return "共 6 条：文本、链接、图片、视频、文件、音乐\n点击进入聊天记录详情页"
        case .redPacket:
            return "红包金额：¥88.00\n个数：3/5\n状态：等待群成员领取"
        case .transfer:
            return "收款方：李明\n金额：¥128.00\n状态：待收款\n备注：全类型测试转账"
        case .splitBill:
            return "群收款总额：¥500.00\n已收 2/5 人\n已支付：张三、客服号\n未支付：李明、小王、陈浩"
        case .coupon:
            return "满 50 减 20\n有效期：2026.07.01-2026.07.31\n适用于测试门店核销"
        case .voiceCall:
            return "通话对象：测试组\n点击可进入语音通话页面"
        case .videoCall:
            return "通话对象：测试组\n点击可进入视频通话页面"
        case .quotedReply:
            return "引用消息发送后，点击引用内容可跳回原消息。"
        case .relay:
            return "发起人ID：coverage-relay\n1. 张三 已确认\n2. 李明 已确认\n3. 小王 待确认"
        case .groupNotice:
            return "本群用于覆盖文本、媒体、文件、位置、卡片、支付、通话、接龙、通知等全部消息类型。"
        case .system:
            return ""
        default:
            return "全类型测试：\(type.title)，可在测试组中查看渲染、点击、长按和转发效果。"
        }
    }

    private static func coverageRichElements(for type: ChatMessageType, index: Int) -> [BubbleRichElement] {
        switch type {
        case .text:
            return [
                .text("打开 "),
                .blueLink(label: "官网", url: "https://cc2.cx", bracketed: false),
                .text("，联系 "),
                .mention("李明"),
                .text("，查看 "),
                .taggedFile(label: "测试文档.pdf", url: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", prefix: "#"),
                .text("，"),
                .aiToken("ai"),
                .text(" 已介入。")
            ]
        case .quotedReply:
            return [
                .quote(author: "李明", text: "这条是被引用的原消息，点击引用可以跳转定位。"),
                .text("引用后的回复文本，包含链接 "),
                .blueLink(label: "https://cc2.cx", url: "https://cc2.cx", bracketed: false)
            ]
        case .miniProgram:
            return [
                .inlineCard(kind: .miniProgramLink, title: "门店助手测试页", subtitle: "点击跳转对应小程序", url: "https://weixin.qq.com/mini/coverage"),
                .location(title: "测试门店", address: "上海市黄浦区西藏中路 268 号", url: "https://maps.apple.com/?q=上海市黄浦区西藏中路268号")
            ]
        case .contactCard:
            return [
                .inlineCard(kind: .enterprise, title: "客服号 · 企微", subtitle: "客户服务中心", url: "https://cc2.cx"),
                .inlineCard(kind: .personal, title: "客服号", subtitle: "个人名片", url: "https://cc2.cx"),
                .inlineCard(kind: .officialAccount, title: "全类型测试公众号", subtitle: "公众号名片", url: "https://cc2.cx"),
                .inlineCard(kind: .channelsProfile, title: "测试视频号", subtitle: "视频号名片", url: "https://cc2.cx")
            ]
        case .file:
            return [
                .file(name: "全类型测试说明.pdf", format: .pdf, url: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", preview: "覆盖全部消息类型、权限、预览和 AI 可访问链接。", access: .public),
                .file(name: "接口字段说明.md", format: .md, url: "https://cc2.cx", preview: "message.type、richElements、accessScope、previewURL", access: .friends),
                .file(name: "销售日报.csv", format: .csv, url: "https://cc2.cx", preview: "日期、门店、客流、成交、客单价。", access: .public)
            ]
        case .image:
            return [
                .image(name: "全类型横图", url: "https://picsum.photos/1200/800", aspect: .horizontal, access: .public)
            ]
        case .capturedPhoto:
            return [
                .image(name: "拍摄竖图", url: "https://picsum.photos/800/1200", aspect: .vertical, access: .recipients)
            ]
        default:
            return []
        }
    }

    private static func sampleBody(for type: ChatMessageType, friendName: String, index: Int) -> String {
        switch type {
        case .text:
            return "资料我放在 https://cc2.cx ，也可以 @李明 确认活动入口。"
        case .image:
            return "\(friendName) 发来的现场照片"
        case .voice:
            return "\(10 + index) 秒语音"
        case .video:
            return "\(15 + index) 秒现场短视频"
        case .file:
            return "\(friendName)-活动确认单.pdf"
        case .location:
            return "\(friendName) 的集合位置"
        case .webLink:
            return "官网链接 https://cc2.cx"
        case .miniProgram:
            return "小程序：门店签到"
        case .redPacket:
            return "辛苦了，现场补贴"
        case .transfer:
            return "转账给\(friendName) ¥\(88 + index).00"
        case .voiceCall:
            return "语音通话 02:\(String(format: "%02d", index + 10))"
        case .videoCall:
            return "视频通话 01:\(String(format: "%02d", index + 12))"
        case .favorite:
            return "收藏：活动资料包"
        case .music:
            return "音乐分享：开场歌单"
        case .article:
            return "公众号图文：城市活动攻略"
        case .coupon:
            return "微信卡券：咖啡兑换券"
        case .mergedForward:
            return "活动沟通记录 \(index + 3) 条"
        case .channelsVideo:
            return "视频号视频：现场回放"
        case .channelsLive:
            return "视频号直播：今晚开场"
        case .liveLocation:
            return "实时位置共享中"
        default:
            return type.title
        }
    }

    private static func sampleDetail(for type: ChatMessageType, friendName: String, index: Int) -> String {
        switch type {
        case .redPacket:
            return "红包金额：¥\(20 + index).00\n状态：等待领取"
        case .transfer:
            return "收款方：\(friendName)\n金额：¥\(88 + index).00\n状态：待收款\n备注：活动物料垫付"
        case .file:
            return "活动确认单，包含到场时间、负责区域和联系方式"
        case .location, .liveLocation:
            return "上海市黄浦区西藏中路 \(260 + index) 号"
        case .webLink:
            return "Eason Studio 官网，点击后可确认跳转浏览器"
        case .miniProgram:
            return "门店签到小程序，可查看路线和签到状态"
        case .mergedForward:
            return "包含链接、图片、文件等活动上下文"
        default:
            return "来自\(friendName)的\(type.title)消息，可点击查看对应内容"
        }
    }

    private static func isGroupParticipant(_ participant: ChatParticipant) -> Bool {
        participant.kind == .group
    }

    private static func makeRightToolItems(messages: [ChatMessage]) -> [SidebarItem] {
        let toolTypes: [ChatMessageType] = [
            .text,
            .capturedPhoto,
            .image,
            .video,
            .channelsVideo,
            .channelsLive,
            .location,
            .liveLocation,
            .contactCard,
            .groupInvite,
            .webLink,
            .article,
            .miniProgram,
            .music,
            .favorite,
            .mergedForward,
            .relay,
            .file,
            .voice,
            .voiceCall,
            .videoCall,
            .redPacket,
            .transfer,
            .splitBill,
            .coupon
        ]
        return toolTypes.compactMap { type in
            let targetMessageID = (messages.first(where: { $0.type == type }) ?? messages.first)?.id
                ?? OpenApiStableID.uuid(namespace: "demo-tool-target", key: "\(type.rawValue)")
            return SidebarItem(
                id: OpenApiStableID.uuid(namespace: "demo-tool", key: "\(type.rawValue)"),
                kind: .tool,
                title: type == .text ? "\u{5feb}\u{6377}\u{8bed}" : (type == .contactCard ? "\u{63a8}\u{540d}\u{7247}" : (type == .mergedForward ? "\u{591a}\u{9009}" : type.title)),
                symbolName: type.symbolName,
                tintColor: type.accentColor,
                participantID: nil,
                targetMessageID: targetMessageID,
                isActive: false,
                toolMessageType: type
            )
        }
    }

    private static func bodyText(for type: ChatMessageType) -> String {
        switch type {
        case .text: return "\u{4f60}\u{597d}\u{ff01}\u{6700}\u{8fd1}\u{600e}\u{4e48}\u{6837}\u{ff1f}"
        case .emoji: return "\u{5fae}\u{7b11}\u{3001}\u{63e1}\u{624b}\u{548c}\u{70b9}\u{8d5e}\u{8868}\u{60c5}"
        case .stickerGif: return "\u{6536}\u{5230}\u{4e00}\u{5f20}\u{52a8}\u{6001}\u{8d34}\u{7eb8}"
        case .image: return "\u{8d2d}\u{7269}\u{4e2d}\u{5fc3}\u{95e8}\u{53e3}\u{7167}\u{7247}"
        case .capturedPhoto: return "\u{73b0}\u{573a}\u{62cd}\u{6444}\u{7684}\u{5408}\u{5f71}"
        case .voice: return "12 \u{79d2}\u{8bed}\u{97f3}"
        case .video: return "18 \u{79d2}\u{77ed}\u{89c6}\u{9891}"
        case .file: return "\u{9879}\u{76ee}\u{9700}\u{6c42}\u{6587}\u{6863}.pdf"
        case .location: return "\u{8d2d}\u{7269}\u{4e2d}\u{5fc3}\u{5317}\u{95e8}"
        case .liveLocation: return "\u{5b9e}\u{65f6}\u{4f4d}\u{7f6e}\u{5171}\u{4eab}\u{4e2d}"
        case .contactCard: return "\u{5c0f}\u{738b}\u{7684}\u{4e2a}\u{4eba}\u{540d}\u{7247}"
        case .groupInvite: return "\u{9080}\u{8bf7}\u{52a0}\u{5165}\u{5468}\u{672b}\u{6d3b}\u{52a8}\u{7fa4}"
        case .webLink: return "\u{65b0}\u{5f00}\u{8d2d}\u{7269}\u{4e2d}\u{5fc3}\u{653b}\u{7565}"
        case .article: return "\u{516c}\u{4f17}\u{53f7}\u{56fe}\u{6587}\u{ff1a}\u{5468}\u{672b}\u{57ce}\u{5e02}\u{6d3b}\u{52a8}"
        case .miniProgram: return "\u{5c0f}\u{7a0b}\u{5e8f}\u{9875}\u{9762}\u{ff1a}\u{95e8}\u{5e97}\u{5bfc}\u{822a}"
        case .channelsVideo: return "\u{89c6}\u{9891}\u{53f7}\u{ff1a}\u{5f00}\u{4e1a}\u{73b0}\u{573a}"
        case .channelsLive: return "\u{89c6}\u{9891}\u{53f7}\u{76f4}\u{64ad}\u{9884}\u{7ea6}"
        case .music: return "\u{97f3}\u{4e50}\u{5206}\u{4eab}\u{ff1a}\u{8def}\u{4e0a}\u{542c}"
        case .favorite: return "\u{6536}\u{85cf}\u{5185}\u{5bb9}\u{5206}\u{4eab}"
        case .mergedForward: return "\u{804a}\u{5929}\u{8bb0}\u{5f55} 6 \u{6761}"
        case .redPacket: return "\u{606d}\u{559c}\u{53d1}\u{8d22}\u{ff0c}\u{5927}\u{5409}\u{5927}\u{5229}"
        case .transfer: return "\u{8f6c}\u{8d26}\u{7ed9}\u{674e}\u{660e} \u{00a5}88.00"
        case .splitBill: return "\u{7fa4}\u{6536}\u{6b3e} 4 \u{4eba}"
        case .coupon: return "\u{5fae}\u{4fe1}\u{5361}\u{5238}\u{ff1a}\u{996e}\u{54c1}\u{5238}"
        case .voiceCall: return "\u{8bed}\u{97f3}\u{901a}\u{8bdd} 03:21"
        case .videoCall: return "\u{89c6}\u{9891}\u{901a}\u{8bdd} 01:48"
        case .quotedReply: return "\u{56de}\u{590d}\u{ff1a}\u{4e0b}\u{5348} 2 \u{70b9}\u{600e}\u{4e48}\u{6837}\u{ff1f}"
        case .relay: return "\u{63a5}\u{9f99}\u{ff1a}\u{5468}\u{672b}\u{540c}\u{884c}\u{540d}\u{5355}"
        case .groupNotice: return "\u{7fa4}\u{516c}\u{544a}\u{5df2}\u{66f4}\u{65b0}"
        case .system: return "\u{4f60}\u{62cd}\u{4e86}\u{62cd}\u{674e}\u{660e}"
        }
    }

    private static func detailText(for type: ChatMessageType) -> String {
        switch type {
        case .text, .emoji, .system:
            return ""
        case .voice:
            return "\u{70b9}\u{51fb}\u{64ad}\u{653e}\u{8bed}\u{97f3}\u{ff0c}\u{652f}\u{6301}\u{8fdb}\u{5ea6}\u{6761}\u{548c}\u{542c}\u{7b52}\u{6a21}\u{5f0f}"
        case .image, .capturedPhoto:
            return "\u{56fe}\u{7247}\u{9884}\u{89c8}\u{6bd4}\u{4f8b} 4:3\u{ff0c}\u{4fdd}\u{7559}\u{5706}\u{89d2}\u{548c}\u{8f7b}\u{8fb9}\u{6846}"
        case .location, .liveLocation:
            return "\u{5730}\u{56fe}\u{5361}\u{7247}\u{663e}\u{793a}\u{540d}\u{79f0}\u{3001}\u{5730}\u{5740}\u{548c}\u{72b6}\u{6001}"
        case .redPacket:
            return "\u{7ea2}\u{5305}\u{91d1}\u{989d}\u{ff1a}\u{00a5}66.00\n\u{72b6}\u{6001}\u{ff1a}\u{5df2}\u{88ab}\u{9648}\u{6d69}\u{9886}\u{53d6}"
        case .transfer:
            return "\u{6536}\u{6b3e}\u{65b9}\u{ff1a}\u{674e}\u{660e}\n\u{72b6}\u{6001}\u{ff1a}\u{5df2}\u{6536}\u{6b3e}\n\u{5907}\u{6ce8}\u{ff1a}\u{5468}\u{672b}\u{6d3b}\u{52a8}\u{7269}\u{6599}\u{8d39}"
        case .splitBill:
            return "\u{7fa4}\u{6536}\u{6b3e}\u{603b}\u{989d}\u{ff1a}\u{00a5}320.00\n\u{5df2}\u{6536} 3/4 \u{4eba}\u{ff0c}\u{8fd8}\u{5dee}\u{5c0f}\u{738b}"
        default:
            return "\u{5361}\u{7247}\u{6d88}\u{606f}\u{5c55}\u{793a}\u{6807}\u{9898}\u{3001}\u{6458}\u{8981}\u{3001}\u{6765}\u{6e90}\u{548c}\u{64cd}\u{4f5c}\u{5165}\u{53e3}"
        }
    }
}
