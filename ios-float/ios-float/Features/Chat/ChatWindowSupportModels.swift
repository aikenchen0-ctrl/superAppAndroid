import UIKit
import Security

enum UnansweredAccountScope: Equatable {
    case all
    case account(UUID)

    var accountID: UUID? {
        guard case .account(let accountID) = self else { return nil }
        return accountID
    }
}

struct UnansweredItemID: Hashable {
    let conversationID: UUID
    let discriminator: String
}

struct UnansweredTimelineFocus: Equatable {
    let itemID: UnansweredItemID
    let messageID: UUID
}

struct UnansweredNavigationOrigin: Equatable {
    let scope: UnansweredAccountScope
    let sourceFocus: UnansweredTimelineFocus
    let accountID: UUID
    let candidateItemIDs: [UnansweredItemID]
    let contentOffsetY: CGFloat
    let completesItemOnSend: Bool
}

struct UnansweredCompletionAnimationSnapshot {
    let messageIDs: [UUID]
    let leftParticipantIDs: [UUID]
    let selectedAccountID: UUID
    let contentOffsetY: CGFloat
}

enum ChatWindowRoute: Equatable {
    case unanswered(scope: UnansweredAccountScope, focus: UnansweredTimelineFocus?)
    case accountDirectory(accountID: UUID)
    case conversation(
        accountID: UUID,
        conversationID: UUID,
        unansweredOrigin: UnansweredNavigationOrigin?,
        directoryOriginAccountID: UUID?
    )

    var unansweredScope: UnansweredAccountScope? {
        guard case .unanswered(let scope, _) = self else { return nil }
        return scope
    }

    var unansweredFocus: UnansweredTimelineFocus? {
        guard case .unanswered(_, let focus) = self else { return nil }
        return focus
    }

    var unansweredOrigin: UnansweredNavigationOrigin? {
        guard case .conversation(_, _, let origin, _) = self else { return nil }
        return origin
    }

    var accountDirectoryID: UUID? {
        switch self {
        case .accountDirectory(let accountID):
            return accountID
        case .conversation(_, _, _, let accountID):
            return accountID
        case .unanswered:
            return nil
        }
    }
}

enum UnansweredRoutePlanner {
    static func destinationSelectingAccount(
        tappedAccountID: UUID,
        hasUnansweredMessages: Bool
    ) -> ChatWindowRoute {
        hasUnansweredMessages
            ? timelineSelectingAccount(tappedAccountID: tappedAccountID)
            : .accountDirectory(accountID: tappedAccountID)
    }

    static func timelineSelectingAccount(
        tappedAccountID: UUID
    ) -> ChatWindowRoute {
        .unanswered(scope: .account(tappedAccountID), focus: nil)
    }

    static func destinationAfterTimelineBack(
        from scope: UnansweredAccountScope
    ) -> ChatWindowRoute? {
        guard let accountID = scope.accountID else { return nil }
        return .accountDirectory(accountID: accountID)
    }

    static func conversation(
        scope: UnansweredAccountScope,
        item: UnansweredItem,
        messageID: UUID,
        accountID: UUID,
        candidateItemIDs: [UnansweredItemID],
        contentOffsetY: CGFloat,
        completesItemOnSend: Bool = true
    ) -> ChatWindowRoute {
        let focus = UnansweredTimelineFocus(itemID: item.id, messageID: messageID)
        return .conversation(
            accountID: accountID,
            conversationID: item.conversationID,
            unansweredOrigin: UnansweredNavigationOrigin(
                scope: scope,
                sourceFocus: focus,
                accountID: accountID,
                candidateItemIDs: candidateItemIDs,
                contentOffsetY: contentOffsetY,
                completesItemOnSend: completesItemOnSend
            ),
            directoryOriginAccountID: nil
        )
    }

    static func timelineAfterBack(from route: ChatWindowRoute) -> ChatWindowRoute {
        guard let origin = route.unansweredOrigin else {
            return .unanswered(scope: .all, focus: nil)
        }
        return .unanswered(scope: origin.scope, focus: origin.sourceFocus)
    }
}

