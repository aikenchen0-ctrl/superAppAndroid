import Foundation

struct OpenApiChatConversationDescriptor: Codable, Hashable {
    let backendID: Int64
    let accountWxid: String
    let conversationWxid: String
    let conversationType: Int
    let displayName: String
    let displayAvatar: String
    let unreadCount: Int
    let messageCount: Int
    let isPinned: Bool
    let isMuted: Bool
    let lastMessageContent: String
    let lastMessageTime: Date?
    let updatedAt: Date?

    var isGroup: Bool {
        conversationWxid.lowercased().hasSuffix("@chatroom") || conversationType == 2
    }

    var participantID: UUID {
        OpenApiStableID.uuid(
            namespace: isGroup ? "openapi-chatroom" : "openapi-contact",
            key: conversationWxid
        )
    }
}

struct OpenApiChatMediaPayload: Codable, Hashable {
    let mediaType: Int
    let fileSize: Int64
    let fileExtension: String
    let uploadStatus: Int
    let url: String
}

struct OpenApiChatMessagePayload: Codable, Hashable {
    let messageID: Int64
    let messageServerID: Int64?
    let conversationBackendID: Int64?
    let senderWxid: String
    let receiverWxid: String
    let chatType: Int
    let messageType: Int
    let content: String
    let direction: Int
    let sendStatus: Int?
    let readStatus: Int?
    let isRevoked: Bool
    let isDeleted: Bool
    let localMessageID: String
    let clientMessageID: String
    let sentAt: Date?
    let receivedAt: Date?
    let readAt: Date?
    let revokedAt: Date?
    let createdAt: Date?
    let updatedAt: Date?
    let media: [OpenApiChatMediaPayload]
    let extensions: [String: String]
    let voiceText: String

    func stableID(accountWxid: String) -> UUID {
        let backendKey: String
        if messageID != 0 {
            backendKey = "message:\(messageID)"
        } else if let messageServerID {
            backendKey = "server:\(messageServerID)"
        } else if !clientMessageID.isEmpty {
            backendKey = "client:\(clientMessageID)"
        } else if !localMessageID.isEmpty {
            backendKey = "local:\(localMessageID)"
        } else {
            backendKey = "fallback:\(conversationBackendID ?? 0):\(createdAt?.timeIntervalSince1970 ?? 0):\(content)"
        }
        return OpenApiStableID.uuid(namespace: "openapi-chat-message", key: "\(accountWxid)|\(backendKey)")
    }
}

struct OpenApiChatBootstrapBatch: Codable {
    let accountWxid: String
    let accountID: String
    let deviceUuid: String
    let baselineSequence: Int64
    let conversations: [OpenApiChatConversationDescriptor]
}

struct OpenApiChatHistoryBatch {
    let accountWxid: String
    let conversationWxid: String
    let hasMore: Bool
    let nextCursor: String?
    let messages: [OpenApiChatMessagePayload]
}

struct OpenApiChatChangesBatch {
    let accountWxid: String
    let afterSequence: Int64
    let nextSequence: Int64
    let headSequence: Int64
    let minimumAvailableSequence: Int64
    let hasMore: Bool
    let messages: [OpenApiChatMessagePayload]
}

struct OpenApiChatSyncCheckpoint: Codable {
    var nextSequenceByAccountWxid: [String: Int64] = [:]
    var conversationsByAccountWxid: [String: [OpenApiChatConversationDescriptor]] = [:]
    var bootstrapConversationCountByAccountWxid: [String: Int] = [:]
    var lastErrorByAccountWxid: [String: String] = [:]
    var lastSuccessfulSyncAt: Date?
    var importedMessageCount = 0
}

