import Foundation

struct FloatingIMConversationSnapshot: Equatable {
    let currentConversationID: UUID?
    let selectedAccountID: UUID?
    let visibleConversationIDs: [UUID]
}

protocol FloatingIMKitProviding {
    var currentConversationID: UUID? { get }
    var selectedAccountID: UUID? { get }
    func visibleConversationIDs() -> [UUID]
    func makeSnapshot() -> FloatingIMConversationSnapshot
}

extension FloatingIMKitProviding {
    func makeSnapshot() -> FloatingIMConversationSnapshot {
        FloatingIMConversationSnapshot(
            currentConversationID: currentConversationID,
            selectedAccountID: selectedAccountID,
            visibleConversationIDs: visibleConversationIDs()
        )
    }
}

struct EmptyFloatingIMKitAdapter: FloatingIMKitProviding {
    let currentConversationID: UUID? = nil
    let selectedAccountID: UUID? = nil

    func visibleConversationIDs() -> [UUID] {
        []
    }
}

struct ClosureFloatingIMKitAdapter: FloatingIMKitProviding {
    let currentConversationIDProvider: () -> UUID?
    let selectedAccountIDProvider: () -> UUID?
    let visibleConversationIDsProvider: () -> [UUID]

    var currentConversationID: UUID? {
        currentConversationIDProvider()
    }

    var selectedAccountID: UUID? {
        selectedAccountIDProvider()
    }

    func visibleConversationIDs() -> [UUID] {
        visibleConversationIDsProvider()
    }
}

enum FloatingIMKitDemo {
    static func makeAdapter(conversationID: UUID, accountID: UUID) -> FloatingIMKitProviding {
        ClosureFloatingIMKitAdapter(
            currentConversationIDProvider: { conversationID },
            selectedAccountIDProvider: { accountID },
            visibleConversationIDsProvider: { [conversationID] }
        )
    }
}

enum FloatingIMConversationResolver {
    static func relatedAccountIDs(
        for conversationID: UUID,
        messages: [ChatMessage],
        accounts: [ChatParticipant]
    ) -> Set<UUID> {
        let accountIDs = Set(accounts.map(\.id))
        guard !accountIDs.isEmpty else { return [] }

        let directMessages = messages.filter {
            $0.conversationID == conversationID && !$0.isGroupConversation
        }
        guard !directMessages.isEmpty else { return [] }

        let explicitReceiverIDs = Set(directMessages.compactMap(\.recipientAccountID))
            .intersection(accountIDs)
        if !explicitReceiverIDs.isEmpty {
            return explicitReceiverIDs
        }

        let outgoingAccountIDs = Set(
            directMessages
                .filter { $0.isOutgoing && accountIDs.contains($0.sender.id) }
                .map(\.sender.id)
        )
        if !outgoingAccountIDs.isEmpty {
            return outgoingAccountIDs
        }

        // Missing ownership is invalid data. Fail closed so UI state cannot
        // silently invent a relationship that changes across datasets.
        return []
    }
}
