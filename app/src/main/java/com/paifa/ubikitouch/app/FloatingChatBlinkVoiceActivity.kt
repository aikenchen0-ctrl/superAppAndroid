package com.paifa.ubikitouch.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.blinkvoice.visual.api.BlinkCaptureOptions
import com.blinkvoice.visual.api.BlinkCaptureResult
import com.blinkvoice.visual.detector.BlinkDetector
import com.blinkvoice.visual.events.BlinkEventClassifier
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.paifa.ubikitouch.accessibility.BlinkVoiceHeadlessExtraName
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.ubikitouch.accessibility.blinkVoiceCaptureAutoFinishOnEvent
import com.paifa.ubikitouch.accessibility.blinkVoiceRealtimeStatusLabel
import com.paifa.ubikitouch.accessibility.blinkVoiceStatusLogEntry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "FloatingChatBlinkVoice"

class FloatingChatBlinkVoiceActivity : ComponentActivity(), BlinkDetector.BlinkListener {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val options: BlinkCaptureOptions = BlinkCaptureOptions.Builder()
        .setAutoFinishOnEvent(blinkVoiceCaptureAutoFinishOnEvent())
        .build()
    private val classifier = BlinkEventClassifier(options)
    private lateinit var previewView: PreviewView
    private var detector: BlinkDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var statusText by mutableStateOf(blinkVoiceRealtimeStatusLabel(null))
    private var detailText by mutableStateOf("识别到单眨、双眨、长闭眼后会在下方记录")
    private val recognitionLogs = mutableStateListOf<String>()
    private var closedNotified = false
    private var headlessMode = false
    private var lastDeliveredEventType: String? = null
    private var lastDeliveredEventAtMs: Long = 0L
    private val headlessCloseRequest: () -> Unit = { finishCapture() }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        headlessMode = intent.getBooleanExtra(BlinkVoiceHeadlessExtraName, false)
        setFinishOnTouchOutside(false)
        configureFloatingWindow()
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        if (headlessMode) {
            FloatingChatBlinkVoiceBridge.registerHeadlessCaptureCloser(headlessCloseRequest)
            setContentView(previewView)
        } else {
            setContent {
                BlinkVoiceFloatingCaptureContent(
                    previewView = previewView,
                    statusText = statusText,
                    detailText = detailText,
                    recognitionLogs = recognitionLogs,
                    onClose = ::finishCapture
                )
            }
        }
        if (hasCameraPermission()) {
            startDetection()
        } else {
            statusText = "需要相机权限"
            detailText = "请允许相机权限后继续眨眼识别"
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyFloatingWindowSize()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFloatingWindowSize()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA_PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startDetection()
        } else {
            statusText = "相机权限未授权"
            detailText = "无法打开摄像头，请授权后重新进入眨眼测试"
        }
    }

    override fun onResult(
        result: FaceLandmarkerResult,
        frameTimeMs: Long,
        leftEyeEar: Float,
        rightEyeEar: Float,
        leftEyeClosed: Boolean,
        rightEyeClosed: Boolean,
        blinkCount: Int,
        inferenceTimeMs: Long,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int
    ) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredFrameTimeMs = frameTimeMs
        val hasFace = result.faceLandmarks().isNotEmpty()
        val now = SystemClock.elapsedRealtime()
        val event = classifier.accept(now, hasFace, leftEyeEar, rightEyeEar)
            ?.takeIf { options.eventTypes.contains(it.eventType) }

        runOnUiThread {
            if (event != null) {
                showRecognizedEvent(
                    event = event,
                    inferenceTimeMs = inferenceTimeMs,
                    faceCount = result.faceLandmarks().size
                )
            }
        }
    }

    override fun onError(message: String) {
        Log.w(TAG, "BlinkVoice detector error: $message")
        runOnUiThread {
            statusText = "识别模块异常"
            detailText = "请关闭后重新进入眨眼测试"
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        detector?.close()
        cameraExecutor.shutdown()
        if (headlessMode) {
            FloatingChatBlinkVoiceBridge.clearHeadlessCaptureCloser(headlessCloseRequest)
        }
        notifyCaptureClosed()
        super.onDestroy()
    }

    private fun configureFloatingWindow() {
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        if (headlessMode) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            window.setGravity(Gravity.TOP or Gravity.START)
        } else {
            window.setGravity(Gravity.CENTER)
        }
        applyFloatingWindowSize()
    }

    private fun applyFloatingWindowSize() {
        if (headlessMode) {
            window.setLayout(dpToPx(HEADLESS_WINDOW_SIZE_DP), dpToPx(HEADLESS_WINDOW_SIZE_DP))
            window.setGravity(Gravity.TOP or Gravity.START)
            return
        }
        val widthPx = min(dpToPx(FLOATING_WINDOW_WIDTH_DP), (resources.displayMetrics.widthPixels * 0.88f).roundToInt())
        val heightPx = min(dpToPx(FLOATING_WINDOW_HEIGHT_DP), (resources.displayMetrics.heightPixels * 0.72f).roundToInt())
        window.setLayout(widthPx, heightPx)
        window.setGravity(Gravity.CENTER)
    }

    private fun startDetection() {
        statusText = "正在加载人脸模型"
        detailText = "请保持手机正对人脸"
        runCatching {
            detector = BlinkDetector(this, this).also { blinkDetector ->
                blinkDetector.setup()
                blinkDetector.elaCloseThreshold = BlinkDetector.ELA_CLOSE_THRESHOLD
            }
        }.onFailure { error ->
            Log.e(TAG, "failed to initialize BlinkVoice detector", error)
            statusText = "模型加载失败"
            detailText = "识别模型启动失败，请关闭后重试"
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            runCatching {
                bindCamera(cameraProviderFuture.get())
            }.onFailure { error ->
                Log.e(TAG, "failed to bind BlinkVoice camera", error)
                statusText = "摄像头启动失败"
                detailText = "当前摄像头不可用，请关闭后重试"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("DEPRECATION")
    private fun bindCamera(provider: ProcessCameraProvider) {
        cameraProvider = provider
        val preview = Preview.Builder().build().also { preview ->
            preview.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor, ::processImage)
            }
        provider.unbindAll()
        provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            imageAnalysis
        )
        statusText = blinkVoiceRealtimeStatusLabel(null)
        detailText = "支持单眨、双眨、长闭眼，识别记录保留在下方"
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val mpImage = BitmapImageBuilder(imageProxy.toBitmap()).build()
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            detector?.detectAsync(mpImage, timestampMs, rotationDegrees)
        } catch (error: Throwable) {
            Log.w(TAG, "failed to process BlinkVoice frame", error)
            runOnUiThread {
                statusText = "画面处理失败"
                detailText = "请保持手机稳定，或关闭后重试"
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun showRecognizedEvent(
        event: BlinkCaptureResult,
        inferenceTimeMs: Long,
        faceCount: Int
    ) {
        val eventType = event.eventType.name
        val nextStatus = blinkVoiceRealtimeStatusLabel(eventType)
        val nextDetail = "持续 ${event.durationMs.coerceAtLeast(0L)}ms · 置信度 ${confidencePercent(event.confidence)}% · ${faceCount.coerceAtLeast(1)} 张脸 · ${inferenceTimeMs.coerceAtLeast(0L)}ms"
        statusText = nextStatus
        detailText = nextDetail
        recognitionLogs += blinkVoiceStatusLogEntry(eventType)
        while (recognitionLogs.size > MAX_RECOGNITION_LOG_COUNT) {
            recognitionLogs.removeAt(0)
        }
        deliverRecognizedEventIfNeeded(event)
    }

    private fun finishCapture() {
        notifyCaptureClosed()
        finish()
    }

    private fun deliverRecognizedEventIfNeeded(event: BlinkCaptureResult) {
        val eventType = event.eventType.name
        val now = SystemClock.elapsedRealtime()
        if (eventType == lastDeliveredEventType &&
            now - lastDeliveredEventAtMs < MIN_DELIVER_EVENT_INTERVAL_MS
        ) {
            return
        }
        lastDeliveredEventType = eventType
        lastDeliveredEventAtMs = now
        FloatingChatBlinkVoiceBridge.deliverResult(
            eventType = eventType,
            durationMs = event.durationMs,
            confidence = event.confidence,
            headless = headlessMode
        )
        if (headlessMode) {
            finishCapture()
        }
    }

    private fun notifyCaptureClosed() {
        if (closedNotified) return
        closedNotified = true
        FloatingChatBlinkVoiceBridge.notifyCaptureClosed()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val REQUEST_CAMERA_PERMISSION = 9301
        const val FLOATING_WINDOW_WIDTH_DP = 330
        const val FLOATING_WINDOW_HEIGHT_DP = 470
        const val HEADLESS_WINDOW_SIZE_DP = 1
        const val MAX_RECOGNITION_LOG_COUNT = 20
        const val MIN_DELIVER_EVENT_INTERVAL_MS = 1200L
    }
}

@Composable
private fun BlinkVoiceFloatingCaptureContent(
    previewView: PreviewView,
    statusText: String,
    detailText: String,
    recognitionLogs: List<String>,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF2151D23))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .align(Alignment.TopCenter)
                .background(Color(0x7A151D23))
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp, end = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC101820)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = Color(0xFFEAF3F7),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "眨眼测试",
                    color = Color(0xFFF0F7FA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "实时识别中",
                    color = Color(0xCFE4EEF2),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xA6101820))
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color(0xFFEAF3F7),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xD9141C22))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = statusText,
                color = Color(0xFFF0F7FA),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = detailText,
                color = Color(0xDDE1ECEF),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "识别记录",
                color = Color(0xBFE1ECEF),
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val visibleLogs = recognitionLogs.takeLast(5)
                if (visibleLogs.isEmpty()) {
                    Text(
                        text = "暂无识别记录",
                        color = Color(0x99E1ECEF),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                } else {
                    visibleLogs.forEach { log ->
                        Text(
                            text = log,
                            color = Color(0xFFEAF3F7),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun confidencePercent(confidence: Float): Int {
    return (confidence.coerceIn(0f, 1f) * 100f).roundToInt()
}
