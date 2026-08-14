import UIKit

struct PendingReplyConversationAccountKey: Hashable {
    let conversationID: UUID
    let accountID: UUID
}

struct PendingReplyIndexedInput {
    let message: ChatMessage
    let key: PendingReplyConversationAccountKey
    let isIncoming: Bool
}

final class PendingReplyIndexAsyncResultStore {
    private let lock = NSLock()
    private var result: (generation: Int, index: PendingReplyIndex)?

    func store(_ index: PendingReplyIndex, generation: Int) {
        lock.lock()
        if result?.generation ?? Int.min <= generation {
            result = (generation, index)
        }
        lock.unlock()
    }

    func latest() -> (generation: Int, index: PendingReplyIndex)? {
        lock.lock()
        defer { lock.unlock() }
        return result
    }
}

struct PendingReplyIndex {
    private struct IndexedMessage {
        let message: ChatMessage
        let isIncoming: Bool
    }

    private struct Bucket {
        var messagesByID: [UUID: IndexedMessage] = [:]
        var orderedMessages: [IndexedMessage] = []
        var pendingStartIndex: Int?

        var pendingCount: Int {
            guard let pendingStartIndex else { return 0 }
            return orderedMessages.count - pendingStartIndex
        }

        mutating func upsert(_ indexedMessage: IndexedMessage) {
            if let previous = messagesByID[indexedMessage.message.id] {
                remove(previous.message)
            }
            messagesByID[indexedMessage.message.id] = indexedMessage

            if let last = orderedMessages.last,
               !PendingReplyIndex.messageSortAscending(indexedMessage.message, last.message) {
                orderedMessages.append(indexedMessage)
                return
            }

            let insertionIndex = orderedMessages.partitioningIndex {
                PendingReplyIndex.messageSortAscending(indexedMessage.message, $0.message)
            }
            orderedMessages.insert(indexedMessage, at: insertionIndex)
        }

        @discardableResult
        mutating func remove(_ message: ChatMessage) -> Bool {
            guard messagesByID.removeValue(forKey: message.id) != nil else { return false }
            let candidateIndex = orderedMessages.partitioningIndex {
                !PendingReplyIndex.messageSortAscending($0.message, message)
            }
            guard candidateIndex < orderedMessages.count,
                  orderedMessages[candidateIndex].message.id == message.id
            else {
                orderedMessages.removeAll { $0.message.id == message.id }
                return true
            }
            orderedMessages.remove(at: candidateIndex)
            return true
        }

        mutating func refreshPendingRange() {
            guard let latest = orderedMessages.last, latest.isIncoming else {
                pendingStartIndex = nil
                return
            }
            pendingStartIndex = orderedMessages.lastIndex { !$0.isIncoming }
                .map { orderedMessages.index(after: $0) }
                ?? orderedMessages.startIndex
        }

        mutating func replaceContents(with indexedMessages: [IndexedMessage]) {
            messagesByID.removeAll(keepingCapacity: true)
            messagesByID.reserveCapacity(indexedMessages.count)
            for indexedMessage in indexedMessages {
                messagesByID[indexedMessage.message.id] = indexedMessage
            }
            orderedMessages = Array(messagesByID.values)
            orderedMessages.sort {
                PendingReplyIndex.messageSortAscending($0.message, $1.message)
            }
            refreshPendingRange()
        }
    }

    private struct HeapCursor {
        let key: PendingReplyConversationAccountKey
        let messageIndex: Int
        let message: ChatMessage
    }

    private var buckets: [PendingReplyConversationAccountKey: Bucket] = [:]
    private var keyByMessageID: [UUID: PendingReplyConversationAccountKey] = [:]
    private(set) var isInitialized = false
    private(set) var fullRebuildCount = 0
    private(set) var incrementalMutationCount = 0
    private(set) var totalPendingCount = 0

    mutating func invalidate() {
        buckets.removeAll(keepingCapacity: true)
        keyByMessageID.removeAll(keepingCapacity: true)
        totalPendingCount = 0
        isInitialized = false
    }

    mutating func rebuild(
        messages: [ChatMessage],
        keyForMessage: (ChatMessage) -> PendingReplyConversationAccountKey?,
        isRelevant: (ChatMessage) -> Bool,
        isIncoming: (ChatMessage) -> Bool
    ) {
        var inputs: [PendingReplyIndexedInput] = []
        inputs.reserveCapacity(messages.count)
        for message in messages where isRelevant(message) {
            guard let key = keyForMessage(message) else { continue }
            inputs.append(
                PendingReplyIndexedInput(
                    message: message,
                    key: key,
                    isIncoming: isIncoming(message)
                )
            )
        }
        rebuild(classifiedInputs: inputs)
    }

