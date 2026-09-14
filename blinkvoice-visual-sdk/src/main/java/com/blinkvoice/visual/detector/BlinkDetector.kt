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
/**
 * 封装 MediaPipe 人脸关键点检测，并把眼部 3D 关键点转换成 ELA 角度。
 */
class BlinkDetector(
    private val context: Context,
    listener: BlinkListener
) {
    @Volatile
    private var listener: BlinkListener = listener

    interface BlinkListener {
        /**
         * 返回每帧人脸关键点、左右眼 ELA、闭眼状态和推理耗时。
         */
        fun onResult(
            result: FaceLandmarkerResult,
            frameTimeMs: Long,
            leftEla: Float,
            rightEla: Float,
            leftClosed: Boolean,
            rightClosed: Boolean,
            blinkCount: Int,
            inferenceMs: Long,
            imageWidth: Int,
            imageHeight: Int,
            rotationDegrees: Int
        )
        /**
         * 返回 MediaPipe 初始化或推理过程中的错误信息。
         */
        fun onError(error: String)
    }

    companion object {
        const val ELA_CLOSE_THRESHOLD = 10f
        const val MODEL_NAME = "face_landmarker.task"
    }

    @Volatile var elaCloseThreshold: Float = ELA_CLOSE_THRESHOLD

    /**
     * 兼容早期 EAR 命名调用方的别名。数值已经是 ELA 角度，不再是 EAR 比值。
     */
    @Deprecated("Use elaCloseThreshold; the detector now uses ELA degrees.")
    var earThreshold: Float
        get() = elaCloseThreshold
        set(value) {
            elaCloseThreshold = value
        }

    private val LEFT_EYE_ELA = EyeLandmarks(263, 362, 386, 374)
    private val RIGHT_EYE_ELA = EyeLandmarks(33, 133, 158, 153)

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
    private val pendingFrameMetadata = FrameMetadataStore()

    var loadTimeMs: Long = 0L
        private set

    /**
     * 设置是否输出检测层调试日志。
     */
    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled = enabled
    }

    /**
     * 预加载 detector 被正式检测页接管时，需要把空 listener 切换成宿主 listener。
     */
    fun setListener(listener: BlinkListener) {
        this.listener = listener
    }

    /**
     * 初始化 FaceLandmarker 模型和实时流推理参数。
     */
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

    /**
     * 异步提交一帧相机图像给 MediaPipe，并记录提交时间用于计算推理耗时。
     */
    fun detectAsync(mpImage: MPImage, frameTime: Long, rotationDegrees: Int = 0) {
        val detector = faceLandmarker ?: return
        val dispatchTimeMs = SystemClock.elapsedRealtime()
        pendingFrameMetadata.record(mpImage, frameTime, rotationDegrees, dispatchTimeMs)
        try {
            detector.detectAsync(mpImage, frameTime)
        } catch (error: Throwable) {
            pendingFrameMetadata.take(mpImage)
            throw error
        }
    }

    /**
     * 释放 MediaPipe 检测器资源，避免相机页面关闭后继续占用模型。
     */
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
        pendingFrameMetadata.clear()
    }

    /**
     * 清空动作计数和调试状态，供连续检测器 stop/start 时复用已加载模型。
     */
    @Synchronized
    fun reset() {
        blinkCount = 0
        wasBlinking = false
        resultFrameCount = 0
        lastHasFace = false
        hasFaceStateInitialized = false
        lastLeftClosed = false
        lastRightClosed = false
        eyeStateInitialized = false
        pendingFrameMetadata.clear()
    }

    /**
     * 处理 MediaPipe 返回的人脸关键点，计算左右眼 ELA 并给出基础闭眼状态。
     */
    private fun handleResult(result: FaceLandmarkerResult, mpImage: MPImage) {
        val metadata = pendingFrameMetadata.take(mpImage) ?: run {
            BlinkDebugLogger.warn(debugLoggingEnabled, "result_without_frame_metadata")
            return
        }
        val inferenceMs = (SystemClock.elapsedRealtime() - metadata.getDispatchTimeMs()).coerceAtLeast(0L)

        var leftEla = Float.NaN
        var rightEla = Float.NaN
        var leftClosed = false
        var rightClosed = false
        val hasFace = result.faceLandmarks().isNotEmpty()

        if (hasFace) {
            val landmarks = result.faceLandmarks()[0]
            leftEla = computeEla(landmarks, LEFT_EYE_ELA)
            rightEla = computeEla(landmarks, RIGHT_EYE_ELA)
            leftClosed = isValidEla(leftEla) && leftEla < elaCloseThreshold
            rightClosed = isValidEla(rightEla) && rightEla < elaCloseThreshold

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
            leftEla,
            rightEla,
            leftClosed,
            rightClosed,
            inferenceMs,
            mpImage.width,
            mpImage.height,
            metadata.getRotationDegrees()
        )

        listener.onResult(
            result,
            metadata.getFrameTimeMs(),
            leftEla,
            rightEla,
            leftClosed,
            rightClosed,
            blinkCount,
            inferenceMs,
            mpImage.width,
            mpImage.height,
            metadata.getRotationDegrees()
        )
    }

    /**
     * 按人脸状态、闭眼状态和节流帧数输出检测层日志。
     */
    private fun logDetectionResult(
        hasFace: Boolean,
        leftEla: Float,
        rightEla: Float,
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
            // 闭眼状态变化要立即输出，用于判断 ELA 阈值是否把睁眼/闭眼分错。
            BlinkDebugLogger.log(
                debugLoggingEnabled,
                "eye_state changed leftClosed=$leftClosed rightClosed=$rightClosed avgEla=${formatEla((leftEla + rightEla) / 2f)}"
            )
            lastLeftClosed = leftClosed
            lastRightClosed = rightClosed
            eyeStateInitialized = true
        }

        if (resultFrameCount % 10 == 0 || faceChanged || eyeChanged) {
            // 帧级日志只节流输出，避免 Logcat 被相机帧刷爆。
            BlinkDebugLogger.log(
                debugLoggingEnabled,
                "detect face=${if (hasFace) 1 else 0} leftEla=${formatEla(leftEla)} rightEla=${formatEla(rightEla)} " +
                    "avgEla=${formatEla((leftEla + rightEla) / 2f)} th=${formatEla(elaCloseThreshold)} " +
                    "closed=$leftClosed/$rightClosed blinkCount=$blinkCount inferenceMs=$inferenceMs " +
                    "size=${imageWidth}x$imageHeight rotation=$rotationDegrees"
            )
        }
    }

    /**
     * 将 ELA 角度格式化成固定两位小数，保证日志可读。
     */
    private fun formatEla(value: Float): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }

    /**
     * 用上下眼睑平面法向量夹角计算单只眼睛的 ELA：角度越小，眼睛越闭合。
     */
    private fun computeEla(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        eye: EyeLandmarks
    ): Float {
        if (!eye.isValidFor(landmarks.size)) return Float.NaN
        val outer = pointOf(landmarks[eye.outerCorner])
        val inner = pointOf(landmarks[eye.innerCorner])
        val upper = pointOf(landmarks[eye.upperLid])
        val lower = pointOf(landmarks[eye.lowerLid])
        return EyelidAngleCalculator.computeAngleDegrees(
            arrayOf(outer, inner, upper),
            arrayOf(outer, inner, lower)
        )
    }

    private fun isValidEla(value: Float): Boolean {
        return !value.isNaN() && !value.isInfinite() && value >= 0f && value <= 180f
    }

    private fun pointOf(
        landmark: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ): EyelidAngleCalculator.Point3 {
        return EyelidAngleCalculator.Point3(landmark.x(), landmark.y(), landmark.z())
    }

    private data class EyeLandmarks(
        val outerCorner: Int,
        val innerCorner: Int,
        val upperLid: Int,
        val lowerLid: Int
    ) {
        fun isValidFor(size: Int): Boolean {
            return outerCorner < size && innerCorner < size && upperLid < size && lowerLid < size
        }
    }
}
