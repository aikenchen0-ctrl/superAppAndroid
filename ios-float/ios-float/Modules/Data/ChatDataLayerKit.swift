import Foundation

protocol ChatDataLayerProviding {
    func loadMessages(participants: [ChatParticipant]) -> [ChatMessage]
    func saveMessages(_ messages: [ChatMessage])
    func applyMessageMutations(_ mutations: [MessageMutation])
    func replaceIMCache(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile],
        selectedAccountID: UUID
    )
    func setValue(_ value: String?, forKey key: String)
    func value(forKey key: String) -> String?
}

final class SQLiteChatDataLayerAdapter: ChatDataLayerProviding {
    private let store: ChatSQLiteStore

    init(store: ChatSQLiteStore = .shared) {
        self.store = store
    }

    func loadMessages(participants: [ChatParticipant]) -> [ChatMessage] {
        store.loadMessages(participants: participants)
    }

    func saveMessages(_ messages: [ChatMessage]) {
        store.replaceMessages(messages)
    }

    func applyMessageMutations(_ mutations: [MessageMutation]) {
        store.applyMessageMutations(mutations)
    }

    func replaceIMCache(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile],
        selectedAccountID: UUID
    ) {
        store.replaceIMCache(
            participants: participants,
            messages: messages,
            contactCards: contactCards,
            selectedAccountID: selectedAccountID
        )
    }

    func setValue(_ value: String?, forKey key: String) {
        store.setString(value, forKey: key)
    }

    func value(forKey key: String) -> String? {
        store.string(forKey: key)
    }
}

enum ChatDataLayerDemo {
    static func makeAdapter() -> ChatDataLayerProviding {
        SQLiteChatDataLayerAdapter()
    }
}