enum UnansweredReplyCompletionPolicy {
    static func shouldComplete(
        message: ChatMessage,
        origin: UnansweredNavigationOrigin,
        isAutoReplyPrediction: Bool
    ) -> Bool {
        message.isOutgoing
            && origin.completesItemOnSend
            && !message.isAI
            && !isAutoReplyPrediction
            && message.conversationID == origin.sourceFocus.itemID.conversationID
            && (message.sender.id == origin.accountID
                || message.recipientAccountID == origin.accountID)
    }
}

enum AppSecretStore {
    private static let service = Bundle.main.bundleIdentifier ?? "ios-float"

    static func string(forKey key: String) -> String? {
        var query = baseQuery(forKey: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let value = String(data: data, encoding: .utf8),
              !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return nil
        }
        return value
    }

    static func setString(_ value: String?, forKey key: String) {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty,
              let data = value.data(using: .utf8)
        else {
            delete(forKey: key)
            return
        }

        var query = baseQuery(forKey: key)
        let attributes: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            query[kSecValueData as String] = data
            query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(query as CFDictionary, nil)
        }
    }

    static func migrateLegacySecret(forKey key: String) -> String? {
        let legacy = ChatSQLiteStore.shared.string(forKey: key)
            ?? UserDefaults.standard.string(forKey: key)
        let trimmed = legacy?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty {
            // A newly supplied legacy value is an explicit update and must
            // replace an older Keychain item before the plaintext is removed.
            setString(trimmed, forKey: key)
            clearLegacySecret(forKey: key)
            return trimmed
        }
        return string(forKey: key)
    }

    static func clearLegacySecret(forKey key: String) {
        ChatSQLiteStore.shared.setString(nil, forKey: key)
        UserDefaults.standard.removeObject(forKey: key)
    }

    private static func delete(forKey key: String) {
        SecItemDelete(baseQuery(forKey: key) as CFDictionary)
    }

    private static func baseQuery(forKey key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}

enum OpenApiIMBootstrapState {
    case loading
    case loaded
    case empty
    case failed(String)
}

enum UnansweredEmptyStatePolicy {
    static func shouldShow(
        isHomeTimeline: Bool,
        messageCount: Int,
        bootstrapState: OpenApiIMBootstrapState,
        hasLoadedSnapshot: Bool
    ) -> Bool {
        guard isHomeTimeline, messageCount == 0 else { return false }
        switch bootstrapState {
        case .loaded:
            return true
        case .loading:
            return hasLoadedSnapshot
        case .empty, .failed:
            return false
        }
    }
}

enum OpenApiDeliveryStatusPresentation {
    static func text(state: OpenApiVisibleTaskState, note: String) -> String? {
        switch state {
        case .pending:
            let isAwaitingConfirmation = note.contains("已提交")
                || note.contains("暂未返回终态")
                || note.contains("结果待确认")
                || note.contains("等待后台回执")
            return isAwaitingConfirmation ? "等待确认" : "发送中"
        case .succeeded:
            return nil
        case .failed:
            return "失败，长按重试"
        }
    }

    static func mergedTaskIDs(_ existing: [String], _ incoming: [String]) -> [String] {
        (existing + incoming).reduce(into: [String]()) { result, taskID in
            let cleaned = taskID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty, !result.contains(cleaned) else { return }
            result.append(cleaned)
        }
    }
}

final class UnsafeSendableBox<Value>: @unchecked Sendable {
    let value: Value

    init(_ value: Value) {
        self.value = value
    }
}

extension UIButton {
    func applyContentInsets(top: CGFloat, leading: CGFloat, bottom: CGFloat, trailing: CGFloat) {
        var currentConfiguration = configuration ?? .plain()
        currentConfiguration.contentInsets = NSDirectionalEdgeInsets(
            top: top,
            leading: leading,
            bottom: bottom,
            trailing: trailing
        )
        configuration = currentConfiguration
    }

    func applyImagePadding(_ padding: CGFloat) {
        var currentConfiguration = configuration ?? .plain()
        currentConfiguration.imagePadding = padding
        configuration = currentConfiguration
    }
}

