import Foundation

struct OpenApiSocialAccount: Hashable {
    let deviceUUID: String
    let weChatID: String

    init(deviceUUID: String, weChatID: String) {
        self.deviceUUID = deviceUUID.trimmingCharacters(in: .whitespacesAndNewlines)
        self.weChatID = weChatID.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

struct OpenApiFriendAddOptions: Hashable {
    var verificationMessage = "你好"
    var remark = ""
    var labelNames: [String] = []
    var sourceChannel = "ios-app"
    var customerLevel = ""
    var profileKey = ""
    var verificationImageURL = ""
}

enum OpenApiFriendRequestDecision: Int {
    case accept = 1
    case reject = 2
}

enum OpenApiSocialManagementError: LocalizedError, Equatable {
    case missingField(String)
    case invalidChatRoomID(String)
    case invalidFriendID(String)
    case friendNotFound(String)

    var errorDescription: String? {
        switch self {
        case .missingField(let field): return "缺少必填字段：\(field)"
        case .invalidChatRoomID(let value): return "群聊 ID 无效：\(value)"
        case .invalidFriendID(let value): return "好友 wxid 无效：\(value)"
        case .friendNotFound(let value): return "未找到可添加的好友 wxid：\(value)"
        }
    }
}

struct OpenApiSocialManagementService {
    typealias Request = (
        _ method: String,
        _ path: String,
        _ query: [URLQueryItem],
        _ body: Any?
    ) async throws -> OpenApiHTTPResult
    typealias TaskValidator = (_ result: OpenApiHTTPResult, _ action: String) async throws -> String
    typealias Sleeper = (_ nanoseconds: UInt64) async throws -> Void

    private let request: Request
    private let validateTask: TaskValidator
    private let sleep: Sleeper

    init(
        request: @escaping Request = { method, path, query, body in
            try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
        },
        validateTask: @escaping TaskValidator = { result, action in
            try await OpenApiHTTPClient.validateTaskResult(result, action: action)
        },
        sleep: @escaping Sleeper = { try await Task.sleep(nanoseconds: $0) }
    ) {
        self.request = request
        self.validateTask = validateTask
        self.sleep = sleep
    }

    // MARK: - Group management

    /// Read a page of contacts for the selected WeChat account.
    /// The synchronizer owns response mapping; this service owns the endpoint contract.
    func listContacts(
        account: OpenApiSocialAccount,
        page: Int,
        pageSize: Int,
        onlyFriends: Bool = true,
        includeProfile: Bool = true,
        includeDeleted: Bool = false
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        return try await request(
            "GET",
            "/openapi/v1/contacts",
            accountQuery(
                account,
                additional: [
                    URLQueryItem(name: "page", value: "\(max(page, 1))"),
                    URLQueryItem(name: "pageSize", value: "\(min(max(pageSize, 1), 100))"),
                    URLQueryItem(name: "includeDeleted", value: includeDeleted ? "true" : "false"),
                    URLQueryItem(name: "onlyFriends", value: onlyFriends ? "true" : "false"),
                    URLQueryItem(name: "includeProfile", value: includeProfile ? "true" : "false")
                ]
            ),
            nil
        )
    }

    /// Read a page of chatrooms for the selected WeChat account.
    func listChatRooms(
        account: OpenApiSocialAccount,
        page: Int,
        pageSize: Int,
        includeDeleted: Bool = false
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        return try await request(
            "GET",
            "/openapi/v1/chatrooms",
            accountQuery(
                account,
                additional: [
                    URLQueryItem(name: "page", value: "\(max(page, 1))"),
                    URLQueryItem(name: "pageSize", value: "\(min(max(pageSize, 1), 100))"),
                    URLQueryItem(name: "includeDeleted", value: includeDeleted ? "true" : "false")
                ]
            ),
            nil
        )
    }

    /// Read a page of members for a specific chatroom.
    func listChatRoomMembers(
        account: OpenApiSocialAccount,
        chatRoomID: String,
        page: Int,
        pageSize: Int,
        includeDeleted: Bool = false
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        let chatRoomID = cleaned(chatRoomID)
        guard chatRoomID.hasSuffix("@chatroom") else {
            throw OpenApiSocialManagementError.invalidChatRoomID(chatRoomID)
        }
        return try await request(
            "GET",
            "/openapi/v1/chatrooms/\(chatRoomID)/members",
            accountQuery(
                account,
                additional: [
                    URLQueryItem(name: "page", value: "\(max(page, 1))"),
                    URLQueryItem(name: "pageSize", value: "\(min(max(pageSize, 1), 200))"),
                    URLQueryItem(name: "includeDeleted", value: includeDeleted ? "true" : "false")
                ]
            ),
            nil
        )
    }

    /// Read friend requests without mutating them.
    func listFriendRequests(
        account: OpenApiSocialAccount,
        count: Int,
        pendingOnly: Bool = false
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        return try await request(
            "GET",
            "/openapi/v1/friend-requests",
            accountQuery(
                account,
                additional: [
                    URLQueryItem(name: "count", value: "\(min(max(count, 1), 100))"),
                    URLQueryItem(name: "pendingOnly", value: pendingOnly ? "true" : "false")
                ]
            ),
            nil
        )
    }

    func renameChatRoom(account: OpenApiSocialAccount, chatRoomID: String, name: String) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/rename",
            action: "修改群名称",
            fields: ["name": try required(name, field: "name")]
        )
    }

