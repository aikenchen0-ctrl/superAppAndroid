import Foundation
import UIKit
import CryptoKit

enum OpenApiConversationKind: Hashable, Codable {
    case contact
    case chatroom
}

struct OpenApiWeChatAccountContext: Hashable, Codable {
    let participantID: UUID
    let wxid: String
    let nickname: String
    let clientUuid: String
    let accountStatus: Int
    let avatarURL: String

    var isOnline: Bool { accountStatus == 1 }
    var initials: String { OpenApiDisplay.initials(from: nickname, fallback: "号") }
    var tintColor: UIColor { OpenApiDisplay.color(for: wxid) }
    var avatar: URL? { OpenApiDisplay.avatarURL(from: avatarURL) }

    func replacingAvatarURL(_ avatarURL: String) -> OpenApiWeChatAccountContext {
        OpenApiWeChatAccountContext(
            participantID: participantID,
            wxid: wxid,
            nickname: nickname,
            clientUuid: clientUuid,
            accountStatus: accountStatus,
            avatarURL: avatarURL
        )
    }
}

struct OpenApiConversationContext: Hashable, Codable {
    let participantID: UUID
    let ownerWxid: String
    let wxid: String
    let backendID: String
    let displayName: String
    let friendNo: String
    let avatarURL: String
    let remark: String
    let source: String
    let sourceExt: String
    let customerLevel: String
    let sourceChannel: String
    let profileKey: String
    let phone: String
    let notes: String
    let kind: OpenApiConversationKind
    let memberCount: Int?

    var initials: String {
        OpenApiDisplay.initials(from: displayName, fallback: kind == .chatroom ? "群" : "友")
    }

    var tintColor: UIColor {
        OpenApiDisplay.color(for: "\(ownerWxid)-\(wxid)")
    }

    var avatar: URL? { OpenApiDisplay.avatarURL(from: avatarURL) }

    func replacingDisplayName(_ displayName: String) -> OpenApiConversationContext {
        OpenApiConversationContext(
            participantID: participantID,
            ownerWxid: ownerWxid,
            wxid: wxid,
            backendID: backendID,
            displayName: displayName,
            friendNo: friendNo,
            avatarURL: avatarURL,
            remark: remark,
            source: source,
            sourceExt: sourceExt,
            customerLevel: customerLevel,
            sourceChannel: sourceChannel,
            profileKey: profileKey,
            phone: phone,
            notes: notes,
            kind: kind,
            memberCount: memberCount
        )
    }

    func replacingAvatarURL(_ avatarURL: String) -> OpenApiConversationContext {
        OpenApiConversationContext(
            participantID: participantID,
            ownerWxid: ownerWxid,
            wxid: wxid,
            backendID: backendID,
            displayName: displayName,
            friendNo: friendNo,
            avatarURL: avatarURL,
            remark: remark,
            source: source,
            sourceExt: sourceExt,
            customerLevel: customerLevel,
            sourceChannel: sourceChannel,
            profileKey: profileKey,
            phone: phone,
            notes: notes,
            kind: kind,
            memberCount: memberCount
        )
    }

    func replacingWxid(_ wxid: String) -> OpenApiConversationContext {
        OpenApiConversationContext(
            participantID: participantID,
            ownerWxid: ownerWxid,
            wxid: wxid,
            backendID: backendID,
            displayName: displayName,
            friendNo: friendNo,
            avatarURL: avatarURL,
            remark: remark,
            source: source,
            sourceExt: sourceExt,
            customerLevel: customerLevel,
            sourceChannel: sourceChannel,
            profileKey: profileKey,
            phone: phone,
            notes: notes,
            kind: kind,
            memberCount: memberCount
        )
    }
}

struct OpenApiChatRoomMemberContext: Hashable {
    let ownerWxid: String
    let chatRoomID: String
    let memberWxid: String
    /// The user's public WeChat number. Keep this separate from `memberWxid`:
    /// wxid is an internal identifier and must never be rendered as a name.
    let friendNo: String
    let nickname: String
    let displayName: String
    let avatarURL: String
    let remarks: String
    let memberRole: Int
    let isOwner: Bool
    let isAdmin: Bool

    var initials: String {
        OpenApiDisplay.initials(
            from: displayName.isEmpty ? (friendNo.isEmpty ? nickname : friendNo) : displayName,
            fallback: "员"
        )
    }

    var tintColor: UIColor {
        OpenApiDisplay.color(for: "\(ownerWxid)-\(chatRoomID)-\(memberWxid)")
    }