    mutating func rebuild(classifiedInputs: [PendingReplyIndexedInput]) {
        buckets.removeAll(keepingCapacity: true)
        keyByMessageID.removeAll(keepingCapacity: true)
        buckets.reserveCapacity(min(classifiedInputs.count, 2_000))
        keyByMessageID.reserveCapacity(classifiedInputs.count)

        var groupedInputs: [PendingReplyConversationAccountKey: [IndexedMessage]] = [:]
        groupedInputs.reserveCapacity(min(classifiedInputs.count, 2_000))
        for input in classifiedInputs {
            groupedInputs[input.key, default: []].append(
                IndexedMessage(message: input.message, isIncoming: input.isIncoming)
            )
            keyByMessageID[input.message.id] = input.key
        }

        totalPendingCount = 0
        for (key, indexedMessages) in groupedInputs {
            var bucket = Bucket()
            bucket.replaceContents(with: indexedMessages)
            totalPendingCount += bucket.pendingCount
            buckets[key] = bucket
        }
        isInitialized = true
        fullRebuildCount += 1
    }

    mutating func apply(
        _ mutations: [MessageMutation],
        keyForMessage: (ChatMessage) -> PendingReplyConversationAccountKey?,
        isRelevant: (ChatMessage) -> Bool,
        isIncoming: (ChatMessage) -> Bool
    ) {
        guard isInitialized, !mutations.isEmpty else { return }
        var affectedKeys = Set<PendingReplyConversationAccountKey>()
        affectedKeys.reserveCapacity(mutations.count * 2)

        for mutation in mutations {
            if let previousKey = keyByMessageID.removeValue(forKey: mutation.messageID),
               var bucket = buckets[previousKey],
               let previous = bucket.messagesByID[mutation.messageID] {
                if !affectedKeys.contains(previousKey) {
                    totalPendingCount -= bucket.pendingCount
                }
                bucket.remove(previous.message)
                buckets[previousKey] = bucket
                affectedKeys.insert(previousKey)
            }

            let message: ChatMessage?
            switch mutation {
            case .insert(let inserted), .update(let inserted):
                message = inserted
            case .delete:
                message = nil
            }

            guard let message, isRelevant(message) else { continue }
            guard let key = keyForMessage(message) else { continue }
            if !affectedKeys.contains(key) {
                totalPendingCount -= buckets[key]?.pendingCount ?? 0
            }
            buckets[key, default: Bucket()].upsert(
                IndexedMessage(message: message, isIncoming: isIncoming(message))
            )
            keyByMessageID[message.id] = key
            affectedKeys.insert(key)
        }

        for key in affectedKeys {
            guard var bucket = buckets[key] else { continue }
            if bucket.orderedMessages.isEmpty {
                buckets.removeValue(forKey: key)
                continue
            }
            bucket.refreshPendingRange()
            totalPendingCount += bucket.pendingCount
            buckets[key] = bucket
        }
        incrementalMutationCount += mutations.count
    }

    func latestMessages(limit: Int) -> [ChatMessage] {
        guard isInitialized, limit > 0, totalPendingCount > 0 else { return [] }
        var heap: [HeapCursor] = []
        heap.reserveCapacity(min(buckets.count, limit))

        for (key, bucket) in buckets {
            guard bucket.pendingStartIndex != nil,
                  let latest = bucket.orderedMessages.last
            else { continue }
            heapPush(
                HeapCursor(
                    key: key,
                    messageIndex: bucket.orderedMessages.count - 1,
                    message: latest.message
                ),
                into: &heap
            )
        }

        var descending: [ChatMessage] = []
        descending.reserveCapacity(min(limit, totalPendingCount))
        while descending.count < limit, let cursor = heapPop(from: &heap) {
            descending.append(cursor.message)
            guard let bucket = buckets[cursor.key],
                  let pendingStartIndex = bucket.pendingStartIndex,
                  cursor.messageIndex > pendingStartIndex
            else { continue }
            let previousIndex = cursor.messageIndex - 1
            heapPush(
                HeapCursor(
                    key: cursor.key,
                    messageIndex: previousIndex,
                    message: bucket.orderedMessages[previousIndex].message
                ),
                into: &heap
            )
        }
        return descending.reversed()
    }

    private static func messageSortAscending(_ lhs: ChatMessage, _ rhs: ChatMessage) -> Bool {
        if lhs.sentAt == rhs.sentAt {
            return lhs.id.uuidString < rhs.id.uuidString
        }
        return lhs.sentAt < rhs.sentAt
    }

    private func heapPush(_ cursor: HeapCursor, into heap: inout [HeapCursor]) {
        heap.append(cursor)
        var child = heap.count - 1
        while child > 0 {
            let parent = (child - 1) / 2
            guard Self.messageSortAscending(heap[parent].message, heap[child].message) else { break }
            heap.swapAt(parent, child)
            child = parent
        }
    }