    func updateChatRoomNotice(account: OpenApiSocialAccount, chatRoomID: String, notice: String) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/notice",
            action: "设置群公告",
            fields: ["notice": try required(notice, field: "notice")]
        )
    }

    func refreshChatRoom(account: OpenApiSocialAccount, chatRoomID: String) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/refresh",
            action: "刷新群资料",
            fields: ["flag": 1]
        )
    }

    /// Requests a chatroom QR code. The caller parses/polls the returned payload
    /// because this endpoint may return either an immediate image or a task ID.
    func fetchChatRoomQRCode(
        account: OpenApiSocialAccount,
        chatRoomID: String
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        let chatRoomID = cleaned(chatRoomID)
        guard chatRoomID.hasSuffix("@chatroom") else {
            throw OpenApiSocialManagementError.invalidChatRoomID(chatRoomID)
        }
        var body = commonBody(account)
        body["chatRoomId"] = chatRoomID
        return try await request("POST", "/openapi/v1/chatrooms/qrcode", [], body)
    }

    func inviteMembers(account: OpenApiSocialAccount, chatRoomID: String, memberWxids: [String]) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/members/invite",
            action: "邀请群成员",
            fields: ["memberWxids": try validMemberWxids(memberWxids)]
        )
    }

    func removeMembers(account: OpenApiSocialAccount, chatRoomID: String, memberWxids: [String]) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/members/kick",
            action: "移出群成员",
            fields: ["memberWxids": try validMemberWxids(memberWxids)]
        )
    }

    func exitChatRoom(account: OpenApiSocialAccount, chatRoomID: String) async throws -> String {
        try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/chatrooms/exit",
            action: "退出群聊"
        )
    }

    func addFriendFromChatRoom(
        account: OpenApiSocialAccount,
        chatRoomID: String,
        friendWxid: String,
        message: String = "你好，我们在群里见过。",
        remark: String = ""
    ) async throws -> String {
        let friendWxid = try validFriendWxid(friendWxid)
        let message = cleaned(message).isEmpty ? "你好，我们在群里见过。" : cleaned(message)
        var fields: [String: Any] = [
            "friendId": friendWxid,
            "message": message,
            "permission": 0
        ]
        if !cleaned(remark).isEmpty { fields["remark"] = cleaned(remark) }
        return try await submitGroupTask(
            account: account,
            chatRoomID: chatRoomID,
            path: "/openapi/v1/friends/in-chatroom",
            action: "群内加好友",
            fields: fields
        )
    }

    // MARK: - Friend lifecycle

    func findFriendWxid(account: OpenApiSocialAccount, target: String) async throws -> String {
        let account = try validAccount(account)
        let target = try required(target, field: "content")
        let result = try await request(
            "POST",
            "/openapi/v1/friends/find",
            [],
            commonBody(account).merging(["content": target]) { _, new in new }
        )
        if let wxid = friendWxid(in: result.jsonObject) { return wxid }

        guard let taskID = OpenApiHTTPClient.taskID(from: result.jsonObject) else {
            throw OpenApiSocialManagementError.friendNotFound(target)
        }
        for attempt in 0..<8 {
            if attempt > 0 {
                try await sleep(UInt64(700_000_000 + attempt * 300_000_000))
            }
            let task = try await request("GET", "/openapi/v1/tasks/\(taskID)", [], nil)
            if let wxid = friendWxid(in: task.jsonObject) { return wxid }
        }
        throw OpenApiSocialManagementError.friendNotFound(target)
    }

    func addFriend(
        account: OpenApiSocialAccount,
        friendWxid: String,
        options: OpenApiFriendAddOptions = .init()
    ) async throws -> String {
        let friendWxid = try validFriendWxid(friendWxid)
        var body = commonBody(try validAccount(account))
        body["friendWxid"] = friendWxid
        body["message"] = cleaned(options.verificationMessage).isEmpty ? "你好" : cleaned(options.verificationMessage)
        body["permission"] = 0
        body["scene"] = 1
        body["sourceChannel"] = cleaned(options.sourceChannel).isEmpty ? "ios-app" : cleaned(options.sourceChannel)
        body["sourceDetail"] = "只发 iOS 好友管理"
        body["notes"] = "只发 iOS 发起添加"
        appendFriendOptions(options, to: &body)
        return try await submitTask(path: "/openapi/v1/friends", action: "添加好友", body: body)
    }

    func addFriendByPhone(
        account: OpenApiSocialAccount,
        phone: String,
        options: OpenApiFriendAddOptions = .init()
    ) async throws -> String {
        let phone = try required(phone, field: "phone")
        var body = commonBody(try validAccount(account))
        body["phones"] = [phone]
        body["message"] = cleaned(options.verificationMessage).isEmpty ? "你好" : cleaned(options.verificationMessage)
        body["permission"] = 0
        appendFriendOptions(options, to: &body)
        return try await submitTask(path: "/openapi/v1/friends/by-phone", action: "通过手机号添加好友", body: body)
    }

    func deleteFriend(account: OpenApiSocialAccount, friendID: String) async throws -> String {
        let account = try validAccount(account)
        let friendID = try required(friendID, field: "friendID")
        let result = try await request(
            "DELETE",
            "/openapi/v1/friends/\(friendID)",
            [
                URLQueryItem(name: "deviceUuid", value: account.deviceUUID),
                URLQueryItem(name: "weChatId", value: account.weChatID)
            ],
            nil
        )
        return try await validateTask(result, "删除好友")
    }

    func pullFriendRequests(account: OpenApiSocialAccount, onlyNew: Bool = false) async throws -> String {
        let account = try validAccount(account)
        var body = commonBody(account)
        body["startTime"] = 0
        body["onlyNew"] = onlyNew
        body["getAll"] = !onlyNew
        return try await submitTask(path: "/openapi/v1/friend-requests/pull", action: "拉取好友申请", body: body)
    }

    func handleFriendRequest(
        account: OpenApiSocialAccount,
        friendWxid: String,
        friendNickname: String,
        decision: OpenApiFriendRequestDecision,
        remark: String = "",
        reply: String = ""
    ) async throws -> String {
        let friendWxid = try validFriendWxid(friendWxid)
        var body = commonBody(try validAccount(account))
        body["friendId"] = friendWxid
        body["friendNick"] = cleaned(friendNickname)
        body["remark"] = cleaned(remark)
        body["replyMsg"] = cleaned(reply)
        body["addWithWW"] = false
        body["onlyWW"] = false
        body["permission"] = 0
        body["operation"] = decision.rawValue
        return try await submitTask(path: "/openapi/v1/friend-requests/handle", action: decision == .accept ? "通过好友申请" : "拒绝好友申请", body: body)
    }

    private func submitGroupTask(
        account: OpenApiSocialAccount,
        chatRoomID: String,
        path: String,
        action: String,
        fields: [String: Any] = [:]
    ) async throws -> String {
        let account = try validAccount(account)
        let chatRoomID = cleaned(chatRoomID)
        guard chatRoomID.hasSuffix("@chatroom") else {
            throw OpenApiSocialManagementError.invalidChatRoomID(chatRoomID)
        }
        var body = commonBody(account)
        body["chatRoomId"] = chatRoomID
        fields.forEach { body[$0.key] = $0.value }
        return try await submitTask(path: path, action: action, body: body)
    }

    private func submitTask(path: String, action: String, body: [String: Any]) async throws -> String {
        let result = try await request("POST", path, [], body)
        return try await validateTask(result, action)
    }

    private func validAccount(_ account: OpenApiSocialAccount) throws -> OpenApiSocialAccount {
        guard !account.weChatID.isEmpty else { throw OpenApiSocialManagementError.missingField("weChatId") }
        return account
    }

    private func commonBody(_ account: OpenApiSocialAccount) -> [String: Any] {
        var body: [String: Any] = ["weChatId": account.weChatID]
        if !account.deviceUUID.isEmpty { body["deviceUuid"] = account.deviceUUID }
        return body
    }

    private func accountQuery(
        _ account: OpenApiSocialAccount,
        additional: [URLQueryItem]
    ) -> [URLQueryItem] {
        var query = [URLQueryItem(name: "weChatId", value: account.weChatID)]
        if !account.deviceUUID.isEmpty {
            query.append(URLQueryItem(name: "deviceUuid", value: account.deviceUUID))
        }
        query.append(contentsOf: additional)
        return query
    }

    private func validMemberWxids(_ values: [String]) throws -> [String] {
        let values = values.map(cleaned).filter { !$0.isEmpty }
        guard !values.isEmpty else { throw OpenApiSocialManagementError.missingField("memberWxids") }
        for value in values { _ = try validFriendWxid(value) }
        return Array(NSOrderedSet(array: values)) as? [String] ?? values
    }

    private func validFriendWxid(_ value: String) throws -> String {
        let value = cleaned(value)
        guard !value.isEmpty, !value.hasSuffix("@chatroom") else {
            throw OpenApiSocialManagementError.invalidFriendID(value)
        }
        return value
    }

    private func required(_ value: String, field: String) throws -> String {
        let value = cleaned(value)
        guard !value.isEmpty else { throw OpenApiSocialManagementError.missingField(field) }
        return value
    }

    private func cleaned(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func appendFriendOptions(_ options: OpenApiFriendAddOptions, to body: inout [String: Any]) {
        if !cleaned(options.remark).isEmpty { body["remark"] = cleaned(options.remark) }
        let labels = options.labelNames.map(cleaned).filter { !$0.isEmpty }
        if !labels.isEmpty {
            body["label"] = labels.joined(separator: ",")
            body["mappedLabelNames"] = labels
        }
        if !cleaned(options.customerLevel).isEmpty { body["customerLevel"] = cleaned(options.customerLevel) }
        if !cleaned(options.profileKey).isEmpty { body["profileKey"] = cleaned(options.profileKey) }
        if !cleaned(options.verificationImageURL).isEmpty {
            body["faceImageUrl"] = cleaned(options.verificationImageURL)
            body["verifyImageUrl"] = cleaned(options.verificationImageURL)
        }
    }

    private func friendWxid(in object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            for key in ["friendWxid", "friend_wxid", "contactWxid", "contact_wxid", "wxid", "userName", "username"] {
                if let value = dictionary[key] as? String {
                    let value = cleaned(value)
                    if value.hasPrefix("wxid_") || value.hasPrefix("gh_") || value.contains("@stranger") {
                        return value
                    }
                }
            }
            for value in dictionary.values {
                if let found = friendWxid(in: value) { return found }
            }
        } else if let array = object as? [Any] {
            for value in array {
                if let found = friendWxid(in: value) { return found }
            }
        }
        return nil
    }
}

