import UIKit
@testable import ios_float

enum PerformanceDataFixtures {
    struct Dataset {
        let accounts: [ChatParticipant]
        let conversations: [ChatParticipant]
        let groupMembers: [ChatParticipant]
        let messages: [ChatMessage]
    }

    struct ConversationAccountKey: Hashable {
        let conversationID: UUID
        let accountID: UUID
    }

    struct MessageLookupIndex {
        let messageOffsetByID: [UUID: Int]
        let messageOffsetsByConversation: [UUID: [Int]]
        let latestMessageOffsetByConversation: [UUID: Int]
    }

    private static let referenceDate = Date(timeIntervalSince1970: 1_800_000_000)
    private static let colors: [UIColor] = [
        .systemBlue,
        .systemGreen,
        .systemOrange,
        .systemPink,
        .systemTeal,
        .systemIndigo,
        .systemRed,
        .systemCyan,
        .systemPurple,
        .systemYellow
    ]

    static func makeLargeCorpus() -> Dataset {
        let accounts = makeAccounts(count: 10)
        let conversations = makeConversations(count: 2_000)
        let groupMembers = makeGroupMembers(count: 40)
        var messages: [ChatMessage] = []
        messages.reserveCapacity(50_000)

        for conversationIndex in conversations.indices {
            let conversation = conversations[conversationIndex]
            let isGroup = isGroupConversation(conversationIndex)
            let conversationAccount = accounts[conversationIndex % accounts.count]

            for localIndex in 0..<25 {
                let messageIndex = conversationIndex * 25 + localIndex
                let isOutgoing = localIndex.isMultiple(of: 7)
                let sender: ChatParticipant
                if isOutgoing {
                    sender = conversationAccount
                } else if isGroup {
                    sender = groupMembers[(conversationIndex + localIndex) % groupMembers.count]
                } else {
                    sender = conversation
                }
                messages.append(makeMessage(
                    index: messageIndex,
                    conversation: conversation,
                    sender: sender,
                    accountID: conversationAccount.id,
                    isOutgoing: isOutgoing,
                    isGroup: isGroup,
                    type: ChatMessageType.allCases[messageIndex % ChatMessageType.allCases.count]
                ))
            }
        }

        return Dataset(
            accounts: accounts,
            conversations: conversations,
            groupMembers: groupMembers,
            messages: messages
        )
    }

    static func makeUnreadTimeline() -> Dataset {
        let accounts = makeAccounts(count: 3)
        let conversations = makeConversations(count: 200)
        let groupMembers = makeGroupMembers(count: 24)
        var messages: [ChatMessage] = []
        messages.reserveCapacity(1_000)

        for conversationIndex in conversations.indices {
            let conversation = conversations[conversationIndex]
            let isGroup = isGroupConversation(conversationIndex)
            for localIndex in 0..<5 {
                let messageIndex = conversationIndex * 5 + localIndex
                let account = isGroup
                    ? accounts[(conversationIndex + localIndex) % accounts.count]
                    : accounts[conversationIndex % accounts.count]
                let sender = isGroup
                    ? groupMembers[(conversationIndex + localIndex) % groupMembers.count]
                    : conversation
                messages.append(makeMessage(
                    index: 100_000 + messageIndex,
                    conversation: conversation,
                    sender: sender,
                    accountID: account.id,
                    isOutgoing: false,
                    isGroup: isGroup,
                    type: unreadMessageTypes[messageIndex % unreadMessageTypes.count]
                ))
            }
        }

        return Dataset(
            accounts: accounts,
            conversations: conversations,
            groupMembers: groupMembers,
            messages: messages
        )
    }

    static func makeDetailConversation() -> Dataset {
        let accounts = makeAccounts(count: 3)
        let conversation = ChatParticipant(
            id: stableID(namespace: 2, index: 50_000),
            displayName: "Performance Group",
            tintColor: .systemIndigo,
            initials: "PG"
        )
        let groupMembers = makeGroupMembers(count: 32)
        var messages: [ChatMessage] = []
        messages.reserveCapacity(2_000)

        for messageIndex in 0..<2_000 {
            let isOutgoing = messageIndex.isMultiple(of: 5)
            let account = accounts[messageIndex % accounts.count]
            let sender = isOutgoing
                ? account
                : groupMembers[messageIndex % groupMembers.count]
            messages.append(makeMessage(
                index: 200_000 + messageIndex,
                conversation: conversation,
                sender: sender,
                accountID: account.id,
                isOutgoing: isOutgoing,
                isGroup: true,
                type: detailMessageTypes[messageIndex % detailMessageTypes.count]
            ))
        }

        return Dataset(
            accounts: accounts,
            conversations: [conversation],
            groupMembers: groupMembers,
            messages: messages
        )
    }

