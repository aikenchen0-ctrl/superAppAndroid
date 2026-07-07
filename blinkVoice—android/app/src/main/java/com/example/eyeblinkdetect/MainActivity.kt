package com.example.eyeblinkdetect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.blinkvoice.visual.api.BlinkCaptureOptions
import com.blinkvoice.visual.api.BlinkOutcomeContract
import com.example.eyeblinkdetect.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), BlinkDetector.BlinkListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var blinkDetector: BlinkDetector
    private var blinkDetectorReady = false
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var sdkCaptureInProgress = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoEarCalibrator = AutoEarThresholdCalibrator()

    // FPS tracking
    private var frameCount = 0
    private var lastFpsTime = SystemClock.elapsedRealtime()
    private var currentFps = 0f

    // Power stats — cached, updated once per second
    private lateinit var batteryManager: BatteryManager
    private var lastPowerUpdateTime = 0L
    private var cachedPowerText = "Bat: --"

    // 请求相机权限，并在授权后启动本页面自己的检测预览。
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
    }

    // 接收 SDK 调起页的最终结果，包含成功事件或取消原因。
    private val sdkCaptureLauncher = registerForActivityResult(
        BlinkOutcomeContract()
    ) { outcome ->
        sdkCaptureInProgress = false
        binding.tvSdkCaptureResult.text = SdkBlinkResultFormatter.format(outcome)
        if (blinkDetectorReady && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    // 初始化 demo 页面、相机检测器和 SDK 调起按钮。
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on while detecting
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        cameraExecutor = Executors.newSingleThreadExecutor()

        configureEarThresholdControl()
        configureSdkCaptureButton()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initDetectorAndCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 配置 demo 页面自己的 EAR 阈值滑条，不影响 SDK 调起页的内部阈值。
    private fun configureEarThresholdControl() {
        // EAR threshold SeekBar: range 10..40 -> 0.10..0.40, default 22
        binding.seekBarEar.max = 30  // 10 steps from 10 to 40
        binding.seekBarEar.progress = 12  // default 0.22 -> progress = 22-10 = 12
        updateEarLabel(0.22f)
        binding.seekBarEar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            // 滑条变化时更新 demo 页面自己的实时检测阈值。
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val threshold = (progress + 10) / 100f
                if (blinkDetectorReady) blinkDetector.earThreshold = threshold
                updateEarLabel(threshold)
            }

            // 当前不需要在开始拖动时执行额外逻辑。
            override fun onStartTrackingTouch(sb: SeekBar) {}

            // 当前不需要在停止拖动时执行额外逻辑。
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // 绑定“测试 SDK 调起接口”按钮，点击后按最新规则启动 SDK 页面。
    private fun configureSdkCaptureButton() {
        binding.btnSdkCapture.setOnClickListener {
            launchSdkBlinkCapture()
        }
    }

    // 释放宿主相机后调起 SDK，避免两个页面同时占用前置摄像头导致取消或闪退。
    private fun launchSdkBlinkCapture() {
        binding.tvSdkCaptureResult.text = "SDK result: waiting..."
        sdkCaptureInProgress = true
        stopCamera()
        mainHandler.postDelayed({
            sdkCaptureLauncher.launch(buildSdkCaptureOptions())
        }, 250L)
    }

    // 构造 App 端传给 SDK 的最新识别规则，避免宿主 App 复制旧默认值。
    private fun buildSdkCaptureOptions(): BlinkCaptureOptions {
        val autoEar = autoEarCalibrator.recommendation
        return BlinkCaptureOptions.Builder()
            .setEarCloseThreshold(autoEar.closeThreshold)
            .setEarOpenThreshold(autoEar.openThreshold)
            .setDoubleBlinkWindowMs(SDK_DOUBLE_BLINK_WINDOW_MS)
            .setLongCloseMinMs(SDK_LONG_CLOSE_MIN_MS)
            .setMinShortBlinkMs(SDK_MIN_SHORT_BLINK_MS)
            .setMaxShortBlinkMs(SDK_MAX_SHORT_BLINK_MS)
            .setNoFaceResetMs(SDK_NO_FACE_RESET_MS)
            .setAutoFinishOnEvent(true)
            .setDebugLoggingEnabled(true)
            .setDebugOverlayEnabled(true)
            .build()
    }

    // 刷新 demo 页面显示的 EAR 阈值文本。
    private fun updateEarLabel(threshold: Float) {
        binding.tvEarThreshold.text = "EAR threshold: ${"%.2f".format(threshold)}"
    }

    // 初始化本 demo 的 MediaPipe 检测器，并在成功后启动相机预览。
    private fun initDetectorAndCamera() {
        blinkDetector = BlinkDetector(this, this)
        try {
            blinkDetector.setup()
            blinkDetector.earThreshold = (binding.seekBarEar.progress + 10) / 100f
            blinkDetectorReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup BlinkDetector", e)
            Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        startCamera()
    }

    // 启动 demo 页面自己的 CameraX 预览和逐帧分析。
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            if (sdkCaptureInProgress) {
                provider.unbindAll()
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            // Use front camera for eye blink detection
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // 停止 demo 页面自己的相机，给 SDK 调起页释放摄像头资源。
    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop camera before SDK capture", e)
        }
    }

    // 将 CameraX 帧转换为 MediaPipe 输入，并交给 demo 页面自己的检测器异步处理。
    private fun processImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000
        val rotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        frameCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastFpsTime
        if (elapsed >= 1000L) {
            currentFps = frameCount * 1000f / elapsed
            frameCount = 0
            lastFpsTime = now
        }

        blinkDetector.detectAsync(mpImage, frameTimeMs, rotation)
    }

    // ── BlinkListener ──────────────────────────────────────────────────────────

    // 接收 demo 页面自己的逐帧检测结果，并刷新页面上的状态、耗时和覆盖层。
    override fun onResult(
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
    ) {
        runOnUiThread {
            // FPS
            binding.tvFps.text = "FPS: ${"%.1f".format(currentFps)}"
            binding.tvBlinkCount.text = "Blinks: $blinkCount"

            // Blink status
            val bothClosed = leftClosed && rightClosed
            if (bothClosed) {
                binding.tvBlinkStatus.text = "EYES CLOSED"
                binding.tvBlinkStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            } else {
                binding.tvBlinkStatus.text = "EYES OPEN"
                binding.tvBlinkStatus.setTextColor(android.graphics.Color.parseColor("#00FF88"))
            }

            // EAR values
            binding.tvLeftEar.text = "Left EAR: ${"%.3f".format(leftEar)}"
            binding.tvRightEar.text = "Right EAR: ${"%.3f".format(rightEar)}"
            val averageEar = (leftEar + rightEar) / 2f
            autoEarCalibrator.addSample(averageEar, bothClosed)
            binding.tvAutoEarThreshold.text = autoEarCalibrator.recommendation.toDisplayText()

            // Inference / load time
            binding.tvInferenceMs.text = "Infer: ${inferenceMs}ms | Load: ${blinkDetector.loadTimeMs}ms"

            // Power stats — update once per second to avoid per-frame registerReceiver cost
            val now2 = SystemClock.elapsedRealtime()
            if (now2 - lastPowerUpdateTime >= 1000L) {
                lastPowerUpdateTime = now2
                val currentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val bIntent = registerReceiver(null, iFilter)
                val voltage = bIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
                val currentMa = currentNow / 1000f
                val powerMw = if (voltage > 0) currentMa.coerceAtLeast(0f) * voltage / 1000f else 0f
                cachedPowerText = "Bat: $batteryPct% | ${"%+.0f".format(currentMa)}mA | ${"%.0f".format(powerMw)}mW"
            }
            binding.tvPower.text = cachedPowerText

            // Update overlay
            if (result.faceLandmarks().isNotEmpty()) {
                binding.overlayView.setResults(result, imageHeight, imageWidth, isFrontCamera = true, rotationDegrees = rotationDegrees)
            } else {
                binding.overlayView.clear()
            }
        }
    }

    // 接收 demo 页面自己的检测错误，并显示到日志和 Toast。
    override fun onError(error: String) {
        runOnUiThread {
            Log.e(TAG, "BlinkDetector error: $error")
            Toast.makeText(this, "Detection error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    // 释放 handler、相机、检测器和后台线程，避免页面销毁后继续占用资源。
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        stopCamera()
        if (::blinkDetector.isInitialized) {
            blinkDetector.close()
        }
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "EyeBlinkDetect"
        private const val SDK_EAR_CLOSE_THRESHOLD = 0.22f
        private const val SDK_EAR_OPEN_THRESHOLD = 0.25f
        private const val SDK_DOUBLE_BLINK_WINDOW_MS = 650L
        private const val SDK_LONG_CLOSE_MIN_MS = 500L
        private const val SDK_MIN_SHORT_BLINK_MS = 30L
        private const val SDK_MAX_SHORT_BLINK_MS = 260L
        private const val SDK_NO_FACE_RESET_MS = 500L
    }
}