    var avatar: URL? { OpenApiDisplay.avatarURL(from: avatarURL) }

    var roleText: String {
        if isOwner { return "群主" }
        if isAdmin { return "管理员" }
        if memberRole > 0 { return "群成员角色 \(memberRole)" }
        return "群成员"
    }
}

struct OpenApiFriendRequestContext: Hashable, Codable {
    let participantID: UUID
    let ownerWxid: String
    let requestWxid: String
    let backendID: String
    let nickname: String
    let avatarURL: String
    let source: String
    let requestMessage: String
    let status: Int
    let requestTime: Date?

    var displayName: String {
        [nickname, requestWxid].first { !OpenApiDisplay.cleaned($0).isEmpty } ?? "好友申请"
    }

    var statusText: String {
        switch status {
        case 0: return "待处理"
        case 1: return "已通过"
        case 2: return "已拒绝"
        default: return "状态\(status)"
        }
    }

    var conversationContext: OpenApiConversationContext {
        OpenApiConversationContext(
            participantID: participantID,
            ownerWxid: ownerWxid,
            wxid: requestWxid,
            backendID: backendID,
            displayName: displayName,
            friendNo: OpenApiDisplay.friendNo(from: source),
            avatarURL: avatarURL,
            remark: "",
            source: source,
            sourceExt: "",
            customerLevel: "",
            sourceChannel: "",
            profileKey: "",
            phone: "",
            notes: requestMessage,
            kind: .contact,
            memberCount: nil
        )
    }

    func replacingAvatarURL(_ avatarURL: String) -> OpenApiFriendRequestContext {
        OpenApiFriendRequestContext(
            participantID: participantID,
            ownerWxid: ownerWxid,
            requestWxid: requestWxid,
            backendID: backendID,
            nickname: nickname,
            avatarURL: avatarURL,
            source: source,
            requestMessage: requestMessage,
            status: status,
            requestTime: requestTime
        )
    }
}

enum OpenApiVisibleTaskState: String, Hashable, Codable {
    case pending
    case succeeded
    case failed

    var displayName: String {
        switch self {
        case .pending: return "发送中"
        case .succeeded: return "已成功"
        case .failed: return "失败可重试"
        }
    }
}

struct OpenApiTaskResultContext: Hashable, Codable {
    let participantID: UUID
    let ownerWxid: String
    let conversationWxid: String
    let taskID: String
    let state: OpenApiVisibleTaskState
    let status: String
    let resultCode: String
    let message: String
    let deviceUuid: String
    let receivedAt: Date?
    let contentType: String
    let msgSvrId: String
    let rawHidden: Bool
    let avatarURL: String

    var statusText: String {
        let base = status.isEmpty ? state.rawValue : status
        return resultCode.isEmpty ? base : "\(base) / \(resultCode)"
    }

    var displayBody: String {
        if state == .pending {
            return contentType.isEmpty
                ? "OpenAPI任务回执：发送中"
                : "OpenAPI任务回执：发送中（\(contentType)）"
        }
        if rawHidden {
            return state == .succeeded ? "真实接口任务已完成，结果已按后端规则脱敏" : "真实接口任务失败，结果已按后端规则脱敏"
        }
        if !contentType.isEmpty {
            return state == .succeeded ? "真实消息已回执（\(contentType)）" : "真实消息发送失败（\(contentType)）"
        }
        if message.contains("会话同步完成") {
            return message
        }
        return message.isEmpty ? "OpenAPI任务回执：\(statusText)" : message
    }
}

struct OpenApiIMSnapshot: Codable, Hashable {
    var accounts: [OpenApiWeChatAccountContext]
    var contacts: [OpenApiConversationContext]
    var chatrooms: [OpenApiConversationContext]
    var friendRequests: [OpenApiFriendRequestContext]
    var taskResults: [OpenApiTaskResultContext]

    var conversations: [OpenApiConversationContext] {
        var seen = Set<UUID>()
        let all = contacts
            + chatrooms
            + friendRequests.map(\.conversationContext)
        return all.filter { seen.insert($0.participantID).inserted }
    }
}

enum OpenApiStableID {
    static func uuid(namespace: String, key: String) -> UUID {
        let digest = SHA256.hash(data: Data("\(namespace):\(key)".utf8))
        var bytes = Array(digest.prefix(16))
        bytes[6] = (bytes[6] & 0x0F) | 0x50
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }
}

