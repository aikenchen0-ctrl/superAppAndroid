import Foundation
import UIKit
import SQLite3

final class ChatSQLiteStore {
    private struct IMCacheSnapshot {
        let participants: [ChatParticipant]
        let messages: [ChatMessage]
        let contactCards: [UUID: ContactCardProfile]
        let selectedAccountID: UUID
    }

    static let shared = ChatSQLiteStore()

    private let queue = DispatchQueue(label: "local.ios-float.chat.sqlite")
    private let snapshotLock = NSLock()
    private var pendingMessagesSnapshot: [ChatMessage]?
    private var isMessagesWriteScheduled = false
    private var pendingMessageMutationsByID: [UUID: MessageMutation] = [:]
    private var isMessageMutationWriteScheduled = false
    private var pendingIMCacheSnapshot: IMCacheSnapshot?
    private var isIMCacheWriteScheduled = false
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let databaseURL: URL
    private var db: OpaquePointer?

    private convenience init() {
        let supportURL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let directoryURL = supportURL.appendingPathComponent("ChatStore", isDirectory: true)
        try? FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        self.init(databaseURL: directoryURL.appendingPathComponent("messages.sqlite"))
    }

    init(databaseURL: URL) {
        self.databaseURL = databaseURL
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
        open()
        migrate()
    }

    deinit {
        if let db {
            sqlite3_close(db)
        }
    }

#if DEBUG
    func waitForPendingWrites() {
        queue.sync {}
    }
#endif

