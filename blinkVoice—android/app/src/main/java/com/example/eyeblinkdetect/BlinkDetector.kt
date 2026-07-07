package com.example.eyeblinkdetect

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.sqrt

/**
 * Wraps MediaPipe FaceLandmarker and computes Eye Aspect Ratio (EAR) for blink detection.
 *
 * EAR = (||p2-p6|| + ||p3-p5||) / (2 * ||p1-p4||)
 * where p1..p6 are the 6 eye landmark points (horizontal endpoints + vertical pairs).
 *
 * When EAR drops below EAR_THRESHOLD the eye is considered closed.
 */
class BlinkDetector(
    private val context: Context,
    private val listener: BlinkListener
) {

    interface BlinkListener {
        fun onResult(
            result: FaceLandmarkerResult,
            leftEar: Float,
            rightEar: Float,
            leftClosed: Boolean,
            rightClosed: Boolean,
            blinkCount: Int,
            inferenceMs: Long,
            imageWidth: Int,
            imageHeight: Int,
            rotationDegrees: Int
        )
        fun onError(error: String)
    }

    companion object {
        // Default EAR threshold
        const val EAR_THRESHOLD = 0.22f
        // Model file name (will be copied to assets)
        const val MODEL_NAME = "face_landmarker.task"
    }

    // Adjustable EAR threshold — can be changed at runtime from UI
    @Volatile var earThreshold: Float = EAR_THRESHOLD

    // MediaPipe FaceMesh 478-landmark model:
    // Left eye EAR points (indices into normalized landmarks list)
    // Using the standard 6-point EAR formula mapped to MediaPipe indices
    // p1=263(outer), p2=387(upper-outer), p3=386(upper-inner),
    // p4=362(inner), p5=380(lower-inner), p6=374(lower-outer) — user's LEFT eye
    private val LEFT_EYE_EAR = intArrayOf(263, 387, 386, 362, 380, 374)
    // p1=33(outer), p2=160(upper-outer), p3=158(upper-inner),
    // p4=133(inner), p5=153(lower-inner), p6=144(lower-outer) — user's RIGHT eye
    private val RIGHT_EYE_EAR = intArrayOf(33, 160, 158, 133, 153, 144)

    private var faceLandmarker: FaceLandmarker? = null

    // Blink counter state
    private var blinkCount = 0
    private var wasBlinking = false

    // Model load time (ms)
    var loadTimeMs: Long = 0L
        private set

    fun setup() {
        val t0 = SystemClock.elapsedRealtime()
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(MODEL_NAME)
            .setDelegate(Delegate.CPU)

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setNumFaces(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, mpImage ->
                handleResult(result, mpImage)
            }
            .setErrorListener { error ->
                listener.onError(error.message ?: "Unknown error")
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        loadTimeMs = SystemClock.elapsedRealtime() - t0
    }

    private var lastRotationDegrees: Int = 0
    private var frameDispatchTime: Long = 0L

    fun detectAsync(mpImage: MPImage, frameTime: Long, rotationDegrees: Int = 0) {
        lastRotationDegrees = rotationDegrees
        frameDispatchTime = SystemClock.elapsedRealtime()
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    private fun handleResult(result: FaceLandmarkerResult, mpImage: MPImage) {
        val inferenceMs = SystemClock.elapsedRealtime() - frameDispatchTime

        var leftEar = 0f
        var rightEar = 0f
        var leftClosed = false
        var rightClosed = false

        if (result.faceLandmarks().isNotEmpty()) {
            val landmarks = result.faceLandmarks()[0]
            leftEar = computeEar(landmarks, LEFT_EYE_EAR)
            rightEar = computeEar(landmarks, RIGHT_EYE_EAR)
            leftClosed = leftEar < earThreshold
            rightClosed = rightEar < earThreshold

            // Count a blink on the leading edge of closure (1 frame = 1 blink)
            val bothClosed = leftClosed && rightClosed
            if (bothClosed && !wasBlinking) {
                blinkCount++
                wasBlinking = true
            } else if (!bothClosed) {
                wasBlinking = false
            }
        }

        listener.onResult(
            result,
            leftEar,
            rightEar,
            leftClosed,
            rightClosed,
            blinkCount,
            inferenceMs,
            mpImage.width,
            mpImage.height,
            lastRotationDegrees
        )
    }

    private fun computeEar(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        indices: IntArray
    ): Float {
        if (indices.any { it >= landmarks.size }) return 0f
        // p1..p6 mapped: [0]=outer, [1]=upper-far, [2]=upper-near, [3]=inner, [4]=lower-near, [5]=lower-far
        val p1 = landmarks[indices[0]]
        val p2 = landmarks[indices[1]]
        val p3 = landmarks[indices[2]]
        val p4 = landmarks[indices[3]]
        val p5 = landmarks[indices[4]]
        val p6 = landmarks[indices[5]]

        val vertical1 = dist(p2, p6)
        val vertical2 = dist(p3, p5)
        val horizontal = dist(p1, p4)

        return if (horizontal < 1e-6f) 0f
        else (vertical1 + vertical2) / (2.0f * horizontal)
    }

    private fun dist(
        a: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        b: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
