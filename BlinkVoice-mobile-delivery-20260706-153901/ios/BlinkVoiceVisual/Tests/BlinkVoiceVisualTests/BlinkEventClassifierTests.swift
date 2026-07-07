import XCTest
@testable import BlinkVoiceVisual

final class BlinkEventClassifierTests: XCTestCase {
    func testEmitsSingleBlinkAfterDoubleBlinkWindowExpires() {
        let classifier = BlinkEventClassifier(options: BlinkCaptureOptions())

        XCTAssertNil(classifier.accept(timestampMs: 0, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 100, hasFace: true, leftEar: 0.10, rightEar: 0.10))
        XCTAssertNil(classifier.accept(timestampMs: 180, hasFace: true, leftEar: 0.30, rightEar: 0.30))

        let result = classifier.accept(timestampMs: 540, hasFace: true, leftEar: 0.30, rightEar: 0.30)

        XCTAssertEqual(result?.eventType, .singleBlink)
        XCTAssertEqual(result?.startTimeMs, 100)
        XCTAssertEqual(result?.endTimeMs, 180)
        XCTAssertEqual(result?.durationMs, 80)
    }

    func testEmitsDoubleBlinkWhenSecondShortBlinkEndsInsideWindow() {
        let classifier = BlinkEventClassifier(options: BlinkCaptureOptions())

        XCTAssertNil(classifier.accept(timestampMs: 0, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 100, hasFace: true, leftEar: 0.10, rightEar: 0.10))
        XCTAssertNil(classifier.accept(timestampMs: 180, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 300, hasFace: true, leftEar: 0.10, rightEar: 0.10))

        let result = classifier.accept(timestampMs: 370, hasFace: true, leftEar: 0.30, rightEar: 0.30)

        XCTAssertEqual(result?.eventType, .doubleBlink)
        XCTAssertEqual(result?.startTimeMs, 100)
        XCTAssertEqual(result?.endTimeMs, 370)
        XCTAssertEqual(result?.durationMs, 270)
    }

    func testEmitsLongCloseOnceUntilEyesOpenAgain() {
        let classifier = BlinkEventClassifier(options: BlinkCaptureOptions())

        XCTAssertNil(classifier.accept(timestampMs: 0, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 100, hasFace: true, leftEar: 0.10, rightEar: 0.10))

        let result = classifier.accept(timestampMs: 850, hasFace: true, leftEar: 0.10, rightEar: 0.10)

        XCTAssertEqual(result?.eventType, .longClose)
        XCTAssertEqual(result?.startTimeMs, 100)
        XCTAssertEqual(result?.endTimeMs, 850)
        XCTAssertEqual(result?.durationMs, 750)
        XCTAssertNil(classifier.accept(timestampMs: 900, hasFace: true, leftEar: 0.10, rightEar: 0.10))
    }

    func testNoFaceTimeoutResetsPendingShortBlink() {
        let classifier = BlinkEventClassifier(options: BlinkCaptureOptions())

        XCTAssertNil(classifier.accept(timestampMs: 0, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 100, hasFace: true, leftEar: 0.10, rightEar: 0.10))
        XCTAssertNil(classifier.accept(timestampMs: 180, hasFace: true, leftEar: 0.30, rightEar: 0.30))
        XCTAssertNil(classifier.accept(timestampMs: 700, hasFace: false, leftEar: 0, rightEar: 0))

        let result = classifier.accept(timestampMs: 800, hasFace: true, leftEar: 0.30, rightEar: 0.30)

        XCTAssertNil(result)
    }
}