enum OpenApiMessageServiceError: LocalizedError, Equatable {
    case missingField(String)
    case invalidMessageID(String)
    case mediaURLMissing
    case unsupportedMediaURL(String)

    var errorDescription: String? {
        switch self {
        case .missingField(let field): return "缺少必填字段：\(field)"
        case .invalidMessageID(let value): return "消息 ID 无效：\(value)"
        case .mediaURLMissing: return "媒体接口未返回可访问 URL"
        case .unsupportedMediaURL(let value): return "媒体 URL 不受支持：\(value)"
        }
    }
}

struct OpenApiMessageMediaResource: Equatable {
    let url: URL
    let mimeType: String
    let fileName: String
}

struct OpenApiFinderAccount: Hashable {
    let deviceUUID: String
    let weChatID: String

    init(deviceUUID: String = "", weChatID: String) {
        self.deviceUUID = deviceUUID.trimmingCharacters(in: .whitespacesAndNewlines)
        self.weChatID = weChatID.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

struct OpenApiFinderPostOptions: Hashable {
    var content: String
    var mediaType = "video"
    var mediaURLs: [String]
    var coverURL = ""
    var poiName = ""
    var poiAddress = ""

    init(
        content: String,
        mediaURLs: [String],
        mediaType: String = "video",
        coverURL: String = "",
        poiName: String = "",
        poiAddress: String = ""
    ) {
        self.content = content
        self.mediaURLs = mediaURLs
        self.mediaType = mediaType
        self.coverURL = coverURL
        self.poiName = poiName
        self.poiAddress = poiAddress
    }
}

enum OpenApiFinderServiceError: LocalizedError, Equatable {
    case missingField(String)
    case invalidMediaURL(String)
    case invalidCoverURL(String)
    case templateRejected(String)

    var errorDescription: String? {
        switch self {
        case .missingField(let field): return "缺少必填字段：\(field)"
        case .invalidMediaURL(let value): return "视频号媒体 URL 无效：\(value)"
        case .invalidCoverURL(let value): return "视频号封面 URL 无效：\(value)"
        case .templateRejected(let message): return "视频号发布模板未通过：\(message)"
        }
    }
}

/// Finder endpoints used by the real video-account flow. The workbench may
/// still expose additional endpoint fields, but it must call this service for
/// request construction so the main page cannot fall back to demo data.
struct OpenApiFinderService {
    typealias Request = OpenApiSocialManagementService.Request
    typealias TaskValidator = OpenApiSocialManagementService.TaskValidator

    private let request: Request
    private let validateTask: TaskValidator

    init(
        request: @escaping Request = { method, path, query, body in
            try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
        },
        validateTask: @escaping TaskValidator = { result, action in
            try await OpenApiHTTPClient.validateTaskResult(result, action: action)
        }
    ) {
        self.request = request
        self.validateTask = validateTask
    }

    func buildPostBody(
        account: OpenApiFinderAccount,
        options: OpenApiFinderPostOptions
    ) throws -> [String: Any] {
        let account = try validAccount(account)
        let content = try required(options.content, field: "content")
        let mediaURLs = try validMediaURLs(options.mediaURLs)
        let mediaType = options.mediaType.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !mediaType.isEmpty else {
            throw OpenApiFinderServiceError.missingField("mediaType")
        }

        var body: [String: Any] = [
            "weChatId": account.weChatID,
            "content": content,
            "mediaType": mediaType,
            "medias": mediaURLs
        ]
        if !account.deviceUUID.isEmpty { body["deviceUuid"] = account.deviceUUID }
        let coverURL = options.coverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        if !coverURL.isEmpty {
            guard isHTTPURL(coverURL) else {
                throw OpenApiFinderServiceError.invalidCoverURL(coverURL)
            }
            body["cover"] = coverURL
        }
        let poiName = options.poiName.trimmingCharacters(in: .whitespacesAndNewlines)
        let poiAddress = options.poiAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        if !poiName.isEmpty || !poiAddress.isEmpty {
            var poi: [String: Any] = [:]
            if !poiName.isEmpty { poi["name"] = poiName }
            if !poiAddress.isEmpty { poi["address"] = poiAddress }
            body["poi"] = poi
        }
        return body
    }

    func validatePost(
        account: OpenApiFinderAccount,
        options: OpenApiFinderPostOptions
    ) async throws -> OpenApiHTTPResult {
        let body = try buildPostBody(account: account, options: options)
        let result = try await request("POST", "/openapi/v1/finder/posts/template", [], body)
        if let rejection = templateRejection(in: result.jsonObject) {
            throw OpenApiFinderServiceError.templateRejected(rejection)
        }
        return result
    }

    /// Submit a real video-account publication and wait for the server task's
    /// terminal state before returning. The returned response is the submit
    /// response and can be rendered for diagnostics.
    func publish(
        account: OpenApiFinderAccount,
        options: OpenApiFinderPostOptions
    ) async throws -> OpenApiHTTPResult {
        let body = try buildPostBody(account: account, options: options)
        let result = try await request("POST", "/openapi/v1/finder/posts", [], body)
        _ = try await validateTask(result, "发布视频号内容")
        return result
    }

    func fetchMentions(account: OpenApiFinderAccount) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        return try await request("POST", "/openapi/v1/finder/mentions", [], accountBody(account))
    }

    func fetchUserPage(account: OpenApiFinderAccount, sphUserName: String) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        var body = accountBody(account)
        body["sphUserName"] = try required(sphUserName, field: "sphUserName")
        return try await request("POST", "/openapi/v1/finder/user-page", [], body)
    }

