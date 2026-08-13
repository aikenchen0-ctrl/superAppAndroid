import Foundation
import UIKit
import UniformTypeIdentifiers

struct OpenApiFieldRow {
    let key: String
    let value: String
    let subtitle: String?
}

struct OpenApiRuntime {
    var baseURL = OpenApiConfiguration.current.baseURL.absoluteString
    var tokenState = OpenApiConfiguration.current.apiKey.isEmpty ? "未配置" : "X-API-Key \(OpenApiConfiguration.current.apiKey.maskedSecret())"
    var environment = "真实 OpenAPI"
    var cachePolicy = "memory+sqlite / stale-while-revalidate"
    var page = 1
    var pageSize = 20
    var schemaVersion = "SCRM_OpenAPI_2026-06"

    var authRows: [OpenApiFieldRow] {
        [
            OpenApiFieldRow(key: "baseURL", value: baseURL, subtitle: "真实网络层接入时统一从这里读取。"),
            OpenApiFieldRow(key: "X-API-Key", value: tokenState, subtitle: "真实 OpenAPI 鉴权头；兼容 Authorization Bearer。"),
            OpenApiFieldRow(key: "mediaMultipartField", value: OpenApiConfiguration.verifiedMediaMultipartFieldName ?? "未验证", subtitle: "Swagger 未声明上传字段名；未配置时使用原始二进制上传，已配置时优先 multipart。"),
            OpenApiFieldRow(key: "登录接口", value: "/api/auth/login", subtitle: "帐号密码获取 JWT，用 JWT 创建/管理 OpenAPI Key。"),
            OpenApiFieldRow(key: "X-Schema-Version", value: schemaVersion, subtitle: "接口文档/Schema 版本。")
        ]
    }

    var networkRows: [OpenApiFieldRow] {
        [
            OpenApiFieldRow(key: "pagination", value: "page=\(page)&pageSize=\(pageSize)", subtitle: "统一分页参数，列表页按这个模拟。"),
            OpenApiFieldRow(key: "cachePolicy", value: cachePolicy, subtitle: "先读本地缓存，再刷新接口数据。"),
            OpenApiFieldRow(key: "retry", value: "3次 / 指数退避", subtitle: "网络失败、5xx、超时走统一重试。"),
            OpenApiFieldRow(key: "timeout", value: "15s", subtitle: "接口超时统一配置。")
        ]
    }

    var validationRows: [OpenApiFieldRow] {
        [
            OpenApiFieldRow(key: "schemaValidation", value: "enabled", subtitle: "请求字段、响应字段、类型、必填项本地模拟校验。"),
            OpenApiFieldRow(key: "error.401", value: "UNAUTHORIZED", subtitle: "鉴权失败，提示重新配置访问凭证。"),
            OpenApiFieldRow(key: "error.422", value: "SCHEMA_INVALID", subtitle: "字段缺失或类型不匹配。"),
            OpenApiFieldRow(key: "error.429", value: "RATE_LIMITED", subtitle: "限流，显示稍后重试。"),
            OpenApiFieldRow(key: "error.500", value: "SERVER_ERROR", subtitle: "服务端错误，进入统一错误页和重试。")
        ]
    }
}

enum OpenApiMockClient {
    static let sharedRuntime = OpenApiRuntime()

    static func envelope(endpoint: String, success: Bool = true, message: String = "OK") -> [OpenApiFieldRow] {
        [
            OpenApiFieldRow(key: "endpoint", value: endpoint, subtitle: "统一接口路径。"),
            OpenApiFieldRow(key: "success", value: "\(success)", subtitle: "统一响应成功标识。"),
            OpenApiFieldRow(key: "code", value: success ? "0" : "SCHEMA_INVALID", subtitle: "统一错误码。"),
            OpenApiFieldRow(key: "message", value: message, subtitle: "用户可读错误/成功信息。"),
            OpenApiFieldRow(key: "requestId", value: UUID().uuidString, subtitle: "链路追踪 ID。"),
            OpenApiFieldRow(key: "cachedAt", value: ChatMessage.displayTimestamp(for: Date()), subtitle: "本地缓存更新时间。")
        ]
    }
}

