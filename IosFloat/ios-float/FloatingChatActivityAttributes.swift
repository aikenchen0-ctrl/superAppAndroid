import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

struct FloatingChatActivityState: Codable, Hashable {
    let appName: String
    let appBadgeText: String
    let appIconAssetName: String
    let title: String
    let subtitle: String
    let unreadCount: Int
    let imStatusText: String
    let imStatusLevel: String
    let aiTaskText: String
    let aiTaskActive: Bool
    let callStatusText: String
    let callStatusKind: String
    let mediaStatusText: String
    let mediaStatusKind: String
    let updatedAtText: String
    let messages: [FloatingChatActivityMessage]
    let draft: String

    init(
        title: String,
        subtitle: String,
        unreadCount: Int,
        messages: [FloatingChatActivityMessage],
        draft: String,
        appName: String = "只发",
        appBadgeText: String = "只",
        appIconAssetName: String = "IslandAppIcon",
        imStatusText: String = "IM 已同步",
        imStatusLevel: String = "normal",
        aiTaskText: String = "AI 待命",
        aiTaskActive: Bool = false,
        callStatusText: String = "无通话",
        callStatusKind: String = "none",
        mediaStatusText: String = "无媒体播放",
        mediaStatusKind: String = "none",
        updatedAtText: String = ""
    ) {
        self.appName = appName
        self.appBadgeText = appBadgeText
        self.appIconAssetName = appIconAssetName
        self.title = title
        self.subtitle = subtitle
        self.unreadCount = unreadCount
        self.imStatusText = imStatusText
        self.imStatusLevel = imStatusLevel
        self.aiTaskText = aiTaskText
        self.aiTaskActive = aiTaskActive
        self.callStatusText = callStatusText
        self.callStatusKind = callStatusKind
        self.mediaStatusText = mediaStatusText
        self.mediaStatusKind = mediaStatusKind
        self.updatedAtText = updatedAtText
        self.messages = messages
        self.draft = draft
    }
}

struct FloatingChatActivityMessage: Codable, Hashable {
    let sender: String
    let text: String
    let isOutgoing: Bool
}

@available(iOS 16.1, *)
struct FloatingChatActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let appName: String
        let appBadgeText: String
        let appIconAssetName: String
        let title: String
        let subtitle: String
        let unreadCount: Int
        let imStatusText: String
        let imStatusLevel: String
        let aiTaskText: String
        let aiTaskActive: Bool
        let callStatusText: String
        let callStatusKind: String
        let mediaStatusText: String
        let mediaStatusKind: String
        let updatedAtText: String
        let messages: [FloatingChatActivityMessage]
        let draft: String

        init(state: FloatingChatActivityState) {
            appName = state.appName
            appBadgeText = state.appBadgeText
            appIconAssetName = state.appIconAssetName
            title = state.title
            subtitle = state.subtitle
            unreadCount = state.unreadCount
            imStatusText = state.imStatusText
            imStatusLevel = state.imStatusLevel
            aiTaskText = state.aiTaskText
            aiTaskActive = state.aiTaskActive
            callStatusText = state.callStatusText
            callStatusKind = state.callStatusKind
            mediaStatusText = state.mediaStatusText
            mediaStatusKind = state.mediaStatusKind
            updatedAtText = state.updatedAtText
            messages = state.messages
            draft = state.draft
        }
    }

    let chatID: String
}