    private func heapPop(from heap: inout [HeapCursor]) -> HeapCursor? {
        guard !heap.isEmpty else { return nil }
        if heap.count == 1 { return heap.removeLast() }
        let result = heap[0]
        heap[0] = heap.removeLast()
        var parent = 0
        while true {
            let left = parent * 2 + 1
            guard left < heap.count else { break }
            let right = left + 1
            let largerChild = right < heap.count
                && Self.messageSortAscending(heap[left].message, heap[right].message)
                ? right
                : left
            guard Self.messageSortAscending(heap[parent].message, heap[largerChild].message) else { break }
            heap.swapAt(parent, largerChild)
            parent = largerChild
        }
        return result
    }
}

struct UnansweredItem: Hashable {
    enum Kind: Hashable {
        case direct
        case group
    }

    let id: UnansweredItemID
    let kind: Kind
    let conversationID: UUID
    let representativeMessageID: UUID
    let messageIDs: [UUID]
    let targetAccountIDs: [UUID]
}

struct PendingReplyPresentation {
    let messages: [ChatMessage]
    let items: [UnansweredItem]
    let itemByID: [UnansweredItemID: UnansweredItem]
    let itemByMessageID: [UUID: UnansweredItem]
    let hiddenDirectMessageCountByConversationID: [UUID: Int]
    let hiddenDirectMessageCountByKey: [PendingReplyConversationAccountKey: Int]
    let hiddenGroupItemCountByConversationID: [UUID: Int]
    let directMessageCountByConversationID: [UUID: Int]
    let directMessageCountByKey: [PendingReplyConversationAccountKey: Int]
    // Number of distinct (account, conversation/reply target) obligations.
    let pendingConversationAccountCount: Int

    func item(forMessageID messageID: UUID) -> UnansweredItem? {
        itemByMessageID[messageID]
    }
}

enum UnansweredReplyAdvancePlanner {
    static func nextMessageID(
        candidateMessageIDs: [UUID],
        availableMessages: [ChatMessage]
    ) -> UUID? {
        let availableIDs = Set(availableMessages.map(\.id))
        return candidateMessageIDs.first(where: availableIDs.contains)
            ?? availableMessages.first?.id
    }

    static func nextFocus(
        candidateItemIDs: [UnansweredItemID],
        presentation: PendingReplyPresentation
    ) -> UnansweredTimelineFocus? {
        let item = candidateItemIDs.lazy.compactMap { presentation.itemByID[$0] }.first
            ?? presentation.items.first
        guard let item else { return nil }
        return UnansweredTimelineFocus(
            itemID: item.id,
            messageID: item.representativeMessageID
        )
    }
}

enum UnreadTimelineFoldKind {
    case directMessages
    case groupItems
}

struct UnreadTimelineFoldPresentation {
    let conversationID: UUID
    let accountID: UUID?
    let kind: UnreadTimelineFoldKind
    let title: String
}

enum PendingReplyPresentationBuilder {
    private struct DirectItemKey: Hashable {
        let conversationID: UUID
        let accountID: UUID
    }

    private struct GroupItemKey: Hashable {
        let conversationID: UUID
        let accountID: UUID
        let itemKey: String
    }

    private struct ResolvedGroupItemKey: Hashable {
        let conversationID: UUID
        let itemKey: String
    }