struct OpenApiConfiguration {
    static let baseURLKey = "OpenApiBaseURL"
    static let apiKeyKey = "OpenApiXAPIKey"
    static let jwtKey = "OpenApiJWT"
    static let loginUsernameKey = "OpenApiLoginUsername"
    static let loginPasswordKey = "OpenApiLoginPassword"
    static let defaultBaseURL = "http://112.74.164.233:42718"
    static let legacyHTTPSBaseURL = "https://112.74.164.233:42718"
    static let defaultAPIKey = ""
    static let mediaMultipartFieldNameKey = "OpenApiMediaMultipartFieldName"
    static let defaultMediaMultipartFieldName = "file"

    var baseURL: URL
    var apiKey: String
    var jwt: String?

    static var current: OpenApiConfiguration {
        let baseURLText = ChatSQLiteStore.shared.string(forKey: baseURLKey)
            ?? UserDefaults.standard.string(forKey: baseURLKey)
            ?? defaultBaseURL
        let normalizedBaseURLText = normalizedBaseURLText(baseURLText)
        let apiKey = AppSecretStore.migrateLegacySecret(forKey: apiKeyKey)
            ?? defaultAPIKey
        let jwt = AppSecretStore.migrateLegacySecret(forKey: jwtKey)
        return OpenApiConfiguration(
            baseURL: URL(string: normalizedBaseURLText) ?? URL(string: defaultBaseURL)!,
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            jwt: jwt?.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    static func save(baseURL: String? = nil, apiKey: String? = nil, jwt: String? = nil) {
        if let baseURL {
            ChatSQLiteStore.shared.setString(baseURL, forKey: baseURLKey)
            UserDefaults.standard.set(baseURL, forKey: baseURLKey)
        }
        if let apiKey {
            AppSecretStore.setString(apiKey, forKey: apiKeyKey)
            AppSecretStore.clearLegacySecret(forKey: apiKeyKey)
        }
        if let jwt {
            AppSecretStore.setString(jwt, forKey: jwtKey)
            AppSecretStore.clearLegacySecret(forKey: jwtKey)
        }
    }

    static func saveLoginCredentials(username: String, password: String) {
        AppSecretStore.setString(username, forKey: loginUsernameKey)
        AppSecretStore.setString(password, forKey: loginPasswordKey)
        AppSecretStore.clearLegacySecret(forKey: loginUsernameKey)
        AppSecretStore.clearLegacySecret(forKey: loginPasswordKey)
    }

    static var savedLoginUsername: String {
        AppSecretStore.migrateLegacySecret(forKey: loginUsernameKey) ?? ""
    }

    static var savedLoginPassword: String {
        AppSecretStore.migrateLegacySecret(forKey: loginPasswordKey) ?? ""
    }

    static var verifiedMediaMultipartFieldName: String? {
        let text = ChatSQLiteStore.shared.string(forKey: mediaMultipartFieldNameKey)
            ?? UserDefaults.standard.string(forKey: mediaMultipartFieldNameKey)
        let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? defaultMediaMultipartFieldName : trimmed
    }

    static func mediaMultipartFieldNameCandidates(isVoice: Bool) -> [String] {
        let configured = verifiedMediaMultipartFieldName
        let fallbacks = isVoice
            ? [defaultMediaMultipartFieldName, "voice", "media", "upload", "uploadFile", "formFile", "files"]
            : [defaultMediaMultipartFieldName, "media", "image", "upload", "uploadFile", "formFile", "files"]
        return ([configured].compactMap { $0 } + fallbacks)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .reduce(into: [String]()) { result, item in
                if !result.contains(item) {
                    result.append(item)
                }
            }
    }

    private static func normalizedBaseURLText(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed == legacyHTTPSBaseURL {
            return defaultBaseURL
        }
        return trimmed.isEmpty ? defaultBaseURL : trimmed
    }
}

struct OpenApiHTTPResult {
    let statusCode: Int
    let endpoint: String
    let method: String
    let jsonObject: Any?
    let rawText: String
}

enum OpenApiMediaSendError: LocalizedError {
    case missingAttachmentURL(String)
    case missingUploadedURL(String)
    case missingRequiredFields(String, String)
    case taskFailed(String, String)
    case taskResultUnknown(String, String)

    var errorDescription: String? {
        switch self {
        case .missingAttachmentURL(let kind):
            return "\(kind)消息缺少本地文件或远程链接。"
        case .missingUploadedURL(let kind):
            return "\(kind)上传成功但未返回可发送 URL。"
        case .missingRequiredFields(let action, let message):
            return "\(action)缺少参数：\(message)"
        case .taskFailed(let action, let message):
            return "\(action)失败：\(message)"
        case .taskResultUnknown(let action, let message):
            return "\(action)未确认：\(message)"
        }
    }
}

/// Coalesces credential recovery so a burst of background sync requests does
/// not create multiple OpenAPI Keys after one key has been revoked or expired.
actor OpenApiCredentialRecovery {
    static let shared = OpenApiCredentialRecovery()

    private var activeRecovery: Task<Bool, Never>?

    func recoverIfPossible() async -> Bool {
        if let activeRecovery {
            return await activeRecovery.value
        }

        let username = OpenApiConfiguration.savedLoginUsername
        let password = OpenApiConfiguration.savedLoginPassword
        guard !username.isEmpty, !password.isEmpty else { return false }

        let recovery = Task { () -> Bool in
            do {
                _ = try await OpenApiHTTPClient.login(username: username, password: password)
                _ = try await OpenApiHTTPClient.createAPIKey(
                    name: "iOS 自动恢复 OpenAPI Key",
                    remark: "原 OpenAPI Key 返回 401 后自动恢复"
                )
                return true
            } catch {
                return false
            }
        }
        activeRecovery = recovery
        let recovered = await recovery.value
        activeRecovery = nil
        return recovered
    }
}

enum OpenApiHTTPClient {
    enum ClientError: LocalizedError {
        case invalidURL(String)
        case missingAPIKey
        case badStatus(Int, String)
        case invalidResponse
        case missingToken
        case missingPlainKey

        var errorDescription: String? {
            switch self {
            case .invalidURL(let path): return "OpenAPI 地址无效：\(path)"
            case .missingAPIKey: return "缺少 X-API-Key，请先在 OpenAPI 环境中配置或登录创建。"
            case .badStatus(let status, let body): return "HTTP \(status)：\(body.isEmpty ? "无响应体" : body)"
            case .invalidResponse: return "服务端响应格式不正确。"
            case .missingToken: return "登录成功但没有返回 token。"
            case .missingPlainKey: return "创建成功但没有返回 plainKey。"
            }
        }
    }

    static func request(
        _ method: String,
        path: String,
        query: [URLQueryItem] = [],
        body: Any? = nil,
        authorizedByJWT: Bool = false,
        allowsCredentialRecovery: Bool = true
    ) async throws -> OpenApiHTTPResult {
        let config = OpenApiConfiguration.current
        guard var components = URLComponents(
            url: config.baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))),
            resolvingAgainstBaseURL: false
        ) else {
            throw ClientError.invalidURL(path)
        }
        if !query.isEmpty {
            components.queryItems = query
        }
        guard let url = components.url else {
            throw ClientError.invalidURL(path)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 18
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if authorizedByJWT {
            guard let jwt = config.jwt, !jwt.isEmpty else { throw ClientError.missingToken }
            request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        } else {
            guard !config.apiKey.isEmpty else { throw ClientError.missingAPIKey }
            request.setValue(config.apiKey, forHTTPHeaderField: "X-API-Key")
            request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ClientError.invalidResponse }
        let text = String(data: data, encoding: .utf8) ?? ""
        let object = data.isEmpty ? nil : try? JSONSerialization.jsonObject(with: data)
        if http.statusCode == 401,
           !authorizedByJWT,
           allowsCredentialRecovery,
           await OpenApiCredentialRecovery.shared.recoverIfPossible() {
            // A 401 means this request was rejected before it reached the
            // device task queue, so one retry with a fresh Key is safe.
            return try await OpenApiHTTPClient.request(
                method,
                path: path,
                query: query,
                body: body,
                authorizedByJWT: false,
                allowsCredentialRecovery: false
            )
        }
        guard 200..<300 ~= http.statusCode else {
            throw ClientError.badStatus(http.statusCode, text)
        }
        return OpenApiHTTPResult(statusCode: http.statusCode, endpoint: path, method: method, jsonObject: object, rawText: text)
    }