    func fetchComments(
        account: OpenApiFinderAccount,
        feedID: String,
        nonceID: String = "",
        sortType: Int = 0
    ) async throws -> OpenApiHTTPResult {
        let account = try validAccount(account)
        var body = accountBody(account)
        body["feedId"] = try required(feedID, field: "feedId")
        let nonceID = nonceID.trimmingCharacters(in: .whitespacesAndNewlines)
        if !nonceID.isEmpty { body["nonceId"] = nonceID }
        body["sortType"] = sortType
        return try await request("POST", "/openapi/v1/finder/comments/list", [], body)
    }

    private func validAccount(_ account: OpenApiFinderAccount) throws -> OpenApiFinderAccount {
        guard !account.weChatID.isEmpty else {
            throw OpenApiFinderServiceError.missingField("weChatId")
        }
        return account
    }

    private func accountBody(_ account: OpenApiFinderAccount) -> [String: Any] {
        var body: [String: Any] = ["weChatId": account.weChatID]
        if !account.deviceUUID.isEmpty { body["deviceUuid"] = account.deviceUUID }
        return body
    }

    private func required(_ value: String, field: String) throws -> String {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw OpenApiFinderServiceError.missingField(field) }
        return value
    }

    private func validMediaURLs(_ values: [String]) throws -> [String] {
        let values = values.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !values.isEmpty else { throw OpenApiFinderServiceError.missingField("medias") }
        for value in values {
            guard isHTTPURL(value) else { throw OpenApiFinderServiceError.invalidMediaURL(value) }
        }
        return values
    }

    private func isHTTPURL(_ value: String) -> Bool {
        guard let url = URL(string: value), let scheme = url.scheme?.lowercased() else { return false }
        return ["http", "https"].contains(scheme) && url.host != nil
    }

    private func templateRejection(in object: Any?) -> String? {
        guard let dictionary = object as? [String: Any] else {
            if let array = object as? [Any] {
                return array.compactMap(templateRejection(in:)).first
            }
            return nil
        }
        for key in ["success", "valid", "isValid", "passed"] {
            if let value = dictionary[key] as? Bool, value == false {
                return firstMessage(in: dictionary) ?? "服务端返回校验失败"
            }
        }
        for key in ["data", "result", "payload", "template", "taskResult"] {
            if let rejection = templateRejection(in: dictionary[key]) { return rejection }
        }
        return nil
    }

    private func firstMessage(in dictionary: [String: Any]) -> String? {
        for key in ["message", "error", "errorMessage", "detail", "reason", "code"] {
            if let value = dictionary[key] as? String {
                let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty { return value }
            }
        }
        return nil
    }
}