enum OpenApiDisplay {
    static func cleaned(_ text: String?) -> String {
        let value = text?
            .replacingOccurrences(of: "\u{00a0}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? "" : value
    }

    static func validChatRoomID(_ text: String?) -> String? {
        guard let value = chatRoomID(in: text) else { return nil }
        let lowered = value.lowercased()
        guard lowered.hasSuffix("@chatroom"),
              lowered != "demo@chatroom",
              lowered.count > "@chatroom".count
        else { return nil }
        return value
    }

    static func chatRoomID(from raw: [String: Any]) -> String {
        let directKeys = [
            "chatRoomId", "chatroomId", "chatRoomID", "chat_room_id",
            "roomId", "roomID", "roomWxid", "roomWxID", "groupWxid",
            "groupId", "groupID", "conversationId", "conversationID",
            "wxid", "talker", "rawFriendId", "effectiveFriendId"
        ]
        for key in directKeys {
            if let value = validChatRoomID(stringValue(raw[key])) {
                return value
            }
        }

        let nestedPaths = [
            ["data", "chatRoomId"], ["result", "chatRoomId"], ["payload", "chatRoomId"],
            ["chatroom", "chatRoomId"], ["chatRoom", "chatRoomId"], ["room", "chatRoomId"],
            ["group", "chatRoomId"], ["conversation", "chatRoomId"], ["contact", "chatRoomId"],
            ["data", "roomId"], ["result", "roomId"], ["payload", "roomId"],
            ["data", "conversationId"], ["result", "conversationId"], ["payload", "conversationId"]
        ]
        for path in nestedPaths {
            if let value = validChatRoomID(stringValue(value(at: path, in: raw))) {
                return value
            }
        }

        for value in raw.values {
            if let found = validChatRoomID(stringValue(value)) {
                return found
            }
            if let nested = value as? [String: Any] {
                let found = chatRoomID(from: nested)
                if !found.isEmpty { return found }
            }
        }
        return ""
    }

    static func chatRoomID(in text: String?) -> String? {
        let cleanedText = cleaned(text)
        guard !cleanedText.isEmpty else { return nil }
        if cleanedText.lowercased().hasSuffix("@chatroom"),
           cleanedText.range(of: #"^[A-Za-z0-9_\-]+@chatroom$"#, options: .regularExpression) != nil {
            return cleanedText
        }
        guard let range = cleanedText.range(of: #"[A-Za-z0-9_\-]+@chatroom"#, options: .regularExpression) else {
            return nil
        }
        return String(cleanedText[range])
    }

    static func initials(from text: String, fallback: String) -> String {
        let cleanedText = cleaned(text)
        guard let first = cleanedText.first else { return fallback }
        return String(first)
    }

    static func color(for key: String) -> UIColor {
        let digest = SHA256.hash(data: Data(key.utf8))
        let bytes = Array(digest.prefix(3))
        let hue = CGFloat(bytes[0]) / 255
        let saturation = 0.48 + CGFloat(bytes[1]) / 255 * 0.22
        let brightness = 0.62 + CGFloat(bytes[2]) / 255 * 0.18
        return UIColor(hue: hue, saturation: saturation, brightness: brightness, alpha: 1)
    }

    static func friendNo(from source: String) -> String {
        source
            .components(separatedBy: ";")
            .first { $0.hasPrefix("friendNo=") }
            .map { String($0.dropFirst("friendNo=".count)) }
            .map(cleaned) ?? ""
    }

    static func contactDisplayName(remark: String, friendNo: String, nickname: String) -> String {
        firstRealDisplayName([remark, friendNo, nickname])
    }

    static func groupMemberDisplayName(remarks: String, friendNo: String, nickname: String) -> String {
        firstRealDisplayName([remarks, friendNo, nickname])
    }

    private static func firstRealDisplayName(_ candidates: [String]) -> String {
        candidates
            .map(cleaned)
            .first {
                !$0.isEmpty
                    && $0.lowercased() != "unknown"
                    && !$0.lowercased().hasPrefix("wxid_")
                    && !$0.lowercased().hasSuffix("@chatroom")
            } ?? ""
    }

    static func url(from text: String?) -> URL? {
        let cleanedText = cleaned(text)
        guard !cleanedText.isEmpty else { return nil }
        if let url = normalizedURL(from: cleanedText) {
            return url
        }
        return cleanedText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)
            .flatMap(normalizedURL(from:))
    }

    static func avatarURL(from text: String?) -> URL? {
        guard let url = url(from: text) else { return nil }
        return upgradedExternalAvatarURLIfNeeded(url)
    }