    static func makeLookupIndex(messages: [ChatMessage]) -> MessageLookupIndex {
        var offsetByID: [UUID: Int] = [:]
        var offsetsByConversation: [UUID: [Int]] = [:]
        var latestOffsetByConversation: [UUID: Int] = [:]
        offsetByID.reserveCapacity(messages.count)
        offsetsByConversation.reserveCapacity(min(messages.count, 2_000))
        latestOffsetByConversation.reserveCapacity(min(messages.count, 2_000))

        for (offset, message) in messages.enumerated() {
            offsetByID[message.id] = offset
            offsetsByConversation[message.conversationID, default: []].append(offset)
            guard let latestOffset = latestOffsetByConversation[message.conversationID] else {
                latestOffsetByConversation[message.conversationID] = offset
                continue
            }
            let latest = messages[latestOffset]
            if latest.sentAt < message.sentAt
                || (latest.sentAt == message.sentAt && latest.id.uuidString < message.id.uuidString) {
                latestOffsetByConversation[message.conversationID] = offset
            }
        }

        return MessageLookupIndex(
            messageOffsetByID: offsetByID,
            messageOffsetsByConversation: offsetsByConversation,
            latestMessageOffsetByConversation: latestOffsetByConversation
        )
    }

    // Reference implementation for tracking aggregation cost while production
    // moves toward an incremental index. It intentionally preserves the current
    // unread-home semantics without depending on a view controller.
    static func aggregatePendingReplies(
        messages: [ChatMessage],
        accountIDs: Set<UUID>
    ) -> [ChatMessage] {
        let fallbackAccountID = accountIDs.min { $0.uuidString < $1.uuidString }
        let grouped = Dictionary(grouping: messages) { message in
            ConversationAccountKey(
                conversationID: message.conversationID,
                accountID: message.recipientAccountID ?? fallbackAccountID ?? message.conversationID
            )
        }
        let pending = grouped.values.flatMap { groupedMessages -> [ChatMessage] in
            let relevant = groupedMessages
                .filter { message in
                    !message.isAI
                        && !message.sender.isAIAccount
                        && message.type != .system
                        && message.type != .groupNotice
                }
                .sorted(by: messageSortAscending)
            guard let latest = relevant.last,
                  isIncoming(latest, accountIDs: accountIDs)
            else { return [] }

            let pendingStart = relevant.lastIndex {
                !isIncoming($0, accountIDs: accountIDs)
            }.map { relevant.index(after: $0) } ?? relevant.startIndex
            return relevant[pendingStart...].filter {
                isIncoming($0, accountIDs: accountIDs)
            }
        }
        return pending.sorted(by: messageSortAscending)
    }

    private static let unreadMessageTypes: [ChatMessageType] = [
        .text, .voice, .image, .file, .quotedReply, .location, .webLink, .redPacket
    ]

    private static let detailMessageTypes: [ChatMessageType] = [
        .text, .image, .stickerGif, .video, .file, .voice, .location,
        .webLink, .quotedReply, .redPacket, .transfer, .mergedForward
    ]

    private static func makeAccounts(count: Int) -> [ChatParticipant] {
        (0..<count).map { index in
            ChatParticipant(
                id: stableID(namespace: 1, index: index),
                displayName: "Performance Account \(index)",
                tintColor: colors[index % colors.count],
                initials: "A\(index)",
                isCurrentUser: true,
                avatarURL: URL(string: "https://fixture.invalid/accounts/\(index).jpg")
            )
        }
    }

    private static func makeConversations(count: Int) -> [ChatParticipant] {
        (0..<count).map { index in
            ChatParticipant(
                id: stableID(namespace: 2, index: index),
                displayName: isGroupConversation(index)
                    ? "Performance Group \(index)"
                    : "Performance Contact \(index)",
                tintColor: colors[index % colors.count],
                initials: "C\(index % 100)",
                avatarURL: URL(string: "https://fixture.invalid/conversations/\(index).jpg")
            )
        }
    }

    private static func makeGroupMembers(count: Int) -> [ChatParticipant] {
        (0..<count).map { index in
            ChatParticipant(
                id: stableID(namespace: 3, index: index),
                displayName: "Performance Member \(index)",
                tintColor: colors[index % colors.count],
                initials: "M\(index % 100)",
                avatarURL: URL(string: "https://fixture.invalid/members/\(index).jpg")
            )
        }
    }