/// Message actions that already have a main-chat entry point. The service keeps
/// request construction and response validation out of the view controller.
struct OpenApiMessageService {
    typealias Request = OpenApiSocialManagementService.Request
    typealias TaskValidator = OpenApiSocialManagementService.TaskValidator

    private let request: Request
    private let validateTask: TaskValidator

    init(
        request: @escaping Request = { method, path, query, body in
            try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
        },
        validateTask: @escaping TaskValidator = { result, action in
            try await OpenApiHTTPClient.validateTaskResult(result, action: action)
        }
    ) {
        self.request = request
        self.validateTask = validateTask
    }

    func sendQuote(
        account: OpenApiSocialAccount,
        conversationID: String,
        content: String,
        quoteMessageServerID: String
    ) async throws -> String {
        let account = try validAccount(account)
        let conversationID = try required(conversationID, field: "conversationId")
        let content = try required(content, field: "content")
        let quoteMessageServerID = try validMessageID(quoteMessageServerID)
        var body: [String: Any] = [
            "deviceUuid": account.deviceUUID,
            "weChatId": account.weChatID,
            "conversationId": conversationID,
            "content": content,
            "quoteMsgSvrId": quoteMessageServerID
        ]
        if account.deviceUUID.isEmpty { body.removeValue(forKey: "deviceUuid") }
        let result = try await request("POST", "/openapi/v1/messages/quote", [], body)
        return try await validateTask(result, "发送引用消息")
    }

