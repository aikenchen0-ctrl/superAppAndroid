import UIKit

final class PaymentMessageRenderer: BubbleBackedMessageRenderer {
    static let supportedTypes: Set<ChatMessageType> = [
        .redPacket, .transfer, .splitBill
    ]

    init() {
        super.init(supportedTypes: Self.supportedTypes)
    }

    override func measure(model: MessageRenderModel, state: MessageRenderState, layout: MessageLayoutSpec) -> CGFloat {
        precondition(Self.supportedTypes.contains(model.type), "Renderer received an unsupported payment type")
        // Payment cards render their delivery state inside the card and retain
        // the legacy card height; the generic delivery label is not part of
        // the payment row's measured height.
        return ChatMessageBubbleView.estimatedHeight(
            for: model.source,
            width: layout.availableWidth,
            showsGroupAvatar: state.groupDisplay.showsAvatar,
            showsGroupName: state.groupDisplay.showsName,
            usesUnansweredPresentation: state.usesUnansweredPresentation,
            deliveryStatusText: nil
        )
    }
}