    static func build(
        pendingMessages: [ChatMessage],
        allMessages: [ChatMessage],
        currentAccountIDs: Set<UUID>,
        accountFilterID: UUID? = nil,
        directAccountID: (ChatMessage) -> UUID? = { $0.recipientAccountID },
        directVisibleLimits: [PendingReplyConversationAccountKey: Int],
        defaultDirectVisibleLimit: Int = 3,
        groupVisibleLimits: [UUID: Int] = [:],
        defaultGroupVisibleLimit: Int = 3
    ) -> PendingReplyPresentation {
        let accountOrder = Dictionary(
            uniqueKeysWithValues: currentAccountIDs.sorted { $0.uuidString < $1.uuidString }
                .enumerated().map { ($0.element, $0.offset) }
        )
        let directPairs = pendingMessages.compactMap { message -> (DirectItemKey, ChatMessage)? in
            guard !message.isGroupConversation,
                  let accountID = directAccountID(message),
                  accountFilterID == nil || accountFilterID == accountID
            else { return nil }
            return (DirectItemKey(conversationID: message.conversationID, accountID: accountID), message)
        }
        let directGroups = Dictionary(grouping: directPairs, by: { $0.0.conversationID })
        var visibleDirectMessages: [ChatMessage] = []
        var hiddenDirectCounts: [UUID: Int] = [:]
        var hiddenDirectCountsByKey: [PendingReplyConversationAccountKey: Int] = [:]
        var directCounts: [UUID: Int] = [:]
        var directCountsByKey: [PendingReplyConversationAccountKey: Int] = [:]
        var items: [UnansweredItem] = []
        var itemByMessageID: [UUID: UnansweredItem] = [:]

        // 同一好友只占一个头像组，但每个主帐号分别保留自己的可见消息上限。
        for (conversationID, pairs) in directGroups {
            let pairsByAccount = Dictionary(grouping: pairs, by: { $0.0.accountID })
            let orderedAccountGroups = pairsByAccount.compactMap {
                accountID, accountPairs -> (accountID: UUID, messages: [ChatMessage])? in
                let messages = deduplicated(
                    accountPairs.map(\.1).sorted(by: messageSortAscending)
                )
                return messages.isEmpty ? nil : (accountID, messages)
            }.sorted { lhs, rhs in
                guard let lhsFirst = lhs.messages.first,
                      let rhsFirst = rhs.messages.first
                else { return lhs.accountID.uuidString < rhs.accountID.uuidString }
                if lhsFirst.sentAt == rhsFirst.sentAt {
                    return lhs.accountID.uuidString < rhs.accountID.uuidString
                }
                return messageSortAscending(lhsFirst, rhsFirst)
            }

            var conversationMessageCount = 0
            var conversationHiddenCount = 0
            for accountGroup in orderedAccountGroups {
                let accountID = accountGroup.accountID
                let orderedAccountMessages = accountGroup.messages
                guard let representative = orderedAccountMessages.last else { continue }
                let directKey = PendingReplyConversationAccountKey(
                    conversationID: conversationID,
                    accountID: accountID
                )
                let visibleLimit = max(
                    1,
                    directVisibleLimits[directKey] ?? defaultDirectVisibleLimit
                )
                let visible = Array(orderedAccountMessages.prefix(visibleLimit))
                conversationMessageCount += orderedAccountMessages.count
                let hiddenCount = max(0, orderedAccountMessages.count - visible.count)
                conversationHiddenCount += hiddenCount
                directCountsByKey[directKey] = orderedAccountMessages.count
                if hiddenCount > 0 {
                    hiddenDirectCountsByKey[directKey] = hiddenCount
                }
                visibleDirectMessages.append(contentsOf: visible)

                let item = UnansweredItem(
                    id: UnansweredItemID(
                        conversationID: conversationID,
                        discriminator: "direct:\(accountID.uuidString)"
                    ),
                    kind: .direct,
                    conversationID: conversationID,
                    representativeMessageID: representative.id,
                    messageIDs: orderedAccountMessages.map(\.id),
                    targetAccountIDs: [accountID]
                )
                items.append(item)
                for message in visible {
                    itemByMessageID[message.id] = item
                }
            }
            directCounts[conversationID] = conversationMessageCount
            hiddenDirectCounts[conversationID] = conversationHiddenCount
        }

        let groupResult = deriveGroupItems(
            from: allMessages,
            currentAccountIDs: accountFilterID.map { [$0] } ?? currentAccountIDs,
            visibleLimits: groupVisibleLimits,
            defaultVisibleLimit: defaultGroupVisibleLimit,
            accountOrder: accountOrder
        )
        items.append(contentsOf: groupResult.items)
        for item in groupResult.items {
            itemByMessageID[item.representativeMessageID] = item
        }
        let messages = messagesGroupedByConversation(
            visibleDirectMessages + groupResult.messages
        )
        // The builder already resolved all outstanding group reply state.
        // Reuse its pair count instead of scanning messages a second time.
        let pendingConversationAccountCount = directCountsByKey.count + groupResult.pairCount
        let visibleMessageByID = Dictionary(
            messages.map { ($0.id, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        items.sort { lhs, rhs in
            let lhsMessage = visibleMessageByID[lhs.representativeMessageID]
            let rhsMessage = visibleMessageByID[rhs.representativeMessageID]
            guard let lhsMessage, let rhsMessage else {
                return lhs.id.discriminator < rhs.id.discriminator
            }
            return messageSortAscending(lhsMessage, rhsMessage)
        }
        return PendingReplyPresentation(
            messages: messages,
            items: items,
            itemByID: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            itemByMessageID: itemByMessageID,
            hiddenDirectMessageCountByConversationID: hiddenDirectCounts.filter { $0.value > 0 },
            hiddenDirectMessageCountByKey: hiddenDirectCountsByKey,
            hiddenGroupItemCountByConversationID: groupResult.hiddenCounts,
            directMessageCountByConversationID: directCounts,
            directMessageCountByKey: directCountsByKey,
            pendingConversationAccountCount: pendingConversationAccountCount
        )
    }

    private static func messagesGroupedByConversation(
        _ messages: [ChatMessage]
    ) -> [ChatMessage] {
        Dictionary(grouping: messages, by: \ChatMessage.conversationID)
            .map { conversationID, messages in
                (
                    conversationID: conversationID,
                    // 输入已按主帐号分块、块内按时间排序，这里保持该稳定顺序。
                    messages: messages
                )
            }
            .sorted { lhs, rhs in
                guard let lhsFirst = lhs.messages.first,
                      let rhsFirst = rhs.messages.first
                else {
                    return lhs.conversationID.uuidString < rhs.conversationID.uuidString
                }
                if lhsFirst.sentAt == rhsFirst.sentAt {
                    return lhs.conversationID.uuidString < rhs.conversationID.uuidString
                }
                return messageSortAscending(lhsFirst, rhsFirst)
            }
            .flatMap(\.messages)
    }

    private static func deriveGroupItems(
        from allMessages: [ChatMessage],
        currentAccountIDs: Set<UUID>,
        visibleLimits: [UUID: Int],
        defaultVisibleLimit: Int,
        accountOrder: [UUID: Int]
    ) -> (messages: [ChatMessage], items: [UnansweredItem], hiddenCounts: [UUID: Int], pairCount: Int) {
        let groupMessages = allMessages
            .filter(\.isGroupConversation)
            .sorted(by: messageSortAscending)
        let messagesByID = Dictionary(
            groupMessages.map { ($0.id, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        var pendingByKey: [GroupItemKey: ChatMessage] = [:]

        for message in groupMessages {
            if message.isOutgoing || currentAccountIDs.contains(message.sender.id) {
                guard currentAccountIDs.contains(message.sender.id),
                      let itemKey = groupItemKey(for: message, messagesByID: messagesByID)
                else { continue }
                pendingByKey.removeValue(forKey: GroupItemKey(
                    conversationID: message.conversationID,
                    accountID: message.sender.id,
                    itemKey: itemKey
                ))
                continue
            }

            let targetAccountIDs = directedAccountIDs(
                for: message,
                currentAccountIDs: currentAccountIDs,
                messagesByID: messagesByID
            )
            guard !targetAccountIDs.isEmpty,
                  let itemKey = groupItemKey(for: message, messagesByID: messagesByID)
            else { continue }
            if message.unansweredMetadata?.handledAt != nil {
                for accountID in targetAccountIDs {
                    pendingByKey.removeValue(forKey: GroupItemKey(
                        conversationID: message.conversationID,
                        accountID: accountID,
                        itemKey: itemKey
                    ))
                }
                continue
            }
            for accountID in targetAccountIDs {
                pendingByKey[GroupItemKey(
                    conversationID: message.conversationID,
                    accountID: accountID,
                    itemKey: itemKey
                )] = message
            }
        }

        var resolvedItems: [ResolvedGroupItemKey: (message: ChatMessage, accountIDs: Set<UUID>)] = [:]
        for (key, message) in pendingByKey {
            let resolvedKey = ResolvedGroupItemKey(
                conversationID: key.conversationID,
                itemKey: key.itemKey
            )
            if var existing = resolvedItems[resolvedKey] {
                existing.accountIDs.insert(key.accountID)
                if messageSortAscending(existing.message, message) {
                    existing.message = message
                }
                resolvedItems[resolvedKey] = existing
            } else {
                resolvedItems[resolvedKey] = (message, [key.accountID])
            }
        }
        let itemsByConversation = Dictionary(grouping: resolvedItems, by: { $0.key.conversationID })
        var visibleMessages: [ChatMessage] = []
        var visibleItems: [UnansweredItem] = []
        var hiddenCounts: [UUID: Int] = [:]
        for (conversationID, entries) in itemsByConversation {
            let ordered = entries.sorted { messageSortAscending($0.value.message, $1.value.message) }
            let visibleLimit = max(1, visibleLimits[conversationID] ?? defaultVisibleLimit)
            // Keep the newest representatives visible. Older items remain
            // available through the fold expansion control.
            let visible = Array(ordered.suffix(visibleLimit))
            for entry in visible {
                let accountIDs = entry.value.accountIDs.sorted {
                    (accountOrder[$0] ?? Int.max) < (accountOrder[$1] ?? Int.max)
                }
                let item = UnansweredItem(
                    id: UnansweredItemID(
                        conversationID: conversationID,
                        discriminator: "group:\(entry.key.itemKey)"
                    ),
                    kind: .group,
                    conversationID: conversationID,
                    representativeMessageID: entry.value.message.id,
                    messageIDs: [entry.value.message.id],
                    targetAccountIDs: accountIDs
                )
                visibleItems.append(item)
                visibleMessages.append(entry.value.message)
            }
            let hiddenCount = max(0, ordered.count - visible.count)
            if hiddenCount > 0 {
                hiddenCounts[conversationID] = hiddenCount
            }
        }
        let pairCount = Set(pendingByKey.keys.map {
            PendingReplyConversationAccountKey(
                conversationID: $0.conversationID,
                accountID: $0.accountID
            )
        }).count
        return (visibleMessages, visibleItems, hiddenCounts, pairCount)
    }

    private static func directedAccountIDs(
        for message: ChatMessage,
        currentAccountIDs: Set<UUID>,
        messagesByID: [UUID: ChatMessage]
    ) -> Set<UUID> {
        var result = Set<UUID>()
        if let metadata = message.unansweredMetadata {
            result.formUnion(metadata.mentionedAccountIDs.filter(currentAccountIDs.contains))
            result.formUnion(metadata.assignedAccountIDs.filter(currentAccountIDs.contains))
            if metadata.requiresReply,
               let recipientAccountID = message.recipientAccountID,
               currentAccountIDs.contains(recipientAccountID) {
                result.insert(recipientAccountID)
            }
        }
        if let quotedMessageID = message.quotedMessageID,
           let quoted = messagesByID[quotedMessageID],
           quoted.isOutgoing,
           currentAccountIDs.contains(quoted.sender.id) {
            result.insert(quoted.sender.id)
        }
        return result
    }

    private static func groupItemKey(
        for message: ChatMessage,
        messagesByID: [UUID: ChatMessage]
    ) -> String? {
        if let threadID = message.unansweredMetadata?.threadID, !threadID.isEmpty {
            return "thread:\(threadID)"
        }
        if let itemID = message.unansweredMetadata?.itemID, !itemID.isEmpty {
            return "item:\(itemID)"
        }
        if let quotedMessageID = message.quotedMessageID {
            return "relation:\(relationRootID(from: quotedMessageID, messagesByID: messagesByID).uuidString)"
        }
        guard message.unansweredMetadata?.requiresReply == true
                || !(message.unansweredMetadata?.mentionedAccountIDs.isEmpty ?? true)
                || !(message.unansweredMetadata?.assignedAccountIDs.isEmpty ?? true)
        else { return nil }
        return "relation:\(message.id.uuidString)"
    }

    private static func relationRootID(
        from messageID: UUID,
        messagesByID: [UUID: ChatMessage]
    ) -> UUID {
        var currentID = messageID
        var visited = Set<UUID>()
        while visited.insert(currentID).inserted,
              let parentID = messagesByID[currentID]?.quotedMessageID {
            currentID = parentID
        }
        return currentID
    }

    private static func deduplicated(_ messages: [ChatMessage]) -> [ChatMessage] {
        var seen = Set<UUID>()
        return messages.filter { seen.insert($0.id).inserted }
    }

    private static func messageSortAscending(_ lhs: ChatMessage, _ rhs: ChatMessage) -> Bool {
        if lhs.sentAt == rhs.sentAt {
            return lhs.id.uuidString < rhs.id.uuidString
        }
        return lhs.sentAt < rhs.sentAt
    }
}

private extension Array {
    func partitioningIndex(where belongsInSecondPartition: (Element) -> Bool) -> Int {
        var lowerBound = startIndex
        var upperBound = endIndex
        while lowerBound < upperBound {
            let distance = self.distance(from: lowerBound, to: upperBound)
            let middle = index(lowerBound, offsetBy: distance / 2)
            if belongsInSecondPartition(self[middle]) {
                upperBound = middle
            } else {
                lowerBound = index(after: middle)
            }
        }
        return lowerBound
    }
}

enum UnreadTimelineLayoutMetrics {
    static let avatarSideLength: CGFloat = 50
    static let foldFooterHeight: CGFloat = 30
    static let foldExpansionBatchSize = 15
    static let groupMessageNameClearance: CGFloat = 10
    static let groupMessageOverlayClearance: CGFloat = 4
    static let unnamedAdjacentMessageSpacing: CGFloat = 6

    static var adjacentGroupMessageSpacing: CGFloat {
        groupMessageNameClearance + groupMessageOverlayClearance
    }
}

final class ContentOffsetPreservingFlowLayout: UICollectionViewFlowLayout {
    var updateLockedContentOffset: CGPoint?

    override func targetContentOffset(
        forProposedContentOffset proposedContentOffset: CGPoint
    ) -> CGPoint {
        updateLockedContentOffset
            ?? super.targetContentOffset(forProposedContentOffset: proposedContentOffset)
    }
}

struct UnreadTimelineGroupViewportAnchor {
    let participantID: UUID
    let offsetFromViewportTop: CGFloat

    static func capture(
        participantID: UUID,
        snapshot: UnreadTimelineLayoutSnapshot,
        contentOffsetY: CGFloat
    ) -> UnreadTimelineGroupViewportAnchor? {
        guard let groupIndex = snapshot.groupIndexByParticipantID[participantID],
              let group = snapshot.groups[safe: groupIndex]
        else { return nil }
        return UnreadTimelineGroupViewportAnchor(
            participantID: participantID,
            offsetFromViewportTop: group.frame.minY - contentOffsetY
        )
    }

    static func captureVisible(
        snapshot: UnreadTimelineLayoutSnapshot,
        contentOffsetY: CGFloat
    ) -> UnreadTimelineGroupViewportAnchor? {
        guard let group = snapshot.groups.first(where: { $0.frame.maxY > contentOffsetY })
        else { return nil }
        return UnreadTimelineGroupViewportAnchor(
            participantID: group.participantID,
            offsetFromViewportTop: group.frame.minY - contentOffsetY
        )
    }

    func restoredContentOffsetY(
        snapshot: UnreadTimelineLayoutSnapshot,
        minimumOffsetY: CGFloat,
        maximumOffsetY: CGFloat
    ) -> CGFloat? {
        guard let groupIndex = snapshot.groupIndexByParticipantID[participantID],
              let group = snapshot.groups[safe: groupIndex]
        else { return nil }
        return min(
            max(group.frame.minY - offsetFromViewportTop, minimumOffsetY),
            maximumOffsetY
        )
    }
}

struct UnreadTimelineGroupLayout {
    let participantID: UUID
    let messageIndices: [Int]
    let frame: CGRect
    let avatarFrame: CGRect
}

enum UnreadTimelineAvatarPinning {
    static func topOffset(
        groupFrame: CGRect,
        naturalAvatarFrame: CGRect,
        viewportTop: CGFloat,
        viewportBottom: CGFloat,
        avatarHeight: CGFloat
    ) -> CGFloat {
        let naturalTop = naturalAvatarFrame.minY
        let maximumTop = max(naturalTop, groupFrame.maxY - avatarHeight)
        var resolvedTop = min(max(naturalTop, viewportTop), maximumTop)
        let visibleTop = max(groupFrame.minY, viewportTop)
        let visibleBottom = min(groupFrame.maxY, viewportBottom)
        if visibleBottom - visibleTop >= avatarHeight {
            resolvedTop = min(max(naturalTop, visibleTop), visibleBottom - avatarHeight)
        }
        return resolvedTop - groupFrame.minY
    }
}

struct UnreadTimelineLayoutSnapshot {
    let width: CGFloat
    let messageFrames: [CGRect]
    let messageTopSpacings: [CGFloat]
    let messageBottomPaddings: [CGFloat]
    let messageFoldBottomOffsets: [CGFloat?]
    let groups: [UnreadTimelineGroupLayout]
    let groupIndexByParticipantID: [UUID: Int]
    let contentHeight: CGFloat
}

struct UnreadTimelinePaginationAnchor {
    let messageID: UUID
    let offsetFromViewportTop: CGFloat

    static func capture(
        messages: [ChatMessage],
        snapshot: UnreadTimelineLayoutSnapshot,
        contentOffsetY: CGFloat
    ) -> UnreadTimelinePaginationAnchor? {
        guard messages.count == snapshot.messageFrames.count else { return nil }
        let anchorIndex = snapshot.messageFrames.firstIndex { frame in
            frame.maxY > contentOffsetY
        }
        guard let anchorIndex, let message = messages[safe: anchorIndex] else { return nil }
        return UnreadTimelinePaginationAnchor(
            messageID: message.id,
            offsetFromViewportTop: snapshot.messageFrames[anchorIndex].minY - contentOffsetY
        )
    }

    func restoredContentOffsetY(
        messages: [ChatMessage],
        snapshot: UnreadTimelineLayoutSnapshot,
        minimumOffsetY: CGFloat
    ) -> CGFloat? {
        guard messages.count == snapshot.messageFrames.count,
              let anchorIndex = messages.firstIndex(where: { $0.id == messageID })
        else { return nil }
        return max(
            minimumOffsetY,
            snapshot.messageFrames[anchorIndex].minY - offsetFromViewportTop
        )
    }
}

extension ChatWindowViewController {
    func unreadTimelineLayoutSnapshot(width: CGFloat? = nil) -> UnreadTimelineLayoutSnapshot? {
        guard isHomeTimeline else { return nil }
        let resolvedWidth = width ?? messageCollectionView.bounds.width
        guard resolvedWidth > 1 else { return nil }

        let cacheKey = "\(renderedMessagesCurrentCacheKey())|width:\(Int(resolvedWidth.rounded()))"
        if cacheKey == unreadTimelineLayoutCacheKey,
           let cached = unreadTimelineLayoutCache {
            return cached
        }

        let messages = renderedMessages
        var messageFrames = Array(repeating: CGRect.zero, count: messages.count)
        var messageTopSpacings = Array(repeating: CGFloat.zero, count: messages.count)
        var messageBottomPaddings = Array(repeating: CGFloat.zero, count: messages.count)
        var messageFoldBottomOffsets = Array<CGFloat?>(repeating: nil, count: messages.count)
        var groups: [UnreadTimelineGroupLayout] = []
        var groupIndexByParticipantID: [UUID: Int] = [:]
        var cursor: CGFloat = 0
        var messageIndex = 0

        while messageIndex < messages.count {
            let participantID = messages[messageIndex].conversationID
            let firstIndex = messageIndex
            while messageIndex < messages.count,
                  messages[messageIndex].conversationID == participantID {
                messageIndex += 1
            }
            let indices = Array(firstIndex..<messageIndex)
            let groupStart = cursor
            let leadingSpacing: CGFloat = groups.isEmpty ? 0 : 10
            let firstMessageNameClearance = messages[firstIndex].isGroupConversation
                ? UnreadTimelineLayoutMetrics.groupMessageNameClearance
                : 0
            let measuredHeights = indices.map { index in
                cachedHeight(
                    for: messages[index],
                    width: resolvedWidth,
                    showsGroupAvatar: false,
                    showsGroupName: true,
                    usesUnansweredPresentation: true
                )
            }
            for (offset, index) in indices.enumerated() {
                let topSpacing = offset == 0
                    ? leadingSpacing
                        + firstMessageNameClearance
                    : (messages[index].isGroupConversation
                        ? UnreadTimelineLayoutMetrics.adjacentGroupMessageSpacing
                        : UnreadTimelineLayoutMetrics.unnamedAdjacentMessageSpacing)
                let height = measuredHeights[offset] + topSpacing
                messageTopSpacings[index] = topSpacing
                messageFrames[index] = CGRect(
                    x: 0,
                    y: cursor,
                    width: resolvedWidth,
                    height: height
                )
                cursor += height

                let isFoldBoundary: Bool
                if messages[index].isGroupConversation {
                    isFoldBoundary = offset == indices.count - 1
                } else if let nextIndex = indices[safe: offset + 1] {
                    isFoldBoundary = unansweredRecipientAccountID(for: messages[index])
                        != unansweredRecipientAccountID(for: messages[nextIndex])
                } else {
                    isFoldBoundary = true
                }
                if isFoldBoundary,
                   unreadTimelineFoldPresentation(for: messages[index]) != nil {
                    messageFoldBottomOffsets[index] = 0
                    messageBottomPaddings[index] += UnreadTimelineLayoutMetrics.foldFooterHeight
                    messageFrames[index].size.height += UnreadTimelineLayoutMetrics.foldFooterHeight
                    cursor += UnreadTimelineLayoutMetrics.foldFooterHeight
                }
            }

            let firstMessageTop = groupStart + leadingSpacing + firstMessageNameClearance
            let firstMessageCenterY = firstMessageTop + measuredHeights[0] / 2
            let avatarFrame = CGRect(
                x: 0,
                y: firstMessageCenterY - Self.sidebarItemSize.height / 2,
                width: Self.sidebarItemSize.width,
                height: Self.sidebarItemSize.height
            )
            let visualHeight = cursor - groupStart
            let groupBodyHeight = max(avatarFrame.maxY - groupStart, visualHeight)
            let trailingPadding = groupBodyHeight - visualHeight
            if let lastIndex = indices.last {
                if trailingPadding > 0 {
                    messageBottomPaddings[lastIndex] += trailingPadding
                    messageFrames[lastIndex].size.height += trailingPadding
                    cursor += trailingPadding
                }
            }
            let groupHeight = groupBodyHeight

            let groupIndex = groups.count
            groupIndexByParticipantID[participantID] = groupIndex
            groups.append(
                UnreadTimelineGroupLayout(
                    participantID: participantID,
                    messageIndices: indices,
                    frame: CGRect(
                        x: 0,
                        y: groupStart,
                        width: resolvedWidth,
                        height: groupHeight
                    ),
                    avatarFrame: avatarFrame
                )
            )
        }

        let snapshot = UnreadTimelineLayoutSnapshot(
            width: resolvedWidth,
            messageFrames: messageFrames,
            messageTopSpacings: messageTopSpacings,
            messageBottomPaddings: messageBottomPaddings,
            messageFoldBottomOffsets: messageFoldBottomOffsets,
            groups: groups,
            groupIndexByParticipantID: groupIndexByParticipantID,
            contentHeight: cursor
        )
        unreadTimelineLayoutCacheKey = cacheKey
        unreadTimelineLayoutCache = snapshot
        return snapshot
    }

    func unreadTimelineMessageTopSpacing(at messageIndex: Int) -> CGFloat {
        if isHomeTimeline {
            return unreadTimelineLayoutSnapshot()?.messageTopSpacings[safe: messageIndex] ?? 0
        }
        guard let message = renderedMessages[safe: messageIndex], !message.isOutgoing else { return 0 }
        return 4
    }
}