    static func avatarURLString(from raw: [String: Any]) -> String {
        var candidates: [String] = []
        let directKeys = [
            "avatar", "avatarUrl", "avatarURL", "headImgUrl", "headimgurl",
            "headImageUrl", "headImageURL", "profileImageUrl", "profileImageURL",
            "faceImageUrl", "faceImageURL", "wechatAvatar", "wechatAvatarUrl",
            "portrait", "portraitUrl", "icon", "iconUrl", "imageUrl", "thumbUrl",
            "avatar_url", "head_img_url", "headimg_url", "headImg", "headimg",
            "smallHeadImgUrl", "bigHeadImgUrl", "headUrl", "photo", "photoUrl",
            "profilePicture", "profilePictureUrl", "profilePic", "profilePicUrl",
            "profile_pic_url", "image", "image_url", "thumb", "thumb_url",
            "smallHeadImgURL", "bigHeadImgURL", "avatarThumb", "avatar_thumb",
            "headImage", "head_image", "head_img", "wechatHeadImgUrl"
        ]
        for key in directKeys {
            if let value = avatarStringValue(raw[key]) {
                candidates.append(value)
            }
        }

        let nestedPaths = [
            ["data", "avatar"],
            ["data", "avatarUrl"],
            ["data", "headImgUrl"],
            ["result", "avatar"],
            ["result", "avatarUrl"],
            ["result", "headImgUrl"],
            ["payload", "avatar"],
            ["payload", "avatarUrl"],
            ["payload", "headImgUrl"],
            ["customerProfile", "faceImageUrl"],
            ["customerProfile", "avatar"],
            ["customerProfile", "avatarUrl"],
            ["contact", "avatar"],
            ["contact", "avatarUrl"],
            ["contact", "headImgUrl"],
            ["friend", "avatar"],
            ["friend", "avatarUrl"],
            ["friend", "headImgUrl"],
            ["profile", "avatar"],
            ["profile", "avatarUrl"],
            ["profile", "headImgUrl"],
            ["profile", "faceImageUrl"],
            ["user", "avatar"],
            ["user", "avatarUrl"],
            ["user", "headImgUrl"],
            ["account", "avatar"],
            ["account", "avatarUrl"],
            ["account", "headImgUrl"],
            ["wechatAccount", "avatar"],
            ["wechatAccount", "avatarUrl"],
            ["wechatAccount", "headImgUrl"],
            ["message", "avatar"],
            ["message", "avatarUrl"],
            ["message", "headImgUrl"],
            ["member", "avatar"],
            ["member", "avatarUrl"],
            ["member", "avatarURL"],
            ["member", "headImgUrl"],
            ["member", "headimgurl"],
            ["memberInfo", "avatar"],
            ["memberInfo", "avatarUrl"],
            ["memberInfo", "headImgUrl"],
            ["memberProfile", "avatar"],
            ["memberProfile", "avatarUrl"],
            ["memberProfile", "headImgUrl"],
            ["sender", "avatar"],
            ["sender", "avatarUrl"],
            ["sender", "headImgUrl"]
        ]
        for path in nestedPaths {
            if let value = avatarStringValue(value(at: path, in: raw)) {
                candidates.append(value)
            }
        }

        if let valid = candidates.first(where: { avatarURL(from: $0) != nil }) {
            return valid
        }
        return candidates.first.map(cleaned) ?? ""
    }

    static func contactAvatarURLString(from raw: [String: Any]) -> String {
        preferredAvatarURLString(
            from: raw,
            directKeys: [
                "avatarUrl", "avatarURL", "avatar", "headImgUrl", "headimgurl",
                "headImageUrl", "smallHeadImgUrl", "bigHeadImgUrl",
                "wechatAvatarUrl", "wechatAvatar", "portraitUrl", "portrait"
            ],
            nestedPaths: [
                ["contact", "avatarUrl"], ["contact", "avatar"], ["contact", "headImgUrl"],
                ["friend", "avatarUrl"], ["friend", "avatar"], ["friend", "headImgUrl"],
                ["user", "avatarUrl"], ["user", "avatar"], ["user", "headImgUrl"],
                ["profile", "avatarUrl"], ["profile", "avatar"], ["profile", "headImgUrl"]
            ]
        )
    }