    private static func makeMessage(
        index: Int,
        conversation: ChatParticipant,
        sender: ChatParticipant,
        accountID: UUID,
        isOutgoing: Bool,
        isGroup: Bool,
        type: ChatMessageType
    ) -> ChatMessage {
        ChatMessage(
            id: stableID(namespace: 4, index: index),
            conversationID: conversation.id,
            type: type,
            sender: sender,
            body: body(for: type, index: index),
            detail: detail(for: type, index: index),
            isOutgoing: isOutgoing,
            presentation: isGroup ? .avatarAndName : .bare,
            timestamp: String(format: "%02d:%02d", (index / 60) % 24, index % 60),
            sentAt: referenceDate.addingTimeInterval(TimeInterval(index)),
            attachmentURL: attachmentURL(for: type, index: index),
            isGroupConversation: isGroup,
            richElements: richElements(for: type, index: index),
            recipientAccountID: accountID,
            unansweredMetadata: isGroup && !isOutgoing
                ? UnansweredMessageMetadata(
                    itemID: "performance-item-\(index)",
                    requiresReply: true
                )
                : nil
        )
    }

    private static func body(for type: ChatMessageType, index: Int) -> String {
        switch type {
        case .text, .quotedReply:
            return "Deterministic performance message \(index) with enough text to exercise grouping and layout metadata."
        case .image, .capturedPhoto: return "Fixture image \(index)"
        case .stickerGif: return "Fixture GIF \(index)"
        case .video, .channelsVideo: return "Fixture video \(index)"
        case .file: return "performance-fixture-\(index).pdf"
        case .voice: return "\(5 + index % 55) second voice message"
        case .location, .liveLocation: return "Fixture location \(index)"
        case .webLink, .article: return "Fixture link \(index)"
        default: return "\(type.title) fixture \(index)"
        }
    }

    private static func detail(for type: ChatMessageType, index: Int) -> String {
        switch type {
        case .file: return "PDF fixture, \(1_024 + index) KB"
        case .voice: return "Duration \(5 + index % 55) seconds"
        case .video: return "1920 x 1080, \(10 + index % 120) seconds"
        case .redPacket, .transfer: return "Amount CNY \(index % 500 + 1).00"
        default: return "Fixture detail \(index)"
        }
    }

    private static func attachmentURL(for type: ChatMessageType, index: Int) -> URL? {
        let pathExtension: String
        switch type {
        case .image, .capturedPhoto: pathExtension = "jpg"
        case .stickerGif: pathExtension = "gif"
        case .video, .channelsVideo: pathExtension = "mp4"
        case .file: pathExtension = "pdf"
        case .voice: pathExtension = "m4a"
        default: return nil
        }
        return URL(string: "https://fixture.invalid/media/\(index).\(pathExtension)")
    }

    private static func richElements(for type: ChatMessageType, index: Int) -> [BubbleRichElement] {
        switch type {
        case .image, .capturedPhoto:
            return [.image(
                name: "Fixture image \(index)",
                url: "https://fixture.invalid/media/\(index).jpg",
                aspect: index.isMultiple(of: 2) ? .horizontal : .vertical,
                access: .friends
            )]
        case .file:
            return [.file(
                name: "performance-fixture-\(index).pdf",
                format: .pdf,
                url: "https://fixture.invalid/media/\(index).pdf",
                preview: "Deterministic PDF fixture",
                access: .recipients
            )]
        case .location, .liveLocation:
            return [.location(
                title: "Fixture location \(index)",
                address: "Performance Road \(index)",
                url: "https://fixture.invalid/maps/\(index)"
            )]
        case .webLink, .article:
            return [.blueLink(
                label: "Fixture link \(index)",
                url: "https://fixture.invalid/links/\(index)",
                bracketed: false
            )]
        case .quotedReply:
            return [.quote(author: "Fixture author", text: "Referenced fixture message \(max(0, index - 1))")]
        default:
            return []
        }
    }

    private static func isIncoming(_ message: ChatMessage, accountIDs: Set<UUID>) -> Bool {
        !message.isOutgoing
            && !message.sender.isCurrentUser
            && !accountIDs.contains(message.sender.id)
    }

    private static func messageSortAscending(_ lhs: ChatMessage, _ rhs: ChatMessage) -> Bool {
        if lhs.sentAt == rhs.sentAt {
            return lhs.id.uuidString < rhs.id.uuidString
        }
        return lhs.sentAt < rhs.sentAt
    }

    private static func isGroupConversation(_ index: Int) -> Bool {
        index.isMultiple(of: 4)
    }

    private static func stableID(namespace: UInt16, index: Int) -> UUID {
        let value = UInt64(index)
        return UUID(uuid: (
            0x50, 0x45,
            UInt8(truncatingIfNeeded: namespace >> 8),
            UInt8(truncatingIfNeeded: namespace),
            0x00, 0x00, 0x00, 0x00,
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
}
