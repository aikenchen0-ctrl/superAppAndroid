final class BlinkEventClassifier {
    private enum State {
        case open
        case closed
        case waitingSecondBlink
        case longCloseEmitted
    }

    private struct BlinkSegment {
        let startTimeMs: Int64
        let endTimeMs: Int64
        let durationMs: Int64
        let leftEar: Float
        let rightEar: Float

        init(startTimeMs: Int64, endTimeMs: Int64, leftEar: Float, rightEar: Float) {
            self.startTimeMs = startTimeMs
            self.endTimeMs = endTimeMs
            self.durationMs = endTimeMs - startTimeMs
            self.leftEar = leftEar
            self.rightEar = rightEar
        }
    }

    private let options: BlinkCaptureOptions
    private var state: State = .open
    private var eyesClosed = false
    private var closedStartMs: Int64 = 0
    private var secondBlinkStartMs: Int64 = 0
    private var lastFaceSeenMs: Int64 = 0
    private var pendingShortBlink: BlinkSegment?

    init(options: BlinkCaptureOptions) {
        self.options = options
    }

    func accept(timestampMs: Int64, hasFace: Bool, leftEar: Float, rightEar: Float) -> BlinkCaptureResult? {
        guard hasFace else {
            if lastFaceSeenMs > 0 && timestampMs - lastFaceSeenMs >= options.noFaceResetMs {
                resetState()
            }
            return nil
        }

        lastFaceSeenMs = timestampMs
        let closed = resolveClosed(leftEar: leftEar, rightEar: rightEar)

        switch state {
        case .open:
            if closed {
                closedStartMs = timestampMs
                state = .closed
            }
            return nil

        case .closed:
            return handleClosed(timestampMs: timestampMs, closed: closed, leftEar: leftEar, rightEar: rightEar)

        case .waitingSecondBlink:
            return handleWaitingSecondBlink(timestampMs: timestampMs, closed: closed)

        case .longCloseEmitted:
            if !closed {
                closedStartMs = 0
                secondBlinkStartMs = 0
                state = .open
            }
            return nil
        }
    }

    private func handleClosed(timestampMs: Int64, closed: Bool, leftEar: Float, rightEar: Float) -> BlinkCaptureResult? {
        let startMs = secondBlinkStartMs > 0 ? secondBlinkStartMs : closedStartMs
        let durationMs = timestampMs - startMs

        if closed && durationMs >= options.longCloseMinMs {
            pendingShortBlink = nil
            secondBlinkStartMs = 0
            state = .longCloseEmitted
            return buildResult(
                eventType: .longClose,
                segment: BlinkSegment(startTimeMs: startMs, endTimeMs: timestampMs, leftEar: leftEar, rightEar: rightEar)
            )
        }

        if !closed {
            let segment = BlinkSegment(startTimeMs: startMs, endTimeMs: timestampMs, leftEar: leftEar, rightEar: rightEar)
            if let pendingShortBlink, isShortBlink(segment) {
                let result = buildDoubleBlink(first: pendingShortBlink, second: segment)
                self.pendingShortBlink = nil
                secondBlinkStartMs = 0
                state = .open
                return result
            }

            if isShortBlink(segment) {
                pendingShortBlink = segment
                state = .waitingSecondBlink
                return nil
            }

            secondBlinkStartMs = 0
            state = .open
        }

        return nil
    }

    private func handleWaitingSecondBlink(timestampMs: Int64, closed: Bool) -> BlinkCaptureResult? {
        guard let pendingShortBlink else {
            state = .open
            return nil
        }

        if timestampMs - pendingShortBlink.endTimeMs > options.doubleBlinkWindowMs {
            let result = buildResult(eventType: .singleBlink, segment: pendingShortBlink)
            self.pendingShortBlink = nil
            state = closed ? .closed : .open
            if closed {
                closedStartMs = timestampMs
            }
            return result
        }

        if closed {
            secondBlinkStartMs = timestampMs
            state = .closed
        }
        return nil
    }

    private func resolveClosed(leftEar: Float, rightEar: Float) -> Bool {
        let averageEar = (leftEar + rightEar) / 2
        if eyesClosed {
            eyesClosed = averageEar <= options.earOpenThreshold
        } else {
            eyesClosed = averageEar < options.earCloseThreshold
        }
        return eyesClosed
    }

    private func isShortBlink(_ segment: BlinkSegment) -> Bool {
        segment.durationMs >= options.minShortBlinkMs && segment.durationMs <= options.maxShortBlinkMs
    }

    private func buildResult(eventType: BlinkEventType, segment: BlinkSegment) -> BlinkCaptureResult {
        BlinkCaptureResult(
            eventType: eventType,
            startTimeMs: segment.startTimeMs,
            endTimeMs: segment.endTimeMs,
            durationMs: segment.durationMs,
            confidence: 1
        )
    }

    private func buildDoubleBlink(first: BlinkSegment, second: BlinkSegment) -> BlinkCaptureResult {
        BlinkCaptureResult(
            eventType: .doubleBlink,
            startTimeMs: first.startTimeMs,
            endTimeMs: second.endTimeMs,
            durationMs: second.endTimeMs - first.startTimeMs,
            confidence: 1
        )
    }

    private func resetState() {
        state = .open
        eyesClosed = false
        closedStartMs = 0
        secondBlinkStartMs = 0
        pendingShortBlink = nil
    }
}
