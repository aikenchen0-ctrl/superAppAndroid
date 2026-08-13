import UIKit

struct MessageRenderModel: Hashable {
    let source: ChatMessage

    var id: UUID { source.id }
    var type: ChatMessageType { source.type }
    var conversationID: UUID { source.conversationID }
}

struct MessageSelectionState: Hashable {
    var isSelecting: Bool
    var isSelected: Bool

    static let inactive = MessageSelectionState(isSelecting: false, isSelected: false)
}

struct MessageGroupDisplayState: Hashable {
    var showsAvatar: Bool
    var showsName: Bool

    static let visible = MessageGroupDisplayState(showsAvatar: true, showsName: true)
}

struct MessageRenderState: Hashable {
    var isVoicePlaying: Bool
    var isAIStreaming: Bool
    var screenshotMediaBlurEnabled: Bool
    var mediaPrivacyBlurEnabled: Bool
    var uses3DAppearance: Bool
    var usesUnansweredPresentation: Bool
    var deliveryStatusText: String?
    var selection: MessageSelectionState
    var groupDisplay: MessageGroupDisplayState

    static let legacyDefault = MessageRenderState(
        isVoicePlaying: false,
        isAIStreaming: false,
        screenshotMediaBlurEnabled: false,
        mediaPrivacyBlurEnabled: false,
        uses3DAppearance: false,
        usesUnansweredPresentation: false,
        deliveryStatusText: nil,
        selection: .inactive,
        groupDisplay: .visible
    )
}

struct MessageLayoutSpec: Equatable {
    var availableWidth: CGFloat
    var maximumBubbleWidthRatio: CGFloat
    var minimumBubbleWidth: CGFloat
    var avatarSize: CGSize
    var rowSpacing: CGFloat

    static func legacy(availableWidth: CGFloat) -> MessageLayoutSpec {
        MessageLayoutSpec(
            availableWidth: availableWidth,
            maximumBubbleWidthRatio: 0.82,
            minimumBubbleWidth: 126,
            avatarSize: CGSize(width: 36, height: 36),
            rowSpacing: 8
        )
    }
}

struct MessageStyleTokens {
    struct Typography {
        let body: UIFont
        let detail: UIFont
        let meta: UIFont
        let sender: UIFont
        let inlineSender: UIFont
        let cardTitle: UIFont
    }

    struct Spacing {
        let content: CGFloat
        let titleRow: CGFloat
        let richElements: CGFloat
        let avatarGap: CGFloat
    }

    struct Radius {
        let bubble: CGFloat
        let card: CGFloat
        let media: CGFloat
        let avatar: CGFloat
    }

    struct Colors {
        let text: UIColor
        let detail: UIColor
        let muted: UIColor
        let ai: UIColor
        let link: UIColor
    }

    struct Metrics {
        let avatarSize: CGSize
        let maximumBubbleWidthRatio: CGFloat
        let minimumBubbleWidth: CGFloat
    }

    let typography: Typography
    let spacing: Spacing
    let radius: Radius
    let colors: Colors
    let metrics: Metrics

    static let legacy = MessageStyleTokens(
        typography: Typography(
            body: .systemFont(ofSize: 14.5),
            detail: .systemFont(ofSize: 12),
            meta: .systemFont(ofSize: 10, weight: .medium),
            sender: .systemFont(ofSize: 11, weight: .semibold),
            inlineSender: .systemFont(ofSize: 13, weight: .semibold),
            cardTitle: .systemFont(ofSize: 13, weight: .semibold)
        ),
        spacing: Spacing(content: 7, titleRow: 7, richElements: 7, avatarGap: 8),
        radius: Radius(bubble: 15, card: 12, media: 12, avatar: 10),
        colors: Colors(
            text: UIColor(red: 0.03, green: 0.27, blue: 0.34, alpha: 1),
            detail: UIColor(red: 0.12, green: 0.31, blue: 0.36, alpha: 0.78),
            muted: UIColor(red: 0.14, green: 0.30, blue: 0.35, alpha: 0.58),
            ai: UIColor(red: 0.95, green: 0.68, blue: 0.18, alpha: 1),
            link: .systemBlue
        ),
        metrics: Metrics(
            avatarSize: CGSize(width: 36, height: 36),
            maximumBubbleWidthRatio: 0.82,
            minimumBubbleWidth: 126
        )
    )
}

enum MessageMediaSource: Equatable {
    case attachment(URL?)
    case richElement(index: Int, url: URL?)
}

enum MessageInteractionEvent: Equatable {
    case openURL(URL)
    case openInlineCard(kind: BubbleCardKind, url: URL?)
    case openMedia(messageID: UUID, source: MessageMediaSource)
    case playVoice(messageID: UUID)
    case openContact(UUID?)
    case openMergedForward(messageID: UUID)
    case longPress(messageID: UUID, sourceRect: CGRect)
    case beginDrag(messageID: UUID)
    case toggleSelection(messageID: UUID)
}

protocol MessageRendering: AnyObject {
    var onEvent: ((MessageInteractionEvent) -> Void)? { get set }
    var view: UIView { get }
    func render(model: MessageRenderModel, state: MessageRenderState, style: MessageStyleTokens)
    func measure(model: MessageRenderModel, state: MessageRenderState, layout: MessageLayoutSpec) -> CGFloat
    func resetForReuse()
}