    static func uploadMediaFile(_ fileURL: URL) async throws -> OpenApiHTTPResult {
        let contentType = mediaContentType(for: fileURL)
        var lastMultipartError: Error?
        for fieldName in OpenApiConfiguration.mediaMultipartFieldNameCandidates(isVoice: false) {
            do {
                return try await uploadMultipartFile(
                    fileURL,
                    path: "/openapi/v1/media",
                    fieldName: fieldName,
                    fallbackFilename: "media.dat",
                    contentType: contentType
                )
            } catch {
                lastMultipartError = error
            }
        }
        return try await uploadRawFile(
            fileURL,
            path: "/openapi/v1/media",
            fallbackFilename: "media.dat",
            contentType: contentType,
            previousUploadError: lastMultipartError
        )
    }

    static func uploadVoiceFile(_ fileURL: URL) async throws -> OpenApiHTTPResult {
        let contentType = voiceContentType(for: fileURL)
        var lastMultipartError: Error?
        for fieldName in OpenApiConfiguration.mediaMultipartFieldNameCandidates(isVoice: true) {
            do {
                return try await uploadMultipartFile(
                    fileURL,
                    path: "/openapi/v1/media/voice",
                    fieldName: fieldName,
                    fallbackFilename: "voice.wav",
                    contentType: contentType
                )
            } catch {
                lastMultipartError = error
            }
        }
        return try await uploadRawFile(
            fileURL,
            path: "/openapi/v1/media/voice",
            fallbackFilename: "voice.wav",
            contentType: contentType,
            previousUploadError: lastMultipartError
        )
    }

