public enum BlinkEventType: String, Codable, CaseIterable, Hashable, Sendable {
    case singleBlink = "SINGLE_BLINK"
    case doubleBlink = "DOUBLE_BLINK"
    case longClose = "LONG_CLOSE"
}