    func sendEmoji(
        account: OpenApiSocialAccount,
        conversationID: String,
        md5: String
    ) async throws -> String {
        let account = try validAccount(account)
        let conversationID = try required(conversationID, field: "conversationId")
        let md5 = try required(md5, field: "md5")
        var body: [String: Any] = [
            "deviceUuid": account.deviceUUID,
            "weChatId": account.weChatID,
            "conversationId": conversationID,
            "md5": md5
        ]
        if account.deviceUUID.isEmpty { body.removeValue(forKey: "deviceUuid") }
        let result = try await request("POST", "/openapi/v1/messages/emoji", [], body)
        return try await validateTask(result, "发送表情")
    }

    /// The media download endpoint is optional on the current backend. It is
    /// called lazily for a message whose history payload had no media URL.
    func fetchMedia(
        account: OpenApiSocialAccount,
        messageID: String
    ) async throws -> OpenApiMessageMediaResource {
        let account = try validAccount(account)
        let messageID = try validMessageID(messageID)
        var query = [URLQueryItem(name: "weChatId", value: account.weChatID)]
        if !account.deviceUUID.isEmpty {
            query.append(URLQueryItem(name: "deviceUuid", value: account.deviceUUID))
        }
        let result = try await request(
            "GET",
            "/openapi/v1/messages/\(messageID)/media",
            query,
            nil
        )
        guard let resource = Self.mediaResource(in: result.jsonObject) else {
            throw OpenApiMessageServiceError.mediaURLMissing
        }
        return resource
    }