    private static func uploadMultipartFile(
        _ fileURL: URL,
        path: String,
        fieldName: String,
        fallbackFilename: String,
        contentType: String
    ) async throws -> OpenApiHTTPResult {
        let config = OpenApiConfiguration.current
        guard let url = URL(string: path, relativeTo: config.baseURL)?.absoluteURL else {
            throw ClientError.invalidURL(path)
        }

        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 45
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        guard !config.apiKey.isEmpty else { throw ClientError.missingAPIKey }
        request.setValue(config.apiKey, forHTTPHeaderField: "X-API-Key")
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let bodyURL = try await makeMultipartUploadBody(
            fileURL,
            boundary: boundary,
            fieldName: fieldName,
            fallbackFilename: fallbackFilename,
            contentType: contentType
        )
        defer { try? FileManager.default.removeItem(at: bodyURL) }
        if let size = try? FileManager.default.attributesOfItem(atPath: bodyURL.path)[.size] as? NSNumber {
            request.setValue(size.stringValue, forHTTPHeaderField: "Content-Length")
        }

        let (responseData, response) = try await URLSession.shared.upload(for: request, fromFile: bodyURL)
        guard let http = response as? HTTPURLResponse else { throw ClientError.invalidResponse }
        let text = String(data: responseData, encoding: .utf8) ?? ""
        let object = responseData.isEmpty ? nil : try? JSONSerialization.jsonObject(with: responseData)
        guard 200..<300 ~= http.statusCode else {
            throw ClientError.badStatus(http.statusCode, text)
        }
        return OpenApiHTTPResult(statusCode: http.statusCode, endpoint: path, method: "POST", jsonObject: object, rawText: text)
    }

