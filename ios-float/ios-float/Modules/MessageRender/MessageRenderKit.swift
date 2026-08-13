import UIKit

protocol MessageRenderKitProviding {
    func estimatedMessageHeight(
        _ message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool,
        showsGroupName: Bool,
        usesUnansweredPresentation: Bool,
        deliveryStatusText: String?
    ) -> CGFloat
    func bubbleSchema(for message: ChatMessage) -> MessageBubbleSchema
}

struct DefaultMessageRenderKitAdapter: MessageRenderKitProviding {
    func estimatedMessageHeight(
        _ message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool,
        showsGroupName: Bool,
        usesUnansweredPresentation: Bool,
        deliveryStatusText: String?
    ) -> CGFloat {
        ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: width,
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation,
            deliveryStatusText: deliveryStatusText
        )
    }

    func bubbleSchema(for message: ChatMessage) -> MessageBubbleSchema {
        MessageBubbleSchema.from(message: message)
    }
}

enum MessageRenderKitDemo {
    static func schema(for message: ChatMessage) -> MessageBubbleSchema {
        DefaultMessageRenderKitAdapter().bubbleSchema(for: message)
    }
}