    private func validAccount(_ account: OpenApiSocialAccount) throws -> OpenApiSocialAccount {
        guard !account.weChatID.isEmpty else {
            throw OpenApiMessageServiceError.missingField("weChatId")
        }
        return account
    }

    private func required(_ value: String, field: String) throws -> String {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw OpenApiMessageServiceError.missingField(field) }
        return value
    }

    private func validMessageID(_ value: String) throws -> String {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty,
              !value.contains(where: { $0.isWhitespace || $0 == "/" || $0 == "\\" })
        else { throw OpenApiMessageServiceError.invalidMessageID(value) }
        return value
    }

    private static func mediaResource(in object: Any?) -> OpenApiMessageMediaResource? {
        if let text = object as? String {
            return resource(urlText: text, mimeType: "", fileName: "")
        }
        if let dictionary = object as? [String: Any] {
            let urlText = firstString(in: dictionary, keys: [
                "downloadUrl", "downloadURL", "mediaUrl", "mediaURL", "fileUrl", "fileURL",
                "imageUrl", "imageURL", "videoUrl", "videoURL", "voiceUrl", "voiceURL", "url", "path"
            ])
            let mimeType = firstString(in: dictionary, keys: ["mimeType", "contentType", "mediaType"])
            let fileName = firstString(in: dictionary, keys: ["fileName", "filename", "name"])
            if let resource = resource(urlText: urlText, mimeType: mimeType, fileName: fileName) {
                return resource
            }
            for key in ["data", "result", "payload", "media", "item", "file", "attachment"] {
                if let resource = mediaResource(in: dictionary[key]) { return resource }
            }
            for value in dictionary.values {
                if let resource = mediaResource(in: value) { return resource }
            }
        } else if let array = object as? [Any] {
            for value in array {
                if let resource = mediaResource(in: value) { return resource }
            }
        }
        return nil
    }

    private static func resource(urlText: String, mimeType: String, fileName: String) -> OpenApiMessageMediaResource? {
        let value = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let url = URL(string: value) else { return nil }
        let scheme = url.scheme?.lowercased() ?? ""
        guard ["http", "https", "file"].contains(scheme) else { return nil }
        return OpenApiMessageMediaResource(
            url: url,
            mimeType: mimeType.trimmingCharacters(in: .whitespacesAndNewlines),
            fileName: fileName.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    private static func firstString(in dictionary: [String: Any], keys: [String]) -> String {
        for key in keys {
            if let value = dictionary[key] as? String,
               !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return value
            }
        }
        return ""
    }
}

