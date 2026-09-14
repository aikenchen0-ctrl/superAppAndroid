package com.blinkvoice.visual.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.blinkvoice.visual.debug.BlinkDebugLogger
import com.blinkvoice.visual.detector.BlinkDetector
import com.blinkvoice.visual.events.BlinkEventClassifier
import com.blinkvoice.visual.performance.AnalysisFrameGate
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 可直接嵌入宿主 App 页面使用的连续眨眼检测控制器。
 */
class BlinkVoiceContinuousDetector private constructor(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    initialOptions: BlinkCaptureOptions,
    private val listener: BlinkVoiceContinuousListener,
    private val targetResolution: Size,
    private val usePreloadedDetector: Boolean
) : BlinkDetector.BlinkListener {
    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var options: BlinkCaptureOptions = initialOptions

    @Volatile
    private var classifier: BlinkEventClassifier = BlinkEventClassifier(initialOptions)

    @Volatile
    private var started = false

    @Volatile
    private var closed = false

    private var detector: BlinkDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    @Volatile
    private var frameGate: AnalysisFrameGate = AnalysisFrameGate(initialOptions.maxAnalysisFps)
    private var startupStartElapsedMs: Long = 0L
    private var detectorReady = false
    private var cameraReady = false
    private var firstFrameLogged = false
    private var firstResultLogged = false
    @Volatile
    private var sessionGeneration = 0L

    /**
     * 初始化模型并把前置摄像头绑定到宿主传入的 PreviewView。
     */
    @Synchronized
    fun start() {
        if (closed) {
            throw IllegalStateException("Detector is closed.")
        }
        if (started) {
            return
        }
        val generation = sessionGeneration + 1L
        sessionGeneration = generation
        started = true
        startupStartElapsedMs = SystemClock.elapsedRealtime()
        detectorReady = detector != null
        cameraReady = false
        firstFrameLogged = false
        firstResultLogged = false
        frameGate.reset()
        logDebug("continuous_start maxFps=${options.maxAnalysisFps} target=${targetResolution.width}x${targetResolution.height}")
        postStartupTiming(
            BlinkVoiceStartupTiming.STAGE_START,
            0L,
            "target=${targetResolution.width}x${targetResolution.height} maxFps=${options.maxAnalysisFps}"
        )

        loadDetectorAsync(generation)
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (!isSessionActive(generation)) {
                return@addListener
            }
            try {
                cameraProvider = providerFuture.get()
                cameraReady = true
                logDebug("continuous_camera_provider_ready elapsedMs=${elapsedSinceStartup()}")
                postStartupTiming(BlinkVoiceStartupTiming.STAGE_CAMERA_PROVIDER_READY, elapsedSinceStartup(), "")
                bindCameraIfReady(generation)
            } catch (error: Exception) {
                started = false
                postError("Camera unavailable: ${error.javaClass.simpleName}", generation)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /**
     * 停止相机分析并解绑本控制器创建的 CameraX use case。
     */
    @Synchronized
    fun stop() {
        sessionGeneration += 1L
        started = false
        cameraReady = false
        unbindOwnedUseCases()
        frameGate.reset()
        classifier.reset()
        detector?.reset()
    }

    /**
     * 释放模型和线程资源。调用后不能再次 start。
     */
    @Synchronized
    fun close() {
        if (closed) {
            return
        }
        stop()
        detector?.close()
        detector = null
        cameraExecutor.shutdown()
        closed = true
    }

    /**
     * 运行时更新识别阈值和时间窗口；状态机会同步重置。
     */
    @Synchronized
    fun updateOptions(newOptions: BlinkCaptureOptions?) {
        val safeOptions = newOptions ?: BlinkCaptureOptions.Builder().build()
        options = safeOptions
        classifier = BlinkEventClassifier(safeOptions)
        frameGate = AnalysisFrameGate(safeOptions.maxAnalysisFps)
        detector?.elaCloseThreshold = safeOptions.earCloseThreshold
    }

    private fun loadDetectorAsync(generation: Long) {
        if (!isSessionActive(generation)) {
            return
        }
        if (detectorReady) {
            detector?.setListener(SessionDetectorListener(generation))
            detector?.elaCloseThreshold = options.earCloseThreshold
            postStartupTiming(BlinkVoiceStartupTiming.STAGE_DETECTOR_READY, elapsedSinceStartup(), "cached")
            mainHandler.post {
                bindCameraIfReady(generation)
            }
            return
        }
        if (usePreloadedDetector) {
            BlinkVoiceSdk.claimPreloadedDetector(SessionDetectorListener(generation))?.let { preloadedDetector ->
                preloadedDetector.setDebugLoggingEnabled(options.isDebugLoggingEnabled)
                preloadedDetector.elaCloseThreshold = options.earCloseThreshold
                detector = preloadedDetector
                detectorReady = true
                logDebug("continuous_detector_ready preloaded elapsedMs=${elapsedSinceStartup()}")
                postStartupTiming(BlinkVoiceStartupTiming.STAGE_DETECTOR_READY, elapsedSinceStartup(), "preloaded")
                mainHandler.post {
                    bindCameraIfReady(generation)
                }
                return
            }
        }
        cameraExecutor.execute {
            val blinkDetector = BlinkDetector(appContext, SessionDetectorListener(generation))
            blinkDetector.setDebugLoggingEnabled(options.isDebugLoggingEnabled)
            try {
                blinkDetector.setup()
                blinkDetector.elaCloseThreshold = options.earCloseThreshold
                mainHandler.post {
                    if (!isSessionActive(generation)) {
                        blinkDetector.close()
                        return@post
                    }
                    detector = blinkDetector
                    detectorReady = true
                    logDebug("continuous_detector_ready loadMs=${blinkDetector.loadTimeMs} elapsedMs=${elapsedSinceStartup()}")
                    postStartupTiming(
                        BlinkVoiceStartupTiming.STAGE_DETECTOR_READY,
                        elapsedSinceStartup(),
                        "loadMs=${blinkDetector.loadTimeMs}"
                    )
                    bindCameraIfReady(generation)
                }
            } catch (error: Exception) {
                blinkDetector.close()
                mainHandler.post {
                    if (!isSessionGenerationCurrent(generation)) {
                        return@post
                    }
                    started = false
                    postError("Model load failed: ${error.javaClass.simpleName}", generation)
                }
            }
        }
    }

    private fun bindCameraIfReady(generation: Long) {
        if (!isSessionActive(generation) || !detectorReady || !cameraReady || cameraProvider == null) {
            return
        }
        if (previewUseCase != null || analysisUseCase != null) {
            return
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(targetResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(cameraExecutor) { image -> processImage(image, generation) }
        unbindOwnedUseCases()
        val provider = cameraProvider ?: return
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
            previewUseCase = preview
            analysisUseCase = analysis
            logDebug("continuous_camera_bound elapsedMs=${elapsedSinceStartup()}")
            postStartupTiming(BlinkVoiceStartupTiming.STAGE_CAMERA_BOUND, elapsedSinceStartup(), "")
        } catch (error: Exception) {
            analysis.clearAnalyzer()
            runCatching { provider.unbind(preview, analysis) }
            postError("Camera binding failed: ${error.javaClass.simpleName}", generation)
        }
    }

    private fun unbindOwnedUseCases() {
        val useCases = listOfNotNull(previewUseCase, analysisUseCase).toTypedArray<UseCase>()
        if (useCases.isNotEmpty()) {
            runCatching { cameraProvider?.unbind(*useCases) }
        }
        previewUseCase = null
        analysisUseCase = null
    }

    private fun processImage(imageProxy: ImageProxy, generation: Long) {
        try {
            if (!isSessionActive(generation)) {
                return
            }
            val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000L
            if (!frameGate.shouldAnalyze(frameTimeMs)) {
                return
            }
            if (!firstFrameLogged) {
                firstFrameLogged = true
                logDebug("continuous_first_frame elapsedMs=${elapsedSinceStartup()} size=${imageProxy.width}x${imageProxy.height}")
                postStartupTiming(
                    BlinkVoiceStartupTiming.STAGE_FIRST_FRAME,
                    elapsedSinceStartup(),
                    "${imageProxy.width}x${imageProxy.height}"
                )
            }
            val mpImage: MPImage = BitmapImageBuilder(imageProxy.toBitmap()).build()
            detector?.detectAsync(mpImage, frameTimeMs, imageProxy.imageInfo.rotationDegrees)
        } catch (error: Exception) {
            postError("Frame analysis failed: ${error.javaClass.simpleName}", generation)
        } finally {
            imageProxy.close()
        }
    }

    override fun onResult(
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
    ) {
        handleResult(
            sessionGeneration,
            result,
            frameTimeMs,
            leftEla,
            rightEla,
            leftClosed,
            rightClosed,
            blinkCount,
            inferenceMs,
            imageWidth,
            imageHeight,
            rotationDegrees
        )
    }

    private fun handleResult(
        generation: Long,
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
    ) {
        if (!isSessionActive(generation)) {
            return
        }
        val hasFace = result.faceLandmarks().isNotEmpty()
        if (!firstResultLogged) {
            firstResultLogged = true
            logDebug("continuous_first_result elapsedMs=${elapsedSinceStartup()} inferenceMs=$inferenceMs hasFace=$hasFace")
            postStartupTiming(
                BlinkVoiceStartupTiming.STAGE_FIRST_RESULT,
                elapsedSinceStartup(),
                "inferMs=$inferenceMs hasFace=$hasFace"
            )
        }
        val currentClassifier = classifier
        val event = currentClassifier.accept(frameTimeMs, hasFace, leftEla, rightEla)
        val snapshot = currentClassifier.debugSnapshot
        val frame = BlinkVoiceFrame(
            hasFace,
            snapshot.isClosed,
            leftEla,
            rightEla,
            inferenceMs,
            imageWidth,
            imageHeight,
            rotationDegrees,
            snapshot.phase,
            snapshot.lastReason,
            snapshot.lastEvent,
            snapshot.closedDurationMs,
            snapshot.pendingBlinkElapsedMs
        )
        val currentOptions = options
        mainHandler.post {
            if (!isSessionGenerationCurrent(generation)) {
                return@post
            }
            listener.onFrame(frame)
            if (event != null && currentOptions.eventTypes.contains(event.eventType)) {
                listener.onEvent(event)
            }
        }
    }

    override fun onError(error: String) {
        postError(error, sessionGeneration)
    }

    private fun postError(error: String, generation: Long) {
        mainHandler.post {
            if (!isSessionGenerationCurrent(generation)) {
                return@post
            }
            listener.onError(error)
        }
    }

    private fun postStartupTiming(stage: String, elapsedMs: Long, detail: String) {
        val generation = sessionGeneration
        val timing = BlinkVoiceStartupTiming(stage, elapsedMs, detail)
        mainHandler.post {
            if (!isSessionGenerationCurrent(generation)) {
                return@post
            }
            listener.onStartupTiming(timing)
        }
    }

    private fun isSessionActive(generation: Long): Boolean {
        return started && !closed && sessionGeneration == generation
    }

    private fun isSessionGenerationCurrent(generation: Long): Boolean {
        return !closed && sessionGeneration == generation
    }

    private fun elapsedSinceStartup(): Long {
        if (startupStartElapsedMs <= 0L) {
            return 0L
        }
        return SystemClock.elapsedRealtime() - startupStartElapsedMs
    }

    private fun logDebug(message: String) {
        BlinkDebugLogger.log(options.isDebugLoggingEnabled, message)
    }

    private inner class SessionDetectorListener(
        private val generation: Long
    ) : BlinkDetector.BlinkListener {
        override fun onResult(
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
        ) {
            handleResult(
                generation,
                result,
                frameTimeMs,
                leftEla,
                rightEla,
                leftClosed,
                rightClosed,
                blinkCount,
                inferenceMs,
                imageWidth,
                imageHeight,
                rotationDegrees
            )
        }

        override fun onError(error: String) {
            postError(error, generation)
        }
    }

    /**
     * Java/Kotlin 宿主 App 使用的构建器。
     */
    class Builder(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,
        private val previewView: PreviewView,
        private val listener: BlinkVoiceContinuousListener
    ) {
        private var options: BlinkCaptureOptions = BlinkCaptureOptions.Builder().build()
        private var targetResolution: Size = Size(640, 480)
        private var usePreloadedDetector: Boolean = true

        fun setOptions(options: BlinkCaptureOptions?): Builder {
            this.options = options ?: BlinkCaptureOptions.Builder().build()
            return this
        }

        fun setUsePreloadedDetector(usePreloadedDetector: Boolean): Builder {
            this.usePreloadedDetector = usePreloadedDetector
            return this
        }

        fun setTargetResolution(width: Int, height: Int): Builder {
            require(width > 0 && height > 0) {
                "Target resolution must be positive."
            }
            targetResolution = Size(width, height)
            return this
        }

        fun build(): BlinkVoiceContinuousDetector {
            return BlinkVoiceContinuousDetector(
                context,
                lifecycleOwner,
                previewView,
                options,
                listener,
                targetResolution,
                usePreloadedDetector
            )
        }
    }
}