    private static func makeMultipartUploadBody(
        _ fileURL: URL,
        boundary: String,
        fieldName: String,
        fallbackFilename: String,
        contentType: String
    ) async throws -> URL {
        let bodyURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("openapi-upload-\(UUID().uuidString)")
            .appendingPathExtension("multipart")
        let filename = fileURL.lastPathComponent.isEmpty ? fallbackFilename : fileURL.lastPathComponent
        let safeFieldName = multipartEscaped(fieldName)
        let safeFilename = multipartEscaped(filename)

        return try await Task.detached(priority: .utility) {
            let didAccessSecurityScope = fileURL.startAccessingSecurityScopedResource()
            defer {
                if didAccessSecurityScope {
                    fileURL.stopAccessingSecurityScopedResource()
                }
            }

            FileManager.default.createFile(atPath: bodyURL.path, contents: nil)
            let output = try FileHandle(forWritingTo: bodyURL)
            defer { try? output.close() }

            try output.write(contentsOf: Data("--\(boundary)\r\n".utf8))
            try output.write(contentsOf: Data("Content-Disposition: form-data; name=\"\(safeFieldName)\"; filename=\"\(safeFilename)\"\r\n".utf8))
            try output.write(contentsOf: Data("Content-Type: \(contentType)\r\n\r\n".utf8))

            let input = try FileHandle(forReadingFrom: fileURL)
            defer { try? input.close() }
            while true {
                let chunk = try input.read(upToCount: 1024 * 1024) ?? Data()
                if chunk.isEmpty { break }
                try output.write(contentsOf: chunk)
            }

            try output.write(contentsOf: Data("\r\n--\(boundary)--\r\n".utf8))
            return bodyURL
        }.value
    }

    private static func multipartEscaped(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\r", with: "")
            .replacingOccurrences(of: "\n", with: "")
    }

    private static func uploadRawFile(
        _ fileURL: URL,
        path: String,
        fallbackFilename: String,
        contentType: String,
        previousUploadError: Error?
    ) async throws -> OpenApiHTTPResult {
        let config = OpenApiConfiguration.current
        guard let url = URL(string: path, relativeTo: config.baseURL)?.absoluteURL else {
            throw ClientError.invalidURL(path)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        guard !config.apiKey.isEmpty else { throw ClientError.missingAPIKey }
        request.setValue(config.apiKey, forHTTPHeaderField: "X-API-Key")
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")

        let filename = fileURL.lastPathComponent.isEmpty ? fallbackFilename : fileURL.lastPathComponent
        request.setValue("attachment; filename=\"\(filename.replacingOccurrences(of: "\"", with: ""))\"", forHTTPHeaderField: "Content-Disposition")
        request.setValue(filename.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? filename, forHTTPHeaderField: "X-File-Name")

        let didAccessSecurityScope = fileURL.startAccessingSecurityScopedResource()
        defer {
            if didAccessSecurityScope {
                fileURL.stopAccessingSecurityScopedResource()
            }
        }

