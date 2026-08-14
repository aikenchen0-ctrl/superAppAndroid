import Foundation

enum MessageFlowCoordinator {
    static func outgoingMessage(
        conversationID: UUID,
        type: ChatMessageType,
        sender: ChatParticipant,
        body: String,
        detail: String,
        presentation: BubblePresentation,
        timestamp: String,
        sentAt: Date = Date(),
        attachmentURL: URL? = nil,
        isGroupConversation: Bool = false,
        richElements: [BubbleRichElement] = []
    ) -> ChatMessage {
        ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: type,
            sender: sender,
            body: body,
            detail: detail,
            isOutgoing: true,
            presentation: presentation,
            timestamp: timestamp,
            sentAt: sentAt,
            attachmentURL: attachmentURL,
            isGroupConversation: isGroupConversation,
            richElements: richElements,
            recipientAccountID: sender.isCurrentUser ? sender.id : nil
        )
    }
}
