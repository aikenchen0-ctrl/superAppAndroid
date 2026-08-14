final class MediaMessageRenderer: BubbleBackedMessageRenderer {
    static let supportedTypes: Set<ChatMessageType> = [
        .image, .capturedPhoto, .stickerGif, .video, .channelsVideo
    ]

    init() {
        super.init(supportedTypes: Self.supportedTypes)
    }
}
