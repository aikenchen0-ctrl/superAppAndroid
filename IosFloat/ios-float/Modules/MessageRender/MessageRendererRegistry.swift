import UIKit

enum MessageRendererVersion: Equatable {
    case legacy
    case componentized
}

enum MessageRendererGroup: String, CaseIterable {
    case text
    case media
    case voiceAndCall
    case card
    case payment
    case system
}

class BubbleBackedMessageRenderer: MessageRendering {
    var onEvent: ((MessageInteractionEvent) -> Void)?
    let bubbleView = ChatMessageBubbleView()
    var view: UIView { bubbleView }
    private let supportedTypes: Set<ChatMessageType>

    init(supportedTypes: Set<ChatMessageType>) {
        self.supportedTypes = supportedTypes
    }

    func render(model: MessageRenderModel, state: MessageRenderState, style: MessageStyleTokens) {
        precondition(supportedTypes.contains(model.type), "Renderer received an unsupported message type")
        bubbleView.configure(
            with: model.source,
            isVoicePlaying: state.isVoicePlaying,
            isAIStreaming: state.isAIStreaming,
            screenshotMediaBlurEnabled: state.screenshotMediaBlurEnabled,
            mediaPrivacyBlurEnabled: state.mediaPrivacyBlurEnabled,
            uses3DBubbleAppearance: state.uses3DAppearance,
            deliveryStatusText: state.deliveryStatusText,
            showsGroupAvatar: state.groupDisplay.showsAvatar,
            showsGroupName: state.groupDisplay.showsName,
            usesUnansweredPresentation: state.usesUnansweredPresentation
        )
    }

    func measure(model: MessageRenderModel, state: MessageRenderState, layout: MessageLayoutSpec) -> CGFloat {
        precondition(supportedTypes.contains(model.type), "Renderer received an unsupported message type")
        return ChatMessageBubbleView.estimatedHeight(
            for: model.source,
            width: layout.availableWidth,
            showsGroupAvatar: state.groupDisplay.showsAvatar,
            showsGroupName: state.groupDisplay.showsName,
            usesUnansweredPresentation: state.usesUnansweredPresentation,
            deliveryStatusText: state.deliveryStatusText
        )
    }

    func resetForReuse() {
        onEvent = nil
        bubbleView.reset()
    }
}

final class LegacyMessageRenderer: BubbleBackedMessageRenderer {
    init() {
        super.init(supportedTypes: Set(ChatMessageType.allCases))
    }
}

final class MessageRendererRegistry {
    typealias Factory = () -> MessageRendering
    typealias ReuseIdentifier = ObjectIdentifier

    static let `default`: MessageRendererRegistry = {
        let registry = MessageRendererRegistry()
        registry.register(
            SystemMessageRenderer.self,
            factory: { SystemMessageRenderer() },
            version: .componentized,
            for: [.system]
        )
        registry.register(
            TextMessageRenderer.self,
            factory: { TextMessageRenderer() },
            version: .componentized,
            for: Array(TextMessageRenderer.supportedTypes)
        )
        registry.register(
            MediaMessageRenderer.self,
            factory: { MediaMessageRenderer() },
            version: .componentized,
            for: Array(MediaMessageRenderer.supportedTypes)
        )
        registry.register(
            VoiceCallMessageRenderer.self,
            factory: { VoiceCallMessageRenderer() },
            version: .componentized,
            for: Array(VoiceCallMessageRenderer.supportedTypes)
        )
        registry.register(
            CardMessageRenderer.self,
            factory: { CardMessageRenderer() },
            version: .componentized,
            for: Array(CardMessageRenderer.supportedTypes)
        )
        registry.register(
            PaymentMessageRenderer.self,
            factory: { PaymentMessageRenderer() },
            version: .componentized,
            for: Array(PaymentMessageRenderer.supportedTypes)
        )
        return registry
    }()

    private var factories: [ChatMessageType: Factory]
    private var versions: [ChatMessageType: MessageRendererVersion]
    private var reuseIdentifiers: [ChatMessageType: ReuseIdentifier]

    init(types: [ChatMessageType] = ChatMessageType.allCases) {
        factories = Dictionary(uniqueKeysWithValues: types.map { ($0, { LegacyMessageRenderer() }) })
        versions = Dictionary(uniqueKeysWithValues: types.map { ($0, .legacy) })
        let legacyIdentifier = ObjectIdentifier(LegacyMessageRenderer.self)
        reuseIdentifiers = Dictionary(uniqueKeysWithValues: types.map { ($0, legacyIdentifier) })
    }

    var registeredTypes: Set<ChatMessageType> {
        Set(factories.keys)
    }

    var isComplete: Bool {
        registeredTypes == Set(ChatMessageType.allCases)
    }

    func renderer(for type: ChatMessageType) -> MessageRendering {
        (factories[type] ?? { LegacyMessageRenderer() })()
    }

    func version(for type: ChatMessageType) -> MessageRendererVersion {
        versions[type] ?? .legacy
    }

    func reuseIdentifier(for type: ChatMessageType) -> ReuseIdentifier {
        reuseIdentifiers[type] ?? ObjectIdentifier(LegacyMessageRenderer.self)
    }

    func group(for type: ChatMessageType) -> MessageRendererGroup {
        switch type {
        case .text, .emoji, .quotedReply, .relay, .groupNotice:
            return .text
        case .image, .capturedPhoto, .stickerGif, .video, .channelsVideo:
            return .media
        case .voice, .voiceCall, .videoCall:
            return .voiceAndCall
        case .file, .location, .liveLocation, .contactCard, .groupInvite, .webLink,
             .article, .miniProgram, .channelsLive, .music, .favorite, .mergedForward, .coupon:
            return .card
        case .redPacket, .transfer, .splitBill:
            return .payment
        case .system:
            return .system
        }
    }

    func register<Renderer: MessageRendering>(
        _ rendererType: Renderer.Type,
        factory: @escaping () -> Renderer,
        version: MessageRendererVersion,
        for types: [ChatMessageType]
    ) {
        let reuseIdentifier = ObjectIdentifier(rendererType)
        for type in types {
            factories[type] = { factory() }
            versions[type] = version
            reuseIdentifiers[type] = reuseIdentifier
        }
    }
}
