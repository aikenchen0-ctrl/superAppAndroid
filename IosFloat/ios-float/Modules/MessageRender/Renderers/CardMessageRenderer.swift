final class CardMessageRenderer: BubbleBackedMessageRenderer {
    static let supportedTypes: Set<ChatMessageType> = [
        .file, .location, .liveLocation, .contactCard, .groupInvite, .webLink,
        .article, .miniProgram, .channelsLive, .music, .favorite, .mergedForward, .coupon
    ]

    init() {
        super.init(supportedTypes: Self.supportedTypes)
    }
}
