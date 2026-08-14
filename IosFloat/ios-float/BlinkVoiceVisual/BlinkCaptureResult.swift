public struct BlinkCaptureResult: Codable, Equatable, Sendable {
    public let eventType: BlinkEventType
    public let startTimeMs: Int64
    public let endTimeMs: Int64
    public let durationMs: Int64
    public let confidence: Float

    public init(
        eventType: BlinkEventType,
        startTimeMs: Int64,
        endTimeMs: Int64,
        durationMs: Int64,
        confidence: Float
    ) {
        self.eventType = eventType
        self.startTimeMs = startTimeMs
        self.endTimeMs = endTimeMs
        self.durationMs = durationMs
        self.confidence = confidence
    }
}
