package com.blinkvoice.visual.detector

import android.content.Context
import android.os.SystemClock
import com.blinkvoice.visual.debug.BlinkDebugLogger
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
 * This is the original visual blink detector moved into the SDK module. The algorithmic
 * behavior is intentionally kept aligned with the app implementation.
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
        const val EAR_THRESHOLD = 0.22f
        const val MODEL_NAME = "face_landmarker.task"
    }

    @Volatile var earThreshold: Float = EAR_THRESHOLD

    private val LEFT_EYE_EAR = intArrayOf(263, 387, 386, 362, 380, 374)
    private val RIGHT_EYE_EAR = intArrayOf(33, 160, 158, 133, 153, 144)

    private var faceLandmarker: FaceLandmarker? = null
    private var blinkCount = 0
    private var wasBlinking = false
    @Volatile private var debugLoggingEnabled = false
    private var resultFrameCount = 0
    private var lastHasFace = false
    private var hasFaceStateInitialized = false
    private var lastLeftClosed = false
    private var lastRightClosed = false
    private var eyeStateInitialized = false

    var loadTimeMs: Long = 0L
        private set

    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled = enabled
    }

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
        val hasFace = result.faceLandmarks().isNotEmpty()

        if (hasFace) {
            val landmarks = result.faceLandmarks()[0]
            leftEar = computeEar(landmarks, LEFT_EYE_EAR)
            rightEar = computeEar(landmarks, RIGHT_EYE_EAR)
            leftClosed = leftEar < earThreshold
            rightClosed = rightEar < earThreshold

            val bothClosed = leftClosed && rightClosed
            if (bothClosed && !wasBlinking) {
                blinkCount++
                wasBlinking = true
            } else if (!bothClosed) {
                wasBlinking = false
            }
        }

        logDetectionResult(
            hasFace,
            leftEar,
            rightEar,
            leftClosed,
            rightClosed,
            inferenceMs,
            mpImage.width,
            mpImage.height,
            lastRotationDegrees
        )

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

    private fun logDetectionResult(
        hasFace: Boolean,
        leftEar: Float,
        rightEar: Float,
        leftClosed: Boolean,
        rightClosed: Boolean,
        inferenceMs: Long,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) {
        if (!debugLoggingEnabled) {
            return
        }

        resultFrameCount++
        val faceChanged = !hasFaceStateInitialized || lastHasFace != hasFace
        val eyeChanged = !eyeStateInitialized || lastLeftClosed != leftClosed || lastRightClosed != rightClosed

        if (faceChanged) {
            // 人脸状态变化要立即输出，用于判断是否卡在人脸检测之前。
            BlinkDebugLogger.log(debugLoggingEnabled, "face_state changed hasFace=$hasFace")
            lastHasFace = hasFace
            hasFaceStateInitialized = true
        }

        if (eyeChanged) {
            // 闭眼状态变化要立即输出，用于判断 EAR 阈值是否把睁眼/闭眼分错。
            BlinkDebugLogger.log(
                debugLoggingEnabled,
                "eye_state changed leftClosed=$leftClosed rightClosed=$rightClosed avgEar=${formatEar((leftEar + rightEar) / 2f)}"
            )
            lastLeftClosed = leftClosed
            lastRightClosed = rightClosed
            eyeStateInitialized = true
        }

        if (resultFrameCount % 10 == 0 || faceChanged || eyeChanged) {
            // 帧级日志只节流输出，避免 Logcat 被相机帧刷爆。
            BlinkDebugLogger.log(
                debugLoggingEnabled,
                "detect face=${if (hasFace) 1 else 0} leftEar=${formatEar(leftEar)} rightEar=${formatEar(rightEar)} " +
                    "avgEar=${formatEar((leftEar + rightEar) / 2f)} th=${formatEar(earThreshold)} " +
                    "closed=$leftClosed/$rightClosed blinkCount=$blinkCount inferenceMs=$inferenceMs " +
                    "size=${imageWidth}x$imageHeight rotation=$rotationDegrees"
            )
        }
    }

    private fun formatEar(value: Float): String {
        return String.format(java.util.Locale.US, "%.3f", value)
    }

    private fun computeEar(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        indices: IntArray
    ): Float {
        if (indices.any { it >= landmarks.size }) return 0f
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
