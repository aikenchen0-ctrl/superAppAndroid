final class TextMessageRenderer: BubbleBackedMessageRenderer {
    static let supportedTypes: Set<ChatMessageType> = [
        .text, .emoji, .quotedReply, .relay, .groupNotice
    ]

    init() {
        super.init(supportedTypes: Self.supportedTypes)
    }
}
