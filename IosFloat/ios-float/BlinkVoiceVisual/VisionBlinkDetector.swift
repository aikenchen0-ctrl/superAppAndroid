#if canImport(AVFoundation) && canImport(Vision)
import AVFoundation
import ImageIO
import Vision

struct BlinkFrameResult {
    let hasFace: Bool
    let leftEar: Float
    let rightEar: Float
}

final class VisionBlinkDetector {
    private let sequenceHandler = VNSequenceRequestHandler()

    func detect(sampleBuffer: CMSampleBuffer) throws -> BlinkFrameResult {
        let request = VNDetectFaceLandmarksRequest()
        try sequenceHandler.perform([request], on: sampleBuffer, orientation: .leftMirrored)

        guard
            let face = request.results?.first as? VNFaceObservation,
            let landmarks = face.landmarks,
            let leftEye = landmarks.leftEye,
            let rightEye = landmarks.rightEye
        else {
            return BlinkFrameResult(hasFace: false, leftEar: 0, rightEar: 0)
        }

        return BlinkFrameResult(
            hasFace: true,
            leftEar: eyeAspectRatio(leftEye),
            rightEar: eyeAspectRatio(rightEye)
        )
    }

    private func eyeAspectRatio(_ eye: VNFaceLandmarkRegion2D) -> Float {
        let points = (0..<eye.pointCount).map { eye.normalizedPoints[$0] }
        guard points.count >= 2 else {
            return 0
        }

        let minX = points.map(\.x).min() ?? 0
        let maxX = points.map(\.x).max() ?? 0
        let minY = points.map(\.y).min() ?? 0
        let maxY = points.map(\.y).max() ?? 0

        let width = maxX - minX
        let height = maxY - minY
        guard width > 0.000001 else {
            return 0
        }

        return Float(height / width)
    }
}
#endif