enum FavoriteCategory: String, CaseIterable, Codable {
    case recent = "最近使用"
    case media = "图片与视频"
    case file = "文件"
    case link = "链接"
    case text = "文本"
    case chat = "聊天记录"

    var shortTitle: String { rawValue }
}

enum FavoritePreviewKind: String, Codable {
    case image
    case video
    case file
    case link
    case text
    case chatRecord
}

struct FavoriteShareItem: Codable {
    let title: String
    let subtitle: String
    let detail: String
    let category: FavoriteCategory
    let dateText: String
    let source: String
    let previewKind: FavoritePreviewKind
}

enum MediaAccessRequestStatus: String, Codable {
    case pending = "待同意"
    case approved = "已同意"
}

enum MediaAccessState: String {
    case viewable = "可查看"
    case requestable = "可申请"
    case requested = "已申请"
    case approved = "已同意"
}

struct MediaAccessRequest: Hashable, Codable {
    let id: UUID
    let key: String
    let title: String
    let url: String
    let kindTitle: String
    let scope: BubbleAccessScope
    let shareURL: String
    let expiresAt: Date?
    let requesterName: String
    let createdAt: Date
    var status: MediaAccessRequestStatus

    var expiryText: String {
        guard let expiresAt else { return ResourceExpiryPolicy.never.rawValue }
        return ChatMessage.displayTimestamp(for: expiresAt)
    }
}

struct PendingAttachment {
    let type: ChatMessageType
    let url: URL
    let title: String
    let detail: String
    let accessScope: BubbleAccessScope
    let expiryPolicy: ResourceExpiryPolicy
}

struct SelectedImageAttachment {
    let image: UIImage
    let type: ChatMessageType
    let title: String
    let detail: String
}

enum GlobalSearchCategory: Int, CaseIterable {
    case all
    case contact
    case group
    case message
    case file
    case moment
    case finder
    case knowledge

    var title: String {
        switch self {
        case .all: return "全部"
        case .contact: return "联系人"
        case .group: return "群聊"
        case .message: return "消息"
        case .file: return "文件"
        case .moment: return "朋友圈"
        case .finder: return "视频号"
        case .knowledge: return "知识库"
        }
    }

    var symbolName: String {
        switch self {
        case .all: return "magnifyingglass"
        case .contact: return "person.crop.circle"
        case .group: return "person.2.circle"
        case .message: return "bubble.left.and.bubble.right"
        case .file: return "doc.text"
        case .moment: return "camera.aperture"
        case .finder: return "play.rectangle"
        case .knowledge: return "books.vertical"
        }
    }

    var tintColor: UIColor {
        switch self {
        case .all: return UIColor(red: 0.16, green: 0.18, blue: 0.22, alpha: 1)
        case .contact: return UIColor(red: 0.12, green: 0.50, blue: 0.34, alpha: 1)
        case .group: return UIColor(red: 0.18, green: 0.43, blue: 0.74, alpha: 1)
        case .message: return UIColor(red: 0.36, green: 0.38, blue: 0.46, alpha: 1)
        case .file: return UIColor(red: 0.84, green: 0.42, blue: 0.12, alpha: 1)
        case .moment: return UIColor(red: 0.66, green: 0.32, blue: 0.82, alpha: 1)
        case .finder: return UIColor(red: 0.14, green: 0.14, blue: 0.16, alpha: 1)
        case .knowledge: return UIColor(red: 0.12, green: 0.48, blue: 0.58, alpha: 1)
        }
    }
}

struct GlobalSearchResult: Hashable {
    let id: String
    let category: GlobalSearchCategory
    let title: String
    let subtitle: String
    let snippet: String
    let targetID: UUID?
    let conversationID: UUID?
    let messageID: UUID?
    let messageType: ChatMessageType?
    let detail: String
    let updatedAt: Date
}

struct GlobalSearchIndexSnapshot {
    let friends: [ChatParticipant]
    let participants: [ChatParticipant]
    let messages: [ChatMessage]
    let contactCards: [UUID: ContactCardProfile]
    let participantRemarks: [UUID: String]
    let selectedAccountID: UUID
    let accountName: String
}