enum OpenApiChatSynchronizer {
    static func loadBootstrap(
        for account: OpenApiWeChatAccountContext,
        conversationLimit: Int = 300
    ) async throws -> OpenApiChatBootstrapBatch {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/chat/bootstrap",
            query: accountQuery(for: account) + [
                URLQueryItem(name: "conversationLimit", value: "\(max(1, min(conversationLimit, 1_000)))")
            ]
        )
        let payload = dictionaryPayload(from: result.jsonObject)
        let conversations = array(payload["conversations"]).compactMap { raw -> OpenApiChatConversationDescriptor? in
            let wxid = string(raw["conversationWxid"])
            guard !wxid.isEmpty else { return nil }
            return OpenApiChatConversationDescriptor(
                backendID: int64(raw["id"]),
                accountWxid: account.wxid,
                conversationWxid: wxid,
                conversationType: int(raw["conversationType"]),
                displayName: string(raw["displayName"]),
                displayAvatar: string(raw["displayAvatar"]),
                unreadCount: max(0, int(raw["unreadCount"])),
                messageCount: max(0, int(raw["messageCount"])),
                isPinned: int(raw["isPinned"]) != 0,
                isMuted: int(raw["isMuted"]) != 0,
                lastMessageContent: string(raw["lastMessageContent"]),
                lastMessageTime: date(raw["lastMessageTime"]),
                updatedAt: date(raw["updatedAt"])
            )
        }
        return OpenApiChatBootstrapBatch(
            accountWxid: account.wxid,
            accountID: string(payload["accountId"]),
            deviceUuid: string(payload["deviceUuid"]),
            baselineSequence: int64(payload["baselineSequence"]),
            conversations: conversations
        )
    }

    static func loadHistory(
        for account: OpenApiWeChatAccountContext,
        conversation: OpenApiChatConversationDescriptor,
        cursor: String? = nil,
        pageSize: Int = 50
    ) async throws -> OpenApiChatHistoryBatch {
        do {
            return try await loadHistory(
                for: account,
                conversation: conversation,
                conversationIdentifier: conversation.conversationWxid,
                cursor: cursor,
                pageSize: pageSize
            )
        } catch OpenApiHTTPClient.ClientError.badStatus(let status, _) where [400, 404].contains(status) && conversation.backendID != 0 {
            return try await loadHistory(
                for: account,
                conversation: conversation,
                conversationIdentifier: "\(conversation.backendID)",
                cursor: cursor,
                pageSize: pageSize
            )
        }
    }

    static func loadChanges(
        for account: OpenApiWeChatAccountContext,
        afterSequence: Int64,
        limit: Int = 200
    ) async throws -> OpenApiChatChangesBatch {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/messages/changes",
            query: accountQuery(for: account) + [
                URLQueryItem(name: "afterSequence", value: "\(max(0, afterSequence))"),
                URLQueryItem(name: "limit", value: "\(max(1, min(limit, 500)))")
            ]
        )
        let payload = dictionaryPayload(from: result.jsonObject)
        let messages = array(payload["items"]).compactMap { item -> OpenApiChatMessagePayload? in
            guard let rawMessage = item["message"] as? [String: Any] else { return nil }
            return message(from: rawMessage)
        }
        return OpenApiChatChangesBatch(
            accountWxid: account.wxid,
            afterSequence: int64(payload["afterSequence"]),
            nextSequence: int64(payload["nextSequence"]),
            headSequence: int64(payload["headSequence"]),
            minimumAvailableSequence: int64(payload["minAvailableSequence"]),
            hasMore: bool(payload["hasMore"]),
            messages: messages
        )
    }

    private static func loadHistory(
        for account: OpenApiWeChatAccountContext,
        conversation: OpenApiChatConversationDescriptor,
        conversationIdentifier: String,
        cursor: String?,
        pageSize: Int
    ) async throws -> OpenApiChatHistoryBatch {
        var query = accountQuery(for: account) + [
            URLQueryItem(name: "conversationId", value: conversationIdentifier),
            URLQueryItem(name: "pageSize", value: "\(max(1, min(pageSize, 200)))")
        ]
        if let cursor, !cursor.isEmpty {
            query.append(URLQueryItem(name: "cursor", value: cursor))
        }
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/chat/history",
            query: query
        )
        let payload = dictionaryPayload(from: result.jsonObject)
        return OpenApiChatHistoryBatch(
            accountWxid: account.wxid,
            conversationWxid: conversation.conversationWxid,
            hasMore: bool(payload["hasMore"]),
            nextCursor: optionalString(payload["nextCursor"]),
            messages: array(payload["items"]).compactMap(message(from:))
        )
    }

    private static func accountQuery(for account: OpenApiWeChatAccountContext) -> [URLQueryItem] {
        var query = [URLQueryItem(name: "weChatId", value: account.wxid)]
        if !account.clientUuid.isEmpty {
            query.insert(URLQueryItem(name: "deviceUuid", value: account.clientUuid), at: 0)
        }
        return query
    }

    private static func message(from raw: [String: Any]) -> OpenApiChatMessagePayload? {
        let messageID = int64(raw["messageId"])
        let messageServerID = optionalInt64(raw["msgSvrId"])
        let localMessageID = string(raw["localMessageId"])
        let clientMessageID = string(raw["clientMsgId"])
        guard messageID != 0 || messageServerID != nil || !localMessageID.isEmpty || !clientMessageID.isEmpty else {
            return nil
        }

        let extensions = array(raw["extensions"]).reduce(into: [String: String]()) { result, item in
            let key = string(item["key"]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty else { return }
            result[key] = string(item["value"])
        }
        let media = array(raw["media"]).map {
            OpenApiChatMediaPayload(
                mediaType: int($0["mediaType"]),
                fileSize: int64($0["fileSize"]),
                fileExtension: string($0["fileExtension"]),
                uploadStatus: int($0["uploadStatus"]),
                url: [
                    $0["downloadUrl"],
                    $0["mediaUrl"],
                    $0["fileUrl"],
                    $0["imageUrl"],
                    $0["url"],
                    $0["path"]
                ].map { string($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                    .first { !$0.isEmpty } ?? ""
            )
        }
        let voice = raw["voiceText"] as? [String: Any]
        return OpenApiChatMessagePayload(
            messageID: messageID,
            messageServerID: messageServerID,
            conversationBackendID: optionalInt64(raw["conversationId"]),
            senderWxid: string(raw["senderWxid"]),
            receiverWxid: string(raw["receiverWxid"]),
            chatType: int(raw["chatType"]),
            messageType: int(raw["messageType"]),
            content: string(raw["content"]),
            direction: int(raw["direction"]),
            sendStatus: optionalInt(raw["sendStatus"]),
            readStatus: optionalInt(raw["readStatus"]),
            isRevoked: bool(raw["isRevoked"]),
            isDeleted: bool(raw["isDeleted"]),
            localMessageID: localMessageID,
            clientMessageID: clientMessageID,
            sentAt: date(raw["sentAt"]),
            receivedAt: date(raw["receivedAt"]),
            readAt: date(raw["readAt"]),
            revokedAt: date(raw["revokedAt"]),
            createdAt: date(raw["createdAt"]),
            updatedAt: date(raw["updatedAt"]),
            media: media,
            extensions: extensions,
            voiceText: string(voice?["text"])
        )
    }

    private static func dictionaryPayload(from object: Any?) -> [String: Any] {
        guard let root = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload"] {
            if let nested = root[key] as? [String: Any] { return nested }
        }
        return root
    }

    private static func array(_ value: Any?) -> [[String: Any]] {
        value as? [[String: Any]] ?? []
    }

    private static func string(_ value: Any?) -> String {
        if let value = value as? String { return value }
        if let value = value as? NSNumber { return value.stringValue }
        return ""
    }

    private static func optionalString(_ value: Any?) -> String? {
        let value = string(value).trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }

    private static func int(_ value: Any?) -> Int {
        if let value = value as? Int { return value }
        if let value = value as? NSNumber { return value.intValue }
        if let value = value as? String { return Int(value) ?? 0 }
        return 0
    }

    private static func optionalInt(_ value: Any?) -> Int? {
        if value == nil || value is NSNull { return nil }
        return int(value)
    }

    private static func int64(_ value: Any?) -> Int64 {
        if let value = value as? Int64 { return value }
        if let value = value as? NSNumber { return value.int64Value }
        if let value = value as? String { return Int64(value) ?? 0 }
        return 0
    }

    private static func optionalInt64(_ value: Any?) -> Int64? {
        if value == nil || value is NSNull { return nil }
        return int64(value)
    }

    private static func bool(_ value: Any?) -> Bool {
        if let value = value as? Bool { return value }
        if let value = value as? NSNumber { return value.boolValue }
        if let value = value as? String {
            return ["1", "true", "yes"].contains(value.lowercased())
        }
        return false
    }

    private static func date(_ value: Any?) -> Date? {
        if let value = value as? Date { return value }
        if let value = value as? NSNumber { return Date(timeIntervalSince1970: value.doubleValue) }
        guard let text = value as? String, !text.isEmpty else { return nil }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractional.date(from: text) { return date }
        return ISO8601DateFormatter().date(from: text)
    }
}