    static func groupMemberAvatarURLString(from raw: [String: Any]) -> String {
        preferredAvatarURLString(
            from: raw,
            directKeys: [
                "avatarUrl", "avatarURL", "avatar", "headImgUrl", "headimgurl",
                "memberAvatarUrl", "memberAvatar", "memberHeadImgUrl",
                "headImageUrl", "smallHeadImgUrl", "bigHeadImgUrl"
            ],
            nestedPaths: [
                ["member", "avatarUrl"], ["member", "avatar"], ["member", "headImgUrl"],
                ["memberInfo", "avatarUrl"], ["memberInfo", "avatar"], ["memberInfo", "headImgUrl"],
                ["memberProfile", "avatarUrl"], ["memberProfile", "avatar"], ["memberProfile", "headImgUrl"],
                ["contact", "avatarUrl"], ["contact", "avatar"], ["contact", "headImgUrl"],
                ["user", "avatarUrl"], ["user", "avatar"], ["user", "headImgUrl"],
                ["sender", "avatarUrl"], ["sender", "avatar"], ["sender", "headImgUrl"]
            ]
        )
    }

    private static func preferredAvatarURLString(
        from raw: [String: Any],
        directKeys: [String],
        nestedPaths: [[String]]
    ) -> String {
        var candidates = directKeys.compactMap { avatarStringValue(raw[$0]) }
        candidates.append(contentsOf: nestedPaths.compactMap { avatarStringValue(value(at: $0, in: raw)) })
        if let valid = candidates.first(where: { avatarURL(from: $0) != nil }) {
            return valid
        }
        for key in ["data", "result", "payload", "item", "record"] {
            if let nested = raw[key] as? [String: Any] {
                let nestedAvatar = preferredAvatarURLString(
                    from: nested,
                    directKeys: directKeys,
                    nestedPaths: nestedPaths
                )
                if !nestedAvatar.isEmpty { return nestedAvatar }
            }
        }
        return ""
    }

    private static func normalizedURL(from text: String) -> URL? {
        let baseURL = OpenApiConfiguration.current.baseURL
        if text.hasPrefix("//") {
            let scheme = baseURL.scheme ?? "https"
            return URL(string: "\(scheme):\(text)")
        }
        if let direct = URL(string: text),
           let scheme = direct.scheme?.lowercased(),
           ["http", "https", "file"].contains(scheme) {
            return direct
        }
        if text.hasPrefix("/") {
            return URL(string: text, relativeTo: baseURL)?.absoluteURL
        }
        if looksLikeHostPath(text) {
        return URL(string: "https://\(text)")
        }
        guard text.contains("/") else { return nil }
        return URL(string: text, relativeTo: baseURL)?.absoluteURL
    }

    private static func upgradedExternalAvatarURLIfNeeded(_ url: URL) -> URL {
        guard url.scheme?.lowercased() == "http",
              let host = url.host?.lowercased(),
              host != OpenApiConfiguration.current.baseURL.host?.lowercased()
        else { return url }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = "https"
        return components?.url ?? url
    }

    private static func looksLikeHostPath(_ text: String) -> Bool {
        guard !text.contains(" "), !text.contains("@") else { return false }
        let host = text.split(separator: "/", maxSplits: 1).first.map(String.init) ?? text
        return host.contains(".") && !host.hasPrefix(".") && !host.hasSuffix(".")
    }

    private static func avatarStringValue(_ value: Any?) -> String? {
        if let string = value as? String {
            let cleanedString = cleaned(string)
            return cleanedString.isEmpty ? nil : cleanedString
        }
        if let number = value as? NSNumber {
            let cleanedString = cleaned(number.stringValue)
            return cleanedString.isEmpty ? nil : cleanedString
        }
        if let dictionary = value as? [String: Any] {
            for key in [
                "url", "href", "uri", "value", "src",
                "avatar", "avatarUrl", "headImgUrl", "headimgurl",
                "faceImageUrl", "imageUrl", "thumbUrl"
            ] {
                if let nested = avatarStringValue(dictionary[key]) {
                    return nested
                }
            }
        }
        if let array = value as? [Any] {
            for item in array {
                if let nested = avatarStringValue(item) {
                    return nested
                }
            }
        }
        return nil
    }

    private static func stringValue(_ value: Any?) -> String {
        if let string = value as? String {
            return cleaned(string)
        }
        if let number = value as? NSNumber {
            return cleaned(number.stringValue)
        }
        return ""
    }

    private static func value(at path: [String], in raw: [String: Any]) -> Any? {
        var current: Any? = raw
        for key in path {
            guard let dictionary = current as? [String: Any] else { return nil }
            current = dictionary[key]
        }
        return current
    }
}