extension UIImage {
    static func chatRobotIcon(
        size: CGSize = CGSize(width: 28, height: 28),
        color: UIColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
    ) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let rect = CGRect(origin: .zero, size: size)
            let scale = min(size.width, size.height) / 28
            let bodyRect = CGRect(
                x: rect.midX - 9 * scale,
                y: rect.midY - 5 * scale,
                width: 18 * scale,
                height: 13 * scale
            )
            let path = UIBezierPath(roundedRect: bodyRect, cornerRadius: 3.2 * scale)
            color.setStroke()
            path.lineWidth = 2.4 * scale
            path.lineJoinStyle = .round
            path.stroke()

            let stroke = UIBezierPath()
            stroke.lineWidth = 2.4 * scale
            stroke.lineCapStyle = .round
            stroke.move(to: CGPoint(x: rect.midX, y: bodyRect.minY))
            stroke.addLine(to: CGPoint(x: rect.midX, y: bodyRect.minY - 4.5 * scale))
            stroke.move(to: CGPoint(x: rect.midX - 3 * scale, y: bodyRect.minY - 4.5 * scale))
            stroke.addLine(to: CGPoint(x: rect.midX + 3 * scale, y: bodyRect.minY - 4.5 * scale))
            stroke.move(to: CGPoint(x: bodyRect.minX - 4 * scale, y: bodyRect.midY))
            stroke.addLine(to: CGPoint(x: bodyRect.minX, y: bodyRect.midY))
            stroke.move(to: CGPoint(x: bodyRect.maxX, y: bodyRect.midY))
            stroke.addLine(to: CGPoint(x: bodyRect.maxX + 4 * scale, y: bodyRect.midY))
            stroke.stroke()

            let eyeSize = CGSize(width: 2.8 * scale, height: 4.2 * scale)
            let leftEye = CGRect(
                x: rect.midX - 5.5 * scale,
                y: bodyRect.midY - eyeSize.height / 2,
                width: eyeSize.width,
                height: eyeSize.height
            )
            let rightEye = CGRect(
                x: rect.midX + 2.7 * scale,
                y: bodyRect.midY - eyeSize.height / 2,
                width: eyeSize.width,
                height: eyeSize.height
            )
            color.setFill()
            UIBezierPath(roundedRect: leftEye, cornerRadius: 1.2 * scale).fill()
            UIBezierPath(roundedRect: rightEye, cornerRadius: 1.2 * scale).fill()
        }.withRenderingMode(.alwaysOriginal)
    }
}

struct GroupDisplaySettings: Codable {
    var showsAvatar = true
    var showsName = true
    var remark = ""
    var myGroupName = ""
}

enum AppLanguage: String {
    case zh
    case en

    func text(_ zh: String, _ en: String) -> String {
        self == .zh ? zh : en
    }

    var toggleTitle: String {
        text("切换到 English", "Switch to 中文")
    }
}

enum LeftSidebarDisplayMode: String, CaseIterable {
    case friendsAndGroups
    case friends
    case groups

    var title: String {
        switch self {
        case .friendsAndGroups: return "好友和群聊"
        case .friends: return "好友用户"
        case .groups: return "群聊"
        }
    }

    var toolTitle: String {
        switch self {
        case .friendsAndGroups: return "左侧全部"
        case .friends: return "只看好友"
        case .groups: return "只看群聊"
        }
    }

    var symbolName: String {
        switch self {
        case .friendsAndGroups: return "person.2.fill"
        case .friends: return "person.crop.circle"
        case .groups: return "person.3.fill"
        }
    }

    var tintColor: UIColor {
        switch self {
        case .friendsAndGroups: return UIColor(red: 0.22, green: 0.50, blue: 0.68, alpha: 1)
        case .friends: return UIColor(red: 0.14, green: 0.56, blue: 0.38, alpha: 1)
        case .groups: return UIColor(red: 0.50, green: 0.38, blue: 0.74, alpha: 1)
        }
    }
}