    func loadMessages(participants: [ChatParticipant]) -> [ChatMessage] {
        queue.sync {
            guard let db else { return [] }
            let participantByID = Dictionary(participants.map { ($0.id, $0) }, uniquingKeysWith: { current, _ in current })
            var statement: OpaquePointer?
            let sql = """
            SELECT id, conversation_id, type, sender_id, sender_json, body, detail,
                   is_outgoing, presentation, timestamp_text, sent_at, attachment_id, is_ai,
                   is_group_conversation, rich_elements_json, contact_card_json,
                   contact_card_account_id, quoted_message_id, merged_forward_json,
                   recipient_account_id, unanswered_metadata_json
            FROM messages
            WHERE is_deleted = 0
            ORDER BY sent_at ASC, rowid ASC;
            """
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return [] }
            defer { sqlite3_finalize(statement) }

            var messages: [ChatMessage] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                guard
                    let id = uuid(statement, 0),
                    let conversationID = uuid(statement, 1),
                    let type = ChatMessageType(rawValue: int(statement, 2)),
                    let senderID = uuid(statement, 3),
                    let sender = participantByID[senderID] ?? decode(ChatParticipant.self, from: text(statement, 4)),
                    let presentation = BubblePresentation(rawValue: text(statement, 8) ?? "")
                else { continue }

                let richElements = decode([BubbleRichElement].self, from: text(statement, 14)) ?? []
                let contactCard = decode(ContactCardProfile.self, from: text(statement, 15))
                let mergedForwardMessages = decode([ChatMessage].self, from: text(statement, 18)) ?? []
                let attachmentURL = attachmentURL(for: sqlite3_column_int64(statement, 11), db: db)

                messages.append(
                    ChatMessage(
                        id: id,
                        conversationID: conversationID,
                        type: type,
                        sender: sender,
                        body: text(statement, 5) ?? "",
                        detail: text(statement, 6) ?? "",
                        isOutgoing: bool(statement, 7),
                        presentation: presentation,
                        timestamp: text(statement, 9) ?? "",
                        sentAt: Date(timeIntervalSince1970: double(statement, 10)),
                        attachmentURL: attachmentURL,
                        isAI: bool(statement, 12),
                        isGroupConversation: bool(statement, 13),
                        richElements: richElements,
                        contactCard: contactCard,
                        contactCardAccountID: uuid(statement, 16),
                        quotedMessageID: uuid(statement, 17),
                        mergedForwardMessages: mergedForwardMessages,
                        recipientAccountID: uuid(statement, 19),
                        unansweredMetadata: decode(
                            UnansweredMessageMetadata.self,
                            from: text(statement, 20)
                        )
                    )
                )
            }
            return messages
        }
    }

    func replaceMessages(_ messages: [ChatMessage]) {
        PerformanceSignpost.event("SQLiteReplaceMessagesRequested")
        snapshotLock.lock()
        pendingMessagesSnapshot = messages
        guard !isMessagesWriteScheduled else {
            snapshotLock.unlock()
            return
        }
        isMessagesWriteScheduled = true
        snapshotLock.unlock()
        queue.async { [weak self] in
            self?.drainPendingMessagesSnapshots()
        }
    }

    func applyMessageMutations(_ mutations: [MessageMutation]) {
        guard !mutations.isEmpty else { return }
        PerformanceSignpost.event("SQLiteMessageMutationsRequested")
        snapshotLock.lock()
        for mutation in mutations {
            if case .update(let message) = mutation,
               case .insert = pendingMessageMutationsByID[mutation.messageID] {
                pendingMessageMutationsByID[mutation.messageID] = .insert(message)
            } else {
                pendingMessageMutationsByID[mutation.messageID] = mutation
            }
        }
        guard !isMessageMutationWriteScheduled else {
            snapshotLock.unlock()
            return
        }
        isMessageMutationWriteScheduled = true
        snapshotLock.unlock()
        queue.async { [weak self] in
            self?.drainPendingMessageMutations()
        }
    }

    func replaceIMCache(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile],
        selectedAccountID: UUID
    ) {
        PerformanceSignpost.event("SQLiteReplaceIMCacheRequested")
        let snapshot = IMCacheSnapshot(
            participants: participants,
            messages: messages,
            contactCards: contactCards,
            selectedAccountID: selectedAccountID
        )
        snapshotLock.lock()
        pendingIMCacheSnapshot = snapshot
        guard !isIMCacheWriteScheduled else {
            snapshotLock.unlock()
            return
        }
        isIMCacheWriteScheduled = true
        snapshotLock.unlock()
        queue.async { [weak self] in
            self?.drainPendingIMCacheSnapshots()
        }
    }

    private func drainPendingMessagesSnapshots() {
        while true {
            snapshotLock.lock()
            guard let snapshot = pendingMessagesSnapshot else {
                isMessagesWriteScheduled = false
                snapshotLock.unlock()
                return
            }
            pendingMessagesSnapshot = nil
            snapshotLock.unlock()
            replaceMessagesSync(snapshot)
        }
    }

    private func drainPendingMessageMutations() {
        while true {
            snapshotLock.lock()
            guard !pendingMessageMutationsByID.isEmpty else {
                isMessageMutationWriteScheduled = false
                snapshotLock.unlock()
                return
            }
            let mutations = Array(pendingMessageMutationsByID.values)
            pendingMessageMutationsByID.removeAll(keepingCapacity: true)
            snapshotLock.unlock()
            applyMessageMutationsSync(mutations)
        }
    }

    private func drainPendingIMCacheSnapshots() {
        while true {
            snapshotLock.lock()
            guard let snapshot = pendingIMCacheSnapshot else {
                isIMCacheWriteScheduled = false
                snapshotLock.unlock()
                return
            }
            pendingIMCacheSnapshot = nil
            snapshotLock.unlock()
            replaceIMCacheSync(
                participants: snapshot.participants,
                messages: snapshot.messages,
                contactCards: snapshot.contactCards,
                selectedAccountID: snapshot.selectedAccountID
            )
        }
    }

    func deleteMessages(ids: Set<UUID>) {
        guard !ids.isEmpty else { return }
        queue.async { [weak self] in
            guard let self, let db else { return }
            let signpostID = PerformanceSignpost.begin("SQLiteDeleteMessages")
            defer { PerformanceSignpost.end("SQLiteDeleteMessages", id: signpostID) }
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, "UPDATE messages SET is_deleted = 1 WHERE id = ?;", -1, &statement, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(statement) }
            for id in ids {
                sqlite3_bind_text(statement, 1, id.uuidString, -1, SQLITE_TRANSIENT)
                sqlite3_step(statement)
                sqlite3_reset(statement)
                sqlite3_clear_bindings(statement)
            }
            self.deleteOpenApiDeliveryStatusesSync(messageIDs: ids, db: db)
        }
    }

    func loadOpenApiDeliveryStatuses() -> [OpenApiDeliveryStatusRecord] {
        queue.sync {
            guard let db else { return [] }
            var statement: OpaquePointer?
            let sql = """
            SELECT message_id, conversation_id, task_ids_json, status, note, updated_at
            FROM openapi_delivery_status
            ORDER BY updated_at ASC;
            """
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return [] }
            defer { sqlite3_finalize(statement) }

            var records: [OpenApiDeliveryStatusRecord] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                guard let messageID = uuid(statement, 0),
                      let conversationID = uuid(statement, 1)
                else { continue }
                let taskIDs = decode([String].self, from: text(statement, 2)) ?? []
                records.append(
                    OpenApiDeliveryStatusRecord(
                        messageID: messageID,
                        conversationID: conversationID,
                        taskIDs: taskIDs,
                        stateRawValue: text(statement, 3) ?? "",
                        note: text(statement, 4) ?? "",
                        updatedAt: Date(timeIntervalSince1970: double(statement, 5))
                    )
                )
            }
            return records
        }
    }

    func upsertOpenApiDeliveryStatus(_ record: OpenApiDeliveryStatusRecord) {
        queue.async { [weak self] in
            guard let self, let db else { return }
            let signpostID = PerformanceSignpost.begin("SQLiteUpsertDeliveryStatus")
            defer { PerformanceSignpost.end("SQLiteUpsertDeliveryStatus", id: signpostID) }
            self.upsertOpenApiDeliveryStatusSync(record, db: db)
        }
    }

    func deleteOpenApiDeliveryStatuses(messageIDs: Set<UUID>) {
        guard !messageIDs.isEmpty else { return }
        queue.async { [weak self] in
            guard let self, let db else { return }
            let signpostID = PerformanceSignpost.begin("SQLiteDeleteDeliveryStatuses")
            defer { PerformanceSignpost.end("SQLiteDeleteDeliveryStatuses", id: signpostID) }
            self.deleteOpenApiDeliveryStatusesSync(messageIDs: messageIDs, db: db)
        }
    }

    func string(forKey key: String) -> String? {
        value(forKey: key)
    }

    func setString(_ value: String?, forKey key: String) {
        setValue(value, forKey: key)
    }

    func bool(forKey key: String) -> Bool? {
        value(forKey: key).flatMap { Int($0) }.map { $0 != 0 }
    }

    func setBool(_ value: Bool, forKey key: String) {
        setValue(value ? "1" : "0", forKey: key)
    }

    func stringArray(forKey key: String) -> [String]? {
        guard let json = value(forKey: key),
              let data = json.data(using: .utf8)
        else { return nil }
        return try? decoder.decode([String].self, from: data)
    }

    func setStringArray(_ value: [String], forKey key: String) {
        guard let data = try? encoder.encode(value),
              let json = String(data: data, encoding: .utf8)
        else { return }
        setValue(json, forKey: key)
    }

    func codable<T: Decodable>(_ type: T.Type, forKey key: String) -> T? {
        guard let json = value(forKey: key),
              let data = json.data(using: .utf8)
        else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    func setCodable<T: Encodable>(_ value: T, forKey key: String) {
        guard let data = try? encoder.encode(value),
              let json = String(data: data, encoding: .utf8)
        else { return }
        setValue(json, forKey: key)
    }

    func setCodableAsync<T: Encodable>(
        _ value: T,
        forKey key: String,
        completion: (() -> Void)? = nil
    ) {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            guard let data = try? encoder.encode(value),
                  let json = String(data: data, encoding: .utf8)
            else { return }
            self?.setString(json, forKey: key)
            completion?()
        }
    }

    private func open() {
        sqlite3_open(databaseURL.path, &db)
    }

    private func migrate() {
        guard let db else { return }
        execute("""
        CREATE TABLE IF NOT EXISTS messages (
            id TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL,
            type INTEGER NOT NULL,
            sender_id TEXT NOT NULL,
            sender_json TEXT,
            body TEXT NOT NULL,
            detail TEXT NOT NULL,
            is_outgoing INTEGER NOT NULL,
            presentation TEXT NOT NULL,
            timestamp_text TEXT NOT NULL,
            sent_at REAL NOT NULL,
            attachment_id INTEGER,
            is_ai INTEGER NOT NULL,
            is_group_conversation INTEGER NOT NULL,
            rich_elements_json TEXT,
            contact_card_json TEXT,
            contact_card_account_id TEXT,
            quoted_message_id TEXT,
            merged_forward_json TEXT,
            recipient_account_id TEXT,
            unanswered_metadata_json TEXT,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            updated_at REAL NOT NULL
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS attachments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            normalized_url TEXT NOT NULL UNIQUE,
            original_url TEXT NOT NULL,
            file_name TEXT,
            file_extension TEXT,
            created_at REAL NOT NULL
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS message_attachments (
            message_id TEXT NOT NULL,
            attachment_id INTEGER NOT NULL,
            role TEXT NOT NULL DEFAULT 'primary',
            PRIMARY KEY (message_id, attachment_id, role),
            FOREIGN KEY(message_id) REFERENCES messages(id),
            FOREIGN KEY(attachment_id) REFERENCES attachments(id)
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS app_state (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at REAL NOT NULL
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS accounts_cache (
            id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            initials TEXT NOT NULL,
            tint_color_json TEXT NOT NULL,
            contact_card_json TEXT,
            is_selected INTEGER NOT NULL DEFAULT 0,
            updated_at REAL NOT NULL
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS contacts_cache (
            id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            initials TEXT NOT NULL,
            tint_color_json TEXT NOT NULL,
            is_ai INTEGER NOT NULL DEFAULT 0,
            is_group INTEGER NOT NULL DEFAULT 0,
            remark TEXT,
            is_hidden INTEGER NOT NULL DEFAULT 0,
            updated_at REAL NOT NULL
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS conversations (
            id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            title TEXT NOT NULL,
            avatar_initials TEXT NOT NULL,
            owner_account_id TEXT,
            last_message_id TEXT,
            last_message_text TEXT,
            last_message_at REAL,
            unread_count INTEGER NOT NULL DEFAULT 0,
            is_pinned INTEGER NOT NULL DEFAULT 0,
            is_muted INTEGER NOT NULL DEFAULT 0,
            updated_at REAL NOT NULL,
            FOREIGN KEY(last_message_id) REFERENCES messages(id)
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS conversation_members (
            conversation_id TEXT NOT NULL,
            participant_id TEXT NOT NULL,
            role TEXT NOT NULL,
            display_name TEXT NOT NULL,
            is_current_user INTEGER NOT NULL DEFAULT 0,
            is_removed INTEGER NOT NULL DEFAULT 0,
            joined_at REAL,
            updated_at REAL NOT NULL,
            PRIMARY KEY (conversation_id, participant_id)
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS message_status (
            message_id TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL,
            status TEXT NOT NULL,
            is_read INTEGER NOT NULL DEFAULT 1,
            read_at REAL,
            delivered_at REAL,
            recalled_at REAL,
            deleted_at REAL,
            updated_at REAL NOT NULL,
            FOREIGN KEY(message_id) REFERENCES messages(id)
        );
        """, db: db)
        execute("""
        CREATE TABLE IF NOT EXISTS openapi_delivery_status (
            message_id TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL,
            task_ids_json TEXT NOT NULL,
            status TEXT NOT NULL,
            note TEXT NOT NULL,
            updated_at REAL NOT NULL,
            FOREIGN KEY(message_id) REFERENCES messages(id)
        );
        """, db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, sent_at);", db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_message_attachments_attachment ON message_attachments(attachment_id);", db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_conversations_owner ON conversations(owner_account_id, last_message_at);", db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_conversation_members_participant ON conversation_members(participant_id);", db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_message_status_conversation ON message_status(conversation_id, status);", db: db)
        execute("CREATE INDEX IF NOT EXISTS idx_openapi_delivery_conversation ON openapi_delivery_status(conversation_id, status);", db: db)
        addColumnIfNeeded("messages", column: "sender_json", definition: "TEXT", db: db)
        addColumnIfNeeded("messages", column: "recipient_account_id", definition: "TEXT", db: db)
        addColumnIfNeeded("messages", column: "unanswered_metadata_json", definition: "TEXT", db: db)
        repairMissingConversations(db: db)
    }

    private func applyMessageMutationsSync(_ mutations: [MessageMutation]) {
        guard let db, !mutations.isEmpty else { return }
        let signpostID = PerformanceSignpost.begin("SQLiteApplyMessageMutations")
        defer { PerformanceSignpost.end("SQLiteApplyMessageMutations", id: signpostID) }
        let now = Date().timeIntervalSince1970
        let upsertSQL = """
        INSERT INTO messages (
            id, conversation_id, type, sender_id, sender_json, body, detail, is_outgoing,
            presentation, timestamp_text, sent_at, attachment_id, is_ai,
            is_group_conversation, rich_elements_json, contact_card_json,
            contact_card_account_id, quoted_message_id, merged_forward_json,
            recipient_account_id, unanswered_metadata_json, is_deleted, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
        ON CONFLICT(id) DO UPDATE SET
            conversation_id = excluded.conversation_id,
            type = excluded.type,
            sender_id = excluded.sender_id,
            sender_json = excluded.sender_json,
            body = excluded.body,
            detail = excluded.detail,
            is_outgoing = excluded.is_outgoing,
            presentation = excluded.presentation,
            timestamp_text = excluded.timestamp_text,
            sent_at = excluded.sent_at,
            attachment_id = excluded.attachment_id,
            is_ai = excluded.is_ai,
            is_group_conversation = excluded.is_group_conversation,
            rich_elements_json = excluded.rich_elements_json,
            contact_card_json = excluded.contact_card_json,
            contact_card_account_id = excluded.contact_card_account_id,
            quoted_message_id = excluded.quoted_message_id,
            merged_forward_json = excluded.merged_forward_json,
            recipient_account_id = excluded.recipient_account_id,
            unanswered_metadata_json = excluded.unanswered_metadata_json,
            is_deleted = 0,
            updated_at = excluded.updated_at;
        """
        var upsertStatement: OpaquePointer?
        guard sqlite3_prepare_v2(db, upsertSQL, -1, &upsertStatement, nil) == SQLITE_OK else {
            return
        }
        defer { sqlite3_finalize(upsertStatement) }

        execute("BEGIN IMMEDIATE TRANSACTION;", db: db)
        var affectedConversationIDs = Set<UUID>()
        for mutation in mutations {
            affectedConversationIDs.insert(mutation.affectedConversationID)
            switch mutation {
            case .insert(let message), .update(let message):
                ensureConversationExists(for: message, updatedAt: now, db: db)
                execute(
                    "DELETE FROM message_attachments WHERE message_id = ?;",
                    values: [message.id.uuidString],
                    db: db
                )
                let attachmentID = message.attachmentURL.flatMap { upsertAttachment(url: $0, db: db) }
                bindMessage(message, attachmentID: attachmentID, updatedAt: now, to: upsertStatement)
                guard sqlite3_step(upsertStatement) == SQLITE_DONE else {
                    sqlite3_reset(upsertStatement)
                    sqlite3_clear_bindings(upsertStatement)
                    execute("ROLLBACK;", db: db)
                    return
                }
                sqlite3_reset(upsertStatement)
                sqlite3_clear_bindings(upsertStatement)
                if let attachmentID {
                    insertMessageAttachment(messageID: message.id, attachmentID: attachmentID, role: "primary", db: db)
                }
                indexNestedAttachments(in: message.mergedForwardMessages, ownerMessageID: message.id, db: db)
                upsertMessageStatus(message, updatedAt: now, db: db)

            case .delete(let messageID, let conversationID):
                execute(
                    "UPDATE messages SET is_deleted = 1, updated_at = ? WHERE id = ?;",
                    values: [now, messageID.uuidString],
                    db: db
                )
                execute(
                    "DELETE FROM message_attachments WHERE message_id = ?;",
                    values: [messageID.uuidString],
                    db: db
                )
                execute(
                    """
                    UPDATE message_status
                    SET status = 'deleted', is_read = 1, deleted_at = ?, updated_at = ?
                    WHERE message_id = ? AND conversation_id = ?;
                    """,
                    values: [now, now, messageID.uuidString, conversationID.uuidString],
                    db: db
                )
                deleteOpenApiDeliveryStatusesSync(messageIDs: [messageID], db: db)
            }
        }
        for conversationID in affectedConversationIDs {
            refreshConversationSummary(conversationID: conversationID, updatedAt: now, db: db)
        }
        execute("COMMIT;", db: db)
    }

    private func ensureConversationExists(
        for message: ChatMessage,
        updatedAt: TimeInterval,
        db: OpaquePointer
    ) {
        let type = message.isAI ? "ai" : (message.isGroupConversation ? "group" : "direct")
        let fallbackTitle = message.isGroupConversation
            ? "群聊"
            : (message.sender.id == message.conversationID ? message.sender.displayName : message.conversationID.uuidString)
        let fallbackInitials = message.isGroupConversation
            ? "群"
            : (message.sender.id == message.conversationID ? message.sender.initials : "友")
        let ownerAccountID = message.isOutgoing ? message.sender.id : message.recipientAccountID
        execute(
            """
            INSERT OR IGNORE INTO conversations
            (id, type, title, avatar_initials, owner_account_id, last_message_id,
             last_message_text, last_message_at, unread_count, is_pinned, is_muted, updated_at)
            VALUES (?, ?, ?, ?, ?, NULL, '', NULL, 0, 0, 0, ?);
            """,
            values: [
                message.conversationID.uuidString,
                type,
                fallbackTitle,
                fallbackInitials,
                ownerAccountID?.uuidString as Any,
                updatedAt
            ],
            db: db
        )
    }

    private func repairMissingConversations(db: OpaquePointer) {
        let now = Date().timeIntervalSince1970
        execute(
            """
            INSERT OR IGNORE INTO conversations
            (id, type, title, avatar_initials, owner_account_id, last_message_id,
             last_message_text, last_message_at, unread_count, is_pinned, is_muted, updated_at)
            SELECT
                latest.conversation_id,
                CASE
                    WHEN latest.is_ai = 1 THEN 'ai'
                    WHEN latest.is_group_conversation = 1 THEN 'group'
                    ELSE 'direct'
                END,
                COALESCE(
                    (SELECT display_name FROM contacts_cache WHERE id = latest.conversation_id),
                    json_extract(latest.sender_json, '$.displayName'),
                    latest.conversation_id
                ),
                COALESCE(
                    (SELECT initials FROM contacts_cache WHERE id = latest.conversation_id),
                    json_extract(latest.sender_json, '$.initials'),
                    CASE WHEN latest.is_group_conversation = 1 THEN '群' ELSE '友' END
                ),
                CASE WHEN latest.is_outgoing = 1 THEN latest.sender_id ELSE latest.recipient_account_id END,
                latest.id,
                CASE WHEN latest.body = '' THEN CAST(latest.type AS TEXT) ELSE latest.body END,
                latest.sent_at,
                (SELECT COUNT(*) FROM messages unread
                 WHERE unread.conversation_id = latest.conversation_id
                   AND unread.is_deleted = 0 AND unread.is_outgoing = 0),
                0,
                0,
                ?
            FROM messages latest
            WHERE latest.is_deleted = 0
              AND latest.rowid = (
                  SELECT candidate.rowid FROM messages candidate
                  WHERE candidate.conversation_id = latest.conversation_id
                    AND candidate.is_deleted = 0
                  ORDER BY candidate.sent_at DESC, candidate.rowid DESC LIMIT 1
              )
              AND NOT EXISTS (
                  SELECT 1 FROM conversations existing
                  WHERE existing.id = latest.conversation_id
              );
            """,
            values: [now],
            db: db
        )
    }

    func hasConversation(id: UUID) -> Bool {
        queue.sync {
            guard let db else { return false }
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, "SELECT 1 FROM conversations WHERE id = ? LIMIT 1;", -1, &statement, nil) == SQLITE_OK else {
                return false
            }
            defer { sqlite3_finalize(statement) }
            sqlite3_bind_text(statement, 1, id.uuidString, -1, SQLITE_TRANSIENT)
            return sqlite3_step(statement) == SQLITE_ROW
        }
    }

    private func bindMessage(
        _ message: ChatMessage,
        attachmentID: Int64?,
        updatedAt: TimeInterval,
        to statement: OpaquePointer?
    ) {
        sqlite3_bind_text(statement, 1, message.id.uuidString, -1, SQLITE_TRANSIENT)
        sqlite3_bind_text(statement, 2, message.conversationID.uuidString, -1, SQLITE_TRANSIENT)
        sqlite3_bind_int(statement, 3, Int32(message.type.rawValue))
        sqlite3_bind_text(statement, 4, message.sender.id.uuidString, -1, SQLITE_TRANSIENT)
        bindJSON(message.sender, to: statement, index: 5)
        sqlite3_bind_text(statement, 6, message.body, -1, SQLITE_TRANSIENT)
        sqlite3_bind_text(statement, 7, message.detail, -1, SQLITE_TRANSIENT)
        sqlite3_bind_int(statement, 8, message.isOutgoing ? 1 : 0)
        sqlite3_bind_text(statement, 9, message.presentation.rawValue, -1, SQLITE_TRANSIENT)
        sqlite3_bind_text(statement, 10, message.timestamp, -1, SQLITE_TRANSIENT)
        sqlite3_bind_double(statement, 11, message.sentAt.timeIntervalSince1970)
        if let attachmentID {
            sqlite3_bind_int64(statement, 12, attachmentID)
        } else {
            sqlite3_bind_null(statement, 12)
        }
        sqlite3_bind_int(statement, 13, message.isAI ? 1 : 0)
        sqlite3_bind_int(statement, 14, message.isGroupConversation ? 1 : 0)
        bindJSON(message.richElements, to: statement, index: 15)
        bindJSON(message.contactCard, to: statement, index: 16)
        bindOptionalUUID(message.contactCardAccountID, to: statement, index: 17)
        bindOptionalUUID(message.quotedMessageID, to: statement, index: 18)
        bindJSON(message.mergedForwardMessages, to: statement, index: 19)
        bindOptionalUUID(message.recipientAccountID, to: statement, index: 20)
        bindJSON(message.unansweredMetadata, to: statement, index: 21)
        sqlite3_bind_double(statement, 22, updatedAt)
    }

    private func upsertMessageStatus(_ message: ChatMessage, updatedAt: TimeInterval, db: OpaquePointer) {
        let deliveredAt: Any = message.isOutgoing
            ? message.sentAt.timeIntervalSince1970
            : Optional<Double>.none as Any
        execute(
            """
            INSERT INTO message_status
            (message_id, conversation_id, status, is_read, read_at, delivered_at,
             recalled_at, deleted_at, updated_at)
            VALUES (?, ?, ?, 1, ?, ?, NULL, NULL, ?)
            ON CONFLICT(message_id) DO UPDATE SET
                conversation_id = excluded.conversation_id,
                status = excluded.status,
                is_read = excluded.is_read,
                read_at = excluded.read_at,
                delivered_at = excluded.delivered_at,
                recalled_at = NULL,
                deleted_at = NULL,
                updated_at = excluded.updated_at;
            """,
            values: [
                message.id.uuidString,
                message.conversationID.uuidString,
                messageStatus(for: message),
                message.sentAt.timeIntervalSince1970,
                deliveredAt,
                updatedAt
            ],
            db: db
        )
    }

    private func refreshConversationSummary(
        conversationID: UUID,
        updatedAt: TimeInterval,
        db: OpaquePointer
    ) {
        execute(
            """
            UPDATE conversations
            SET last_message_id = (
                    SELECT id FROM messages
                    WHERE conversation_id = ? AND is_deleted = 0
                    ORDER BY sent_at DESC, rowid DESC LIMIT 1
                ),
                last_message_text = (
                    SELECT CASE WHEN body = '' THEN CAST(type AS TEXT) ELSE body END
                    FROM messages
                    WHERE conversation_id = ? AND is_deleted = 0
                    ORDER BY sent_at DESC, rowid DESC LIMIT 1
                ),
                last_message_at = (
                    SELECT sent_at FROM messages
                    WHERE conversation_id = ? AND is_deleted = 0
                    ORDER BY sent_at DESC, rowid DESC LIMIT 1
                ),
                unread_count = (
                    SELECT COUNT(*) FROM messages
                    WHERE conversation_id = ? AND is_deleted = 0 AND is_outgoing = 0
                ),
                updated_at = ?
            WHERE id = ?;
            """,
            values: [
                conversationID.uuidString,
                conversationID.uuidString,
                conversationID.uuidString,
                conversationID.uuidString,
                updatedAt,
                conversationID.uuidString
            ],
            db: db
        )
    }

    private func replaceMessagesSync(_ messages: [ChatMessage]) {
        guard let db else { return }
        let signpostID = PerformanceSignpost.begin("SQLiteReplaceMessages")
        defer { PerformanceSignpost.end("SQLiteReplaceMessages", id: signpostID) }
        execute("BEGIN IMMEDIATE TRANSACTION;", db: db)
        execute("DELETE FROM message_attachments;", db: db)
        execute("DELETE FROM messages;", db: db)

        var statement: OpaquePointer?
        let sql = """
        INSERT OR REPLACE INTO messages (
            id, conversation_id, type, sender_id, sender_json, body, detail, is_outgoing,
            presentation, timestamp_text, sent_at, attachment_id, is_ai,
            is_group_conversation, rich_elements_json, contact_card_json,
            contact_card_account_id, quoted_message_id, merged_forward_json,
            recipient_account_id, unanswered_metadata_json, is_deleted, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?);
        """
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            execute("ROLLBACK;", db: db)
            return
        }
        defer { sqlite3_finalize(statement) }

        for message in messages {
            let attachmentID = message.attachmentURL.flatMap { upsertAttachment(url: $0, db: db) }
            sqlite3_bind_text(statement, 1, message.id.uuidString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 2, message.conversationID.uuidString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_int(statement, 3, Int32(message.type.rawValue))
            sqlite3_bind_text(statement, 4, message.sender.id.uuidString, -1, SQLITE_TRANSIENT)
            bindJSON(message.sender, to: statement, index: 5)
            sqlite3_bind_text(statement, 6, message.body, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 7, message.detail, -1, SQLITE_TRANSIENT)
            sqlite3_bind_int(statement, 8, message.isOutgoing ? 1 : 0)
            sqlite3_bind_text(statement, 9, message.presentation.rawValue, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 10, message.timestamp, -1, SQLITE_TRANSIENT)
            sqlite3_bind_double(statement, 11, message.sentAt.timeIntervalSince1970)
            if let attachmentID {
                sqlite3_bind_int64(statement, 12, attachmentID)
            } else {
                sqlite3_bind_null(statement, 12)
            }
            sqlite3_bind_int(statement, 13, message.isAI ? 1 : 0)
            sqlite3_bind_int(statement, 14, message.isGroupConversation ? 1 : 0)
            bindJSON(message.richElements, to: statement, index: 15)
            bindJSON(message.contactCard, to: statement, index: 16)
            bindOptionalUUID(message.contactCardAccountID, to: statement, index: 17)
            bindOptionalUUID(message.quotedMessageID, to: statement, index: 18)
            bindJSON(message.mergedForwardMessages, to: statement, index: 19)
            bindOptionalUUID(message.recipientAccountID, to: statement, index: 20)
            bindJSON(message.unansweredMetadata, to: statement, index: 21)
            sqlite3_bind_double(statement, 22, Date().timeIntervalSince1970)
            sqlite3_step(statement)
            sqlite3_reset(statement)
            sqlite3_clear_bindings(statement)

            if let attachmentID {
                insertMessageAttachment(messageID: message.id, attachmentID: attachmentID, role: "primary", db: db)
            }
            indexNestedAttachments(in: message.mergedForwardMessages, ownerMessageID: message.id, db: db)
        }

        execute("COMMIT;", db: db)
    }

    private func replaceIMCacheSync(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile],
        selectedAccountID: UUID
    ) {
        guard let db else { return }
        let signpostID = PerformanceSignpost.begin("SQLiteReplaceIMCache")
        defer { PerformanceSignpost.end("SQLiteReplaceIMCache", id: signpostID) }
        let now = Date().timeIntervalSince1970
        let participantByID = Dictionary(participants.map { ($0.id, $0) }, uniquingKeysWith: { current, _ in current })
        let accounts = participants.filter(\.isCurrentUser)
        let accountIDs = Set(accounts.map(\.id))
        let conversations = Dictionary(grouping: messages) { $0.conversationID }

        execute("BEGIN IMMEDIATE TRANSACTION;", db: db)
        execute("DELETE FROM message_status;", db: db)
        execute("DELETE FROM conversation_members;", db: db)
        execute("DELETE FROM conversations;", db: db)
        execute("DELETE FROM contacts_cache;", db: db)
        execute("DELETE FROM accounts_cache;", db: db)

        for account in accounts {
            execute(
                """
                INSERT OR REPLACE INTO accounts_cache
                (id, display_name, initials, tint_color_json, contact_card_json, is_selected, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """,
                values: [
                    account.id.uuidString,
                    contactCards[account.id]?.displayName ?? account.displayName,
                    account.initials,
                    colorJSON(account.tintColor),
                    jsonString(contactCards[account.id]) as Any,
                    account.id == selectedAccountID,
                    now
                ],
                db: db
            )
        }

        for contact in participants where !contact.isCurrentUser {
            let isGroup = isGroupLike(contact) || messages.contains { $0.conversationID == contact.id && $0.isGroupConversation }
            execute(
                """
                INSERT OR REPLACE INTO contacts_cache
                (id, display_name, initials, tint_color_json, is_ai, is_group, remark, is_hidden, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                values: [
                    contact.id.uuidString,
                    contact.displayName,
                    contact.initials,
                    colorJSON(contact.tintColor),
                    contact.isAIAccount,
                    isGroup,
                    "",
                    false,
                    now
                ],
                db: db
            )
        }

        for (conversationID, conversationMessages) in conversations {
            guard let latest = conversationMessages.max(by: { lhs, rhs in
                if lhs.sentAt == rhs.sentAt { return lhs.id.uuidString < rhs.id.uuidString }
                return lhs.sentAt < rhs.sentAt
            }) else { continue }
            let conversationParticipant = participantByID[conversationID]
            let isGroup = conversationParticipant.map(isGroupLike) == true || latest.isGroupConversation
            let isAI = conversationParticipant?.isAIAccount == true
            let type = isAI ? "ai" : (isGroup ? "group" : "direct")
            let title = conversationParticipant?.displayName ?? latest.sender.displayName
            let initials = conversationParticipant?.initials ?? latest.sender.initials
            let ownerAccountID = ownerAccountID(
                messages: conversationMessages,
                accountIDs: accountIDs
            )
            let unreadCount = conversationMessages.filter { !$0.isOutgoing }.count

            execute(
                """
                INSERT OR REPLACE INTO conversations
                (id, type, title, avatar_initials, owner_account_id, last_message_id,
                 last_message_text, last_message_at, unread_count, is_pinned, is_muted, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                values: [
                    conversationID.uuidString,
                    type,
                    title,
                    initials,
                    ownerAccountID?.uuidString as Any,
                    latest.id.uuidString,
                    latest.body.isEmpty ? latest.type.title : latest.body,
                    latest.sentAt.timeIntervalSince1970,
                    unreadCount,
                    isAI,
                    false,
                    now
                ],
                db: db
            )

            let memberIDs = memberIDsForConversation(
                conversationID: conversationID,
                messages: conversationMessages,
                participantByID: participantByID,
                accounts: accounts,
                isGroup: isGroup
            )
            for memberID in memberIDs {
                guard let member = participantByID[memberID] else { continue }
                let role: String
                if member.isCurrentUser {
                    role = "account"
                } else if member.id == conversationID {
                    role = isGroup ? "group" : "contact"
                } else {
                    role = "member"
                }
                execute(
                    """
                    INSERT OR REPLACE INTO conversation_members
                    (conversation_id, participant_id, role, display_name, is_current_user,
                     is_removed, joined_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                    """,
                    values: [
                        conversationID.uuidString,
                        member.id.uuidString,
                        role,
                        member.displayName,
                        member.isCurrentUser,
                        false,
                        conversationMessages.first?.sentAt.timeIntervalSince1970 as Any,
                        now
                    ],
                    db: db
                )
            }
        }

        for message in messages {
            let deliveredAt: Any = message.isOutgoing ? message.sentAt.timeIntervalSince1970 : Optional<Double>.none as Any
            let recalledAt: Any = Optional<Double>.none as Any
            let deletedAt: Any = Optional<Double>.none as Any
            execute(
                """
                INSERT OR REPLACE INTO message_status
                (message_id, conversation_id, status, is_read, read_at, delivered_at,
                 recalled_at, deleted_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                values: [
                    message.id.uuidString,
                    message.conversationID.uuidString,
                    messageStatus(for: message),
                    true,
                    message.sentAt.timeIntervalSince1970,
                    deliveredAt,
                    recalledAt,
                    deletedAt,
                    now
                ],
                db: db
            )
        }

        execute("COMMIT;", db: db)
    }

    private func upsertOpenApiDeliveryStatusSync(_ record: OpenApiDeliveryStatusRecord, db: OpaquePointer) {
        execute(
            """
            INSERT OR REPLACE INTO openapi_delivery_status
            (message_id, conversation_id, task_ids_json, status, note, updated_at)
            VALUES (?, ?, ?, ?, ?, ?);
            """,
            values: [
                record.messageID.uuidString,
                record.conversationID.uuidString,
                jsonString(record.taskIDs) ?? "[]",
                record.stateRawValue,
                record.note,
                record.updatedAt.timeIntervalSince1970
            ],
            db: db
        )
    }

    private func deleteOpenApiDeliveryStatusesSync(messageIDs: Set<UUID>, db: OpaquePointer) {
        guard !messageIDs.isEmpty else { return }
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "DELETE FROM openapi_delivery_status WHERE message_id = ?;", -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        for messageID in messageIDs {
            sqlite3_bind_text(statement, 1, messageID.uuidString, -1, SQLITE_TRANSIENT)
            sqlite3_step(statement)
            sqlite3_reset(statement)
            sqlite3_clear_bindings(statement)
        }
    }

    private func ownerAccountID(
        messages: [ChatMessage],
        accountIDs: Set<UUID>
    ) -> UUID? {
        var explicitOwnerIDs = Set(
            messages.compactMap(\.recipientAccountID).filter(accountIDs.contains)
        )
        for message in messages where message.isOutgoing && accountIDs.contains(message.sender.id) {
            explicitOwnerIDs.insert(message.sender.id)
        }
        return explicitOwnerIDs.count == 1 ? explicitOwnerIDs.first : nil
    }

    private func memberIDsForConversation(
        conversationID: UUID,
        messages: [ChatMessage],
        participantByID: [UUID: ChatParticipant],
        accounts: [ChatParticipant],
        isGroup: Bool
    ) -> [UUID] {
        var ids: [UUID] = []
        func append(_ id: UUID) {
            guard !ids.contains(id) else { return }
            ids.append(id)
        }

        append(conversationID)
        messages.forEach { append($0.sender.id) }
        messages.compactMap(\.recipientAccountID).forEach(append)
        if isGroup {
            accounts.forEach { account in
                if messages.contains(where: { $0.sender.id == account.id || $0.recipientAccountID == account.id }) {
                    append(account.id)
                }
            }
        } else if let account = accounts.first(where: { account in
            messages.contains { $0.sender.id == account.id || $0.recipientAccountID == account.id }
        }) {
            append(account.id)
        }
        return ids.filter { participantByID[$0] != nil }
    }

    private func messageStatus(for message: ChatMessage) -> String {
        if message.type == .system {
            return "system"
        }
        if message.body.contains("已撤回") || message.detail.contains("已撤回") {
            return "recalled"
        }
        return message.isOutgoing ? "sent" : "received"
    }

    private func isGroupLike(_ participant: ChatParticipant) -> Bool {
        participant.kind == .group
    }

    private func colorJSON(_ color: UIColor) -> String {
        let data = try? encoder.encode(ChatParticipant(
            id: UUID(),
            displayName: "",
            tintColor: color,
            initials: "",
            isCurrentUser: false
        ))
        guard let data,
              let json = String(data: data, encoding: .utf8),
              let range = json.range(of: "\"tintColor\":")
        else { return "[1,1,1,1]" }
        let tail = json[range.upperBound...]
        guard let start = tail.firstIndex(of: "["),
              let end = tail[start...].firstIndex(of: "]")
        else { return "[1,1,1,1]" }
        return String(tail[start...end])
    }

    private func jsonString<T: Encodable>(_ value: T?) -> String? {
        guard let value,
              let data = try? encoder.encode(value)
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func indexNestedAttachments(in messages: [ChatMessage], ownerMessageID: UUID, db: OpaquePointer) {
        for message in messages {
            if let url = message.attachmentURL,
               let attachmentID = upsertAttachment(url: url, db: db) {
                insertMessageAttachment(messageID: ownerMessageID, attachmentID: attachmentID, role: "merged", db: db)
            }
            indexNestedAttachments(in: message.mergedForwardMessages, ownerMessageID: ownerMessageID, db: db)
        }
    }

    private func upsertAttachment(url: URL, db: OpaquePointer) -> Int64? {
        let normalized = normalizedURL(url)
        execute(
            """
            INSERT OR IGNORE INTO attachments
            (normalized_url, original_url, file_name, file_extension, created_at)
            VALUES (?, ?, ?, ?, ?);
            """,
            values: [
                normalized,
                url.absoluteString,
                url.lastPathComponent,
                url.pathExtension.lowercased(),
                Date().timeIntervalSince1970
            ],
            db: db
        )
        return attachmentID(normalizedURL: normalized, db: db)
    }

    private func insertMessageAttachment(messageID: UUID, attachmentID: Int64, role: String, db: OpaquePointer) {
        execute(
            "INSERT OR IGNORE INTO message_attachments (message_id, attachment_id, role) VALUES (?, ?, ?);",
            values: [messageID.uuidString, attachmentID, role],
            db: db
        )
    }

    private func attachmentID(normalizedURL: String, db: OpaquePointer) -> Int64? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT id FROM attachments WHERE normalized_url = ? LIMIT 1;", -1, &statement, nil) == SQLITE_OK else { return nil }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_text(statement, 1, normalizedURL, -1, SQLITE_TRANSIENT)
        guard sqlite3_step(statement) == SQLITE_ROW else { return nil }
        return sqlite3_column_int64(statement, 0)
    }

    private func attachmentURL(for id: Int64, db: OpaquePointer) -> URL? {
        guard id > 0 else { return nil }
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT original_url FROM attachments WHERE id = ? LIMIT 1;", -1, &statement, nil) == SQLITE_OK else { return nil }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_int64(statement, 1, id)
        guard sqlite3_step(statement) == SQLITE_ROW,
              let value = text(statement, 0)
        else { return nil }
        return URL(string: value) ?? URL(fileURLWithPath: value)
    }

    private func normalizedURL(_ url: URL) -> String {
        if url.isFileURL {
            return URL(fileURLWithPath: url.path).standardizedFileURL.path
        }
        return url.absoluteString.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func value(forKey key: String) -> String? {
        queue.sync {
            guard let db else { return nil }
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, "SELECT value FROM app_state WHERE key = ? LIMIT 1;", -1, &statement, nil) == SQLITE_OK else { return nil }
            defer { sqlite3_finalize(statement) }
            sqlite3_bind_text(statement, 1, key, -1, SQLITE_TRANSIENT)
            guard sqlite3_step(statement) == SQLITE_ROW else { return nil }
            return text(statement, 0)
        }
    }

    private func setValue(_ value: String?, forKey key: String) {
        queue.async { [weak self] in
            guard let self, let db else { return }
            let signpostID = PerformanceSignpost.begin("SQLiteSetValue")
            defer { PerformanceSignpost.end("SQLiteSetValue", id: signpostID) }
            if let value {
                execute(
                    "INSERT OR REPLACE INTO app_state (key, value, updated_at) VALUES (?, ?, ?);",
                    values: [key, value, Date().timeIntervalSince1970],
                    db: db
                )
            } else {
                execute("DELETE FROM app_state WHERE key = ?;", values: [key], db: db)
            }
        }
    }

    private func execute(_ sql: String, db: OpaquePointer) {
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    private func addColumnIfNeeded(_ table: String, column: String, definition: String, db: OpaquePointer) {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "PRAGMA table_info(\(table));", -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }

        while sqlite3_step(statement) == SQLITE_ROW {
            if text(statement, 1) == column {
                return
            }
        }
        execute("ALTER TABLE \(table) ADD COLUMN \(column) \(definition);", db: db)
    }

    private func execute(_ sql: String, values: [Any], db: OpaquePointer) {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(statement) }
        for (offset, value) in values.enumerated() {
            bind(value, to: statement, index: Int32(offset + 1))
        }
        sqlite3_step(statement)
    }

    private func bind(_ value: Any, to statement: OpaquePointer?, index: Int32) {
        switch value {
        case let value as String:
            sqlite3_bind_text(statement, index, value, -1, SQLITE_TRANSIENT)
        case let value as Int64:
            sqlite3_bind_int64(statement, index, value)
        case let value as Int:
            sqlite3_bind_int(statement, index, Int32(value))
        case let value as Double:
            sqlite3_bind_double(statement, index, value)
        case let value as Bool:
            sqlite3_bind_int(statement, index, value ? 1 : 0)
        default:
            sqlite3_bind_null(statement, index)
        }
    }

    private func bindOptionalUUID(_ uuid: UUID?, to statement: OpaquePointer?, index: Int32) {
        if let uuid {
            sqlite3_bind_text(statement, index, uuid.uuidString, -1, SQLITE_TRANSIENT)
        } else {
            sqlite3_bind_null(statement, index)
        }
    }

    private func bindJSON<T: Encodable>(_ value: T?, to statement: OpaquePointer?, index: Int32) {
        guard let value,
              let data = try? encoder.encode(value),
              let json = String(data: data, encoding: .utf8)
        else {
            sqlite3_bind_null(statement, index)
            return
        }
        sqlite3_bind_text(statement, index, json, -1, SQLITE_TRANSIENT)
    }

    private func decode<T: Decodable>(_ type: T.Type, from json: String?) -> T? {
        guard let json,
              let data = json.data(using: .utf8)
        else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    private func text(_ statement: OpaquePointer?, _ index: Int32) -> String? {
        guard let cString = sqlite3_column_text(statement, index) else { return nil }
        return String(cString: cString)
    }

    private func uuid(_ statement: OpaquePointer?, _ index: Int32) -> UUID? {
        text(statement, index).flatMap(UUID.init(uuidString:))
    }

    private func int(_ statement: OpaquePointer?, _ index: Int32) -> Int {
        Int(sqlite3_column_int(statement, index))
    }

    private func bool(_ statement: OpaquePointer?, _ index: Int32) -> Bool {
        sqlite3_column_int(statement, index) != 0
    }

    private func double(_ statement: OpaquePointer?, _ index: Int32) -> Double {
        sqlite3_column_double(statement, index)
    }
}

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
