import Foundation

struct PersistedOpenApiIMSnapshot {
    let snapshot: OpenApiIMSnapshot
    let cacheDate: Date?
}

struct ConversationStateStore {
    func loadMessages(participants: [ChatParticipant]) -> [ChatMessage] {
        AppKits.registry.dataLayer.loadMessages(participants: participants)
    }

    func saveState(_ state: ChatDemoState) {
        let dataLayer = AppKits.registry.dataLayer
        dataLayer.saveMessages(state.messages)
        dataLayer.replaceIMCache(
            participants: state.participants,
            messages: state.messages,
            contactCards: state.contactCards,
            selectedAccountID: state.selectedAccountID
        )
    }

    func applyMessageMutations(_ mutations: [MessageMutation]) {
        guard !mutations.isEmpty else { return }
        AppKits.registry.dataLayer.applyMessageMutations(mutations)
    }

    func loadOpenApiDeliveryStatuses(messageIDs: Set<UUID>) -> [UUID: OpenApiDeliveryStatusRecord] {
        Dictionary(
            ChatSQLiteStore.shared.loadOpenApiDeliveryStatuses()
                .filter { messageIDs.isEmpty || messageIDs.contains($0.messageID) }
                .map { ($0.messageID, $0) },
            uniquingKeysWith: { current, _ in current }
        )
    }

    func loadOpenApiIMSnapshot(snapshotKey: String, updatedAtKey: String) -> PersistedOpenApiIMSnapshot? {
        guard let snapshot = ChatSQLiteStore.shared.codable(OpenApiIMSnapshot.self, forKey: snapshotKey),
              !snapshot.accounts.isEmpty
        else {
            return nil
        }
        let cacheDate = ChatSQLiteStore.shared.string(forKey: updatedAtKey)
            .flatMap(TimeInterval.init)
            .map(Date.init(timeIntervalSince1970:))
        return PersistedOpenApiIMSnapshot(snapshot: snapshot, cacheDate: cacheDate)
    }

    @discardableResult
    func persistOpenApiIMSnapshot(_ snapshot: OpenApiIMSnapshot, snapshotKey: String, updatedAtKey: String) -> Date {
        let now = Date()
        ChatSQLiteStore.shared.setCodableAsync(snapshot, forKey: snapshotKey) {
            ChatSQLiteStore.shared.setString(String(now.timeIntervalSince1970), forKey: updatedAtKey)
        }
        return now
    }

    func clearOpenApiIMSnapshot(snapshotKey: String, updatedAtKey: String) {
        ChatSQLiteStore.shared.setString(nil, forKey: snapshotKey)
        ChatSQLiteStore.shared.setString(nil, forKey: updatedAtKey)
    }
}
