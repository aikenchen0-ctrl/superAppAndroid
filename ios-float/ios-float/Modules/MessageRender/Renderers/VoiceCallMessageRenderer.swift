final class VoiceCallMessageRenderer: BubbleBackedMessageRenderer {
    static let supportedTypes: Set<ChatMessageType> = [
        .voice, .voiceCall, .videoCall
    ]

    init() {
        super.init(supportedTypes: Self.supportedTypes)
    }
}