/// Payment message endpoints are kept separate from social-management actions,
/// but share the same account context and task validation contract.
struct OpenApiPaymentService {
    typealias Request = OpenApiSocialManagementService.Request
    typealias TaskValidator = OpenApiSocialManagementService.TaskValidator

    private let request: Request
    private let validateTask: TaskValidator

    init(
        request: @escaping Request = { method, path, query, body in
            try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
        },
        validateTask: @escaping TaskValidator = { result, action in
            try await OpenApiHTTPClient.validateTaskResult(result, action: action)
        }
    ) {
        self.request = request
        self.validateTask = validateTask
    }

    func redPacketStatus(
        account: OpenApiSocialAccount,
        messageServerID: String
    ) async throws -> OpenApiHTTPResult {
        try await readPayment(
            account: account,
            messageServerID: messageServerID,
            path: "/openapi/v1/payments/red-packets/status-by-message"
        )
    }

    func redPacketDetail(
        account: OpenApiSocialAccount,
        messageServerID: String
    ) async throws -> OpenApiHTTPResult {
        try await readPayment(
            account: account,
            messageServerID: messageServerID,
            path: "/openapi/v1/payments/red-packets/detail-by-message"
        )
    }

    func takeRedPacket(
        account: OpenApiSocialAccount,
        messageServerID: String
    ) async throws -> String {
        try await takePayment(
            account: account,
            messageServerID: messageServerID,
            path: "/openapi/v1/payments/lucky-money/take-by-message",
            action: "领取红包"
        )
    }

    func takeTransfer(
        account: OpenApiSocialAccount,
        messageServerID: String
    ) async throws -> String {
        try await takePayment(
            account: account,
            messageServerID: messageServerID,
            path: "/openapi/v1/payments/transfers/take-by-message",
            action: "领取转账"
        )
    }

    private func readPayment(
        account: OpenApiSocialAccount,
        messageServerID: String,
        path: String
    ) async throws -> OpenApiHTTPResult {
        let body = try paymentBody(account: account, messageServerID: messageServerID)
        return try await request("POST", path, [], body)
    }

    private func takePayment(
        account: OpenApiSocialAccount,
        messageServerID: String,
        path: String,
        action: String
    ) async throws -> String {
        let result = try await readPayment(account: account, messageServerID: messageServerID, path: path)
        return try await validateTask(result, action)
    }

    private func paymentBody(
        account: OpenApiSocialAccount,
        messageServerID: String
    ) throws -> [String: Any] {
        guard !account.weChatID.isEmpty else {
            throw OpenApiSocialManagementError.missingField("weChatId")
        }
        let messageServerID = messageServerID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !messageServerID.isEmpty else {
            throw OpenApiSocialManagementError.missingField("msgSvrId")
        }
        var body: [String: Any] = [
            "weChatId": account.weChatID,
            "msgSvrId": messageServerID
        ]
        if !account.deviceUUID.isEmpty { body["deviceUuid"] = account.deviceUUID }
        return body
    }
}