        let (responseData, response) = try await URLSession.shared.upload(for: request, fromFile: fileURL)
        guard let http = response as? HTTPURLResponse else { throw ClientError.invalidResponse }
        let text = String(data: responseData, encoding: .utf8) ?? ""
        let object = responseData.isEmpty ? nil : try? JSONSerialization.jsonObject(with: responseData)
        guard 200..<300 ~= http.statusCode else {
            if let previousUploadError {
                throw ClientError.badStatus(
                    http.statusCode,
                    "\(text.isEmpty ? "无响应体" : text)\n原 multipart 尝试：\(previousUploadError.localizedDescription)"
                )
            }
            throw ClientError.badStatus(http.statusCode, text)
        }
        return OpenApiHTTPResult(statusCode: http.statusCode, endpoint: path, method: "POST", jsonObject: object, rawText: text)
    }

    private static func mediaContentType(for fileURL: URL) -> String {
        let ext = fileURL.pathExtension.lowercased()
        switch ext {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "gif": return "image/gif"
        case "webp": return "image/webp"
        case "mp4": return "video/mp4"
        case "mov": return "video/quicktime"
        case "m4v": return "video/x-m4v"
        case "pdf": return "application/pdf"
        case "doc": return "application/msword"
        case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        case "xls": return "application/vnd.ms-excel"
        case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        case "ppt": return "application/vnd.ms-powerpoint"
        case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        case "csv": return "text/csv"
        case "html", "htm": return "application/octet-stream"
        case "json": return "application/json"
        case "xml": return "application/xml"
        case "yaml", "yml": return "application/x-yaml"
        case "rtf": return "application/rtf"
        case "txt", "md": return "text/plain"
        case "zip": return "application/zip"
        case "rar": return "application/vnd.rar"
        case "7z": return "application/x-7z-compressed"
        case "tar": return "application/x-tar"
        case "gz": return "application/gzip"
        case "numbers": return "application/vnd.apple.numbers"
        case "pages": return "application/vnd.apple.pages"
        case "key": return "application/vnd.apple.keynote"
        default:
            if let type = UTType(filenameExtension: ext), let mime = type.preferredMIMEType {
                return mime
            }
            return "application/octet-stream"
        }
    }

    private static func voiceContentType(for fileURL: URL) -> String {
        switch fileURL.pathExtension.lowercased() {
        case "m4a", "mp4": return "audio/mp4"
        case "aac": return "audio/aac"
        case "amr": return "audio/amr"
        case "mp3": return "audio/mpeg"
        case "wav": return "audio/wav"
        default: return "application/octet-stream"
        }
    }

    static func login(username: String, password: String) async throws -> String {
        let payload: [String: Any] = [
            "userName": username,
            "username": username,
            "password": password,
            "rememberMe": true
        ]
        let result = try await request(
            "POST",
            path: "/api/auth/login",
            body: payload,
            authorizedByJWT: false,
            skipsOpenApiKey: true
        )
        guard let token = firstString(
            in: result.jsonObject,
            keys: ["token", "accessToken", "jwt"],
            nestedKeys: ["data", "result", "item"]
        ) else {
            throw ClientError.missingToken
        }
        OpenApiConfiguration.save(jwt: token)
        return token
    }

    static func createAPIKey(name: String, remark: String) async throws -> String {
        let payload: [String: Any] = [
            "name": name,
            "remark": remark
        ]
        let result = try await request(
            "POST",
            path: "/api/openapi-keys",
            body: payload,
            authorizedByJWT: true
        )
        guard let plainKey = firstString(
            in: result.jsonObject,
            keys: ["plainKey", "apiKey", "key", "secret"],
            nestedKeys: ["data", "result", "item"]
        ) else {
            throw ClientError.missingPlainKey
        }
        OpenApiConfiguration.save(apiKey: plainKey)
        return plainKey
    }

    private static func firstString(in object: Any?, keys: [String], nestedKeys: [String]) -> String? {
        guard let dictionary = object as? [String: Any] else { return nil }
        for key in keys {
            if let value = dictionary[key] as? String {
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    return trimmed
                }
            }
        }
        for nestedKey in nestedKeys {
            if let value = firstString(in: dictionary[nestedKey], keys: keys, nestedKeys: nestedKeys) {
                return value
            }
        }
        return nil
    }

    private static func request(
        _ method: String,
        path: String,
        body: Any?,
        authorizedByJWT: Bool,
        skipsOpenApiKey: Bool
    ) async throws -> OpenApiHTTPResult {
        let config = OpenApiConfiguration.current
        guard let url = URL(string: path, relativeTo: config.baseURL)?.absoluteURL else {
            throw ClientError.invalidURL(path)
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 18
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if authorizedByJWT {
            guard let jwt = config.jwt, !jwt.isEmpty else { throw ClientError.missingToken }
            request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        } else if !skipsOpenApiKey {
            guard !config.apiKey.isEmpty else { throw ClientError.missingAPIKey }
            request.setValue(config.apiKey, forHTTPHeaderField: "X-API-Key")
        }
        if let body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ClientError.invalidResponse }
        let text = String(data: data, encoding: .utf8) ?? ""
        let object = data.isEmpty ? nil : try? JSONSerialization.jsonObject(with: data)
        guard 200..<300 ~= http.statusCode else {
            throw ClientError.badStatus(http.statusCode, text)
        }
        return OpenApiHTTPResult(statusCode: http.statusCode, endpoint: path, method: method, jsonObject: object, rawText: text)
    }

    static func rows(from result: OpenApiHTTPResult, maxItems: Int = 24) -> [OpenApiFieldRow] {
        var rows = [
            OpenApiFieldRow(key: "method", value: result.method, subtitle: nil),
            OpenApiFieldRow(key: "endpoint", value: result.endpoint, subtitle: "真实 OpenAPI 请求路径。"),
            OpenApiFieldRow(key: "status", value: "\(result.statusCode)", subtitle: "HTTP 状态码。")
        ]
        rows.append(contentsOf: flatten(result.jsonObject, prefix: "data", maxItems: maxItems))
        if rows.count <= 3, !result.rawText.isEmpty {
            rows.append(OpenApiFieldRow(key: "raw", value: result.rawText, subtitle: "原始响应。"))
        }
        return rows
    }

    static func errorRows(endpoint: String, error: Error) -> [OpenApiFieldRow] {
        [
            OpenApiFieldRow(key: "endpoint", value: endpoint, subtitle: nil),
            OpenApiFieldRow(key: "success", value: "false", subtitle: nil),
            OpenApiFieldRow(key: "error", value: error.localizedDescription, subtitle: "真实接口调用失败。")
        ]
    }

    static func validateTaskResult(_ result: OpenApiHTTPResult, action: String) async throws -> String {
        guard let root = result.jsonObject as? [String: Any] else {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "响应不是 JSON；HTTP \(result.statusCode)：\(result.rawText.isEmpty ? "无响应体" : result.rawText)"
            )
        }
        if let taskID = taskID(from: root) {
            try await awaitTaskCompletion(taskID: taskID, action: action)
            return taskID
        }
        if optionalBool(root["success"]) == false {
            let message = firstNonEmptyString(in: root, keys: ["message", "error", "errorMessage", "detail", "resultCode", "code"])
            throw OpenApiMediaSendError.taskFailed(action, message.isEmpty ? "后端返回 success=false" : message)
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "后端仅返回 HTTP 受理结果，未返回 taskId/taskResultUrl，不能确认微信端已执行。HTTP \(result.statusCode)：\(result.rawText.isEmpty ? "无响应体" : result.rawText)"
        )
    }

    private static func awaitTaskCompletion(taskID: String, action: String) async throws {
        var latestStateText = ""
        var latestError: Error?
        for attempt in 0..<12 {
            if attempt > 0 {
                let delay = UInt64(700_000_000 + attempt * 300_000_000)
                try await Task.sleep(nanoseconds: delay)
            }
            do {
                let result = try await request("GET", path: "/openapi/v1/tasks/\(taskID)")
                switch taskPollState(from: result.jsonObject) {
                case .succeeded:
                    return
                case .failed(let message):
                    throw OpenApiMediaSendError.taskFailed(
                        action,
                        message.isEmpty ? "任务失败，taskId=\(taskID)" : message
                    )
                case .pending(let message):
                    latestStateText = message
                }
            } catch let error as OpenApiMediaSendError {
                throw error
            } catch {
                latestError = error
            }
        }
        if let latestError {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "任务结果查询失败，taskId=\(taskID)：\(latestError.localizedDescription)"
            )
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "任务未返回终态，taskId=\(taskID)\(latestStateText.isEmpty ? "" : "，最新状态：\(latestStateText)")"
        )
    }

    private enum TaskPollState {
        case pending(String)
        case succeeded(String)
        case failed(String)
    }

    private static func taskPollState(from object: Any?) -> TaskPollState {
        let payload = taskPayload(from: object)
        let success = optionalBool(payload["success"])
            ?? optionalBool(payload["isSuccess"])
            ?? optionalBool(payload["succeeded"])
        let resultUnknown = optionalBool(payload["resultUnknown"]) ?? false
        let message = firstNonEmptyString(
            in: payload,
            keys: ["message", "error", "errorMessage", "status", "state", "resultCode", "code", "nextStep"]
        )
        let sentMessageID = firstNonEmptyString(
            in: payload,
            keys: ["msgSvrId", "MsgSvrId", "messageSvrId", "MessageSvrId", "wechatMsgId", "WechatMsgId"]
        )
        if !sentMessageID.isEmpty {
            return .succeeded(message)
        }
        let searchable = [
            message,
            firstNonEmptyString(in: payload, keys: ["status", "state", "taskStatus"]),
            firstNonEmptyString(in: payload, keys: ["resultCode", "code"])
        ].joined(separator: " ").lowercased()
        let pendingTerms = ["pending", "running", "processing", "queued", "created", "accepted", "wait", "progress", "处理中", "排队", "等待", "已提交", "已创建", "受理", "上传中"]
        if resultUnknown || pendingTerms.contains(where: searchable.contains) {
            return .pending(message)
        }
        let successTerms = ["success", "succeed", "done", "completed", "finished", "ok", "sent", "added", "已完成", "发送成功", "添加成功", "成功", "已发送"]
        if success == true || successTerms.contains(where: searchable.contains) {
            return .succeeded(message)
        }
        let failureTerms = [
            "fail", "error", "timeout", "cancel", "reject", "denied", "disabled", "not allowed", "invalid", "not found",
            "失败", "错误", "异常", "超时", "取消", "拒绝", "未开启", "不允许", "不可用", "不存在", "无效", "频繁", "限制", "风控"
        ]
        if failureTerms.contains(where: searchable.contains) {
            return .failed(message)
        }
        return .pending(message)
    }

    private static func taskPayload(from object: Any?) -> [String: Any] {
        guard let root = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload", "task", "taskResult"] {
            if let nested = root[key] as? [String: Any] {
                return root.merging(nested) { _, nestedValue in nestedValue }
            }
        }
        return root
    }

    static func taskID(from object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            for key in ["taskId", "taskID", "task_id"] {
                let value = stringValue(dictionary[key])
                if isValidTaskID(value) { return value }
            }
            for key in ["taskResultUrl", "taskResultURL", "taskUrl", "resultUrl"] {
                let value = stringValue(dictionary[key])
                guard !value.isEmpty, let url = OpenApiDisplay.url(from: value) else { continue }
                let last = url.pathComponents.last?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if isValidTaskID(last), last != "tasks" { return last }
            }
            for key in ["data", "result", "payload", "task", "taskResult"] {
                if let nested = taskID(from: dictionary[key]) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let nested = taskID(from: item) {
                    return nested
                }
            }
        }
        return nil
    }

    private static func isValidTaskID(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        if let number = Int64(trimmed) {
            return number > 0
        }
        return trimmed.range(of: #"^[A-Za-z0-9][A-Za-z0-9_-]*$"#, options: .regularExpression) != nil
    }

    private static func firstNonEmptyString(in dictionary: [String: Any], keys: [String]) -> String {
        for key in keys {
            let value = stringValue(dictionary[key])
            if !value.isEmpty { return value }
        }
        if let data = dictionary["data"] as? [String: Any] {
            return firstNonEmptyString(in: data, keys: keys)
        }
        return ""
    }

    private static func stringValue(_ value: Any?) -> String {
        if let text = value as? String {
            return text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return ""
    }

    private static func optionalBool(_ value: Any?) -> Bool? {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let text = value as? String {
            let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if ["true", "1", "yes", "y", "success"].contains(normalized) { return true }
            if ["false", "0", "no", "n", "fail", "failed"].contains(normalized) { return false }
        }
        return nil
    }

    private static func flatten(_ value: Any?, prefix: String, maxItems: Int) -> [OpenApiFieldRow] {
        var rows: [OpenApiFieldRow] = []
        func walk(_ value: Any?, key: String) {
            guard rows.count < maxItems else { return }
            if let dict = value as? [String: Any] {
                if dict.isEmpty {
                    rows.append(OpenApiFieldRow(key: key, value: "{}", subtitle: nil))
                } else {
                    for childKey in dict.keys.sorted() {
                        walk(dict[childKey], key: key == "data" ? childKey : "\(key).\(childKey)")
                    }
                }
                return
            }
            if let array = value as? [Any] {
                rows.append(OpenApiFieldRow(key: key, value: "\(array.count) 条", subtitle: "数组响应。"))
                for (index, item) in array.prefix(6).enumerated() {
                    walk(item, key: "\(key)[\(index)]")
                }
                return
            }
            if value is NSNull || value == nil {
                rows.append(OpenApiFieldRow(key: key, value: "null", subtitle: nil))
            } else {
                rows.append(OpenApiFieldRow(key: key, value: "\(value!)", subtitle: nil))
            }
        }
        walk(value, key: prefix)
        return rows
    }
}
