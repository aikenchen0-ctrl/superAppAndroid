public struct BlinkCaptureOptions: Equatable, Sendable {
    public var earCloseThreshold: Float
    public var earOpenThreshold: Float
    public var doubleBlinkWindowMs: Int64
    public var longCloseMinMs: Int64
    public var minShortBlinkMs: Int64
    public var maxShortBlinkMs: Int64
    public var noFaceResetMs: Int64
    public var autoFinishOnEvent: Bool
    public var eventTypes: Set<BlinkEventType>

    public init(
        earCloseThreshold: Float = 0.22,
        earOpenThreshold: Float = 0.25,
        doubleBlinkWindowMs: Int64 = 350,
        longCloseMinMs: Int64 = 700,
        minShortBlinkMs: Int64 = 16,
        maxShortBlinkMs: Int64 = 280,
        noFaceResetMs: Int64 = 500,
        autoFinishOnEvent: Bool = true,
        eventTypes: Set<BlinkEventType> = Set(BlinkEventType.allCases)
    ) {
        self.earCloseThreshold = earCloseThreshold
        self.earOpenThreshold = earOpenThreshold
        self.doubleBlinkWindowMs = doubleBlinkWindowMs
        self.longCloseMinMs = longCloseMinMs
        self.minShortBlinkMs = minShortBlinkMs
        self.maxShortBlinkMs = maxShortBlinkMs
        self.noFaceResetMs = noFaceResetMs
        self.autoFinishOnEvent = autoFinishOnEvent
        self.eventTypes = eventTypes.isEmpty ? Set(BlinkEventType.allCases) : eventTypes
    }
}
