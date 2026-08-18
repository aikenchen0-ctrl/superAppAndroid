package com.paifa.univerge.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blinkvoice.visual.api.BlinkCaptureOptions
import com.blinkvoice.visual.api.BlinkCaptureResult
import com.blinkvoice.visual.detector.BlinkDetector
import com.blinkvoice.visual.events.BlinkEventClassifier
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.paifa.univerge.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.univerge.accessibility.UniVergeAccessibilityService
import com.paifa.univerge.accessibility.blinkVoiceCaptureAutoFinishOnEvent
import com.paifa.univerge.accessibility.blinkVoiceRealtimeStatusLabel
import com.paifa.univerge.accessibility.blinkVoiceStatusLogEntry
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceMotion
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopBarDefaults
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class BlinkVoiceFullscreenOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun blinkVoiceFullscreenOverlayWindowPresentation() = BlinkVoiceFullscreenOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun blinkVoiceFullscreenStatusBarHeightDp(): Int = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp

internal fun blinkVoiceFullscreenEntryTranslationY(heightPx: Int): Float = FloatingWorkspaceMotion.enterTranslationY(heightPx)

internal fun blinkVoiceFullscreenExitTranslationY(heightPx: Int): Float = FloatingWorkspaceMotion.exitTranslationY(heightPx)

/** App-module host for the right-rail test. Call [show] after the system camera permission is granted. */
object FloatingChatBlinkVoiceOverlayHost {
    private var controller: BlinkVoiceFullscreenOverlayController? = null

    fun show(): Boolean {
        val service = UniVergeAccessibilityService.instance ?: return false
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val active = controller ?: BlinkVoiceFullscreenOverlayController(
            context = service,
            windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
            onClosed = { controller = null }
        ).also { controller = it }
        active.show()
        return true
    }

    fun dismissImmediately() {
        controller?.dismissImmediately()
        controller = null
    }
}

internal data class BlinkRecognitionLog(
    val id: Long,
    val title: String,
    val detail: String
)

private class BlinkOverlayComposeOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * Fullscreen M3 UI plus the local camera detector. Test by opening the right-rail item, facing the
 * front camera, then verifying single blink, double blink and long close appear in the record tab.
 */
internal class BlinkVoiceFullscreenOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClosed: () -> Unit
) : BlinkDetector.BlinkListener {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val options = BlinkCaptureOptions.Builder()
        .setAutoFinishOnEvent(blinkVoiceCaptureAutoFinishOnEvent())
        .build()
    private val classifier = BlinkEventClassifier(options)
    private val logs = mutableStateListOf<BlinkRecognitionLog>()
    private var view: ComposeView? = null
    private var owner: BlinkOverlayComposeOwner? = null
    private var previewView: PreviewView? = null
    private var detector: BlinkDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastDeliveredEventType: String? = null
    private var lastDeliveredEventAtMs = 0L
    private var isClosed = false

    var statusText by mutableStateOf("正在加载人脸模型")
        private set
    var detailText by mutableStateOf("请正视前置摄像头，识别结果会同步回悬浮聊天")
        private set
    var faceCount by mutableStateOf(0)
        private set
    var inferenceTimeMs by mutableStateOf(0L)
        private set

    /** UI entry point. The WindowManager call is guarded so a bad token never leaves camera resources running. */
    fun show() {
        if (view != null) return
        isClosed = false
        val composeOwner = BlinkOverlayComposeOwner()
        val preview = PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = FloatingWorkspaceMotion.enterTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { BlinkVoiceFullscreenScreen(controller = this@BlinkVoiceFullscreenOverlayController) }
        }
        runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            previewView = preview
            composeView.post {
                if (view !== composeView) return@post
                composeView.animate().translationY(0f).alpha(1f)
                    .setDuration(SHOW_DURATION_MILLIS)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
                startDetection(composeOwner, preview)
            }
        }.onFailure { error ->
            composeOwner.destroy()
            previewView = null
            Log.w(TAG, "failed to add BlinkVoice fullscreen overlay", error)
            onClosed()
        }
    }

    /** Toolbar back action: upward property animation, then CameraX and the accessibility overlay are released. */
    fun dismiss() {
        val current = view ?: return
        current.animate().cancel()
        val height = current.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        current.animate().translationY(FloatingWorkspaceMotion.exitTranslationY(height)).alpha(0f)
            .setDuration(HIDE_DURATION_MILLIS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction { if (view === current) dismissImmediately() }
            .start()
    }

    fun dismissImmediately() {
        val current = view
        view = null
        current?.animate()?.cancel()
        cameraProvider?.unbindAll()
        cameraProvider = null
        detector?.close()
        detector = null
        cameraExecutor.shutdown()
        owner?.destroy()
        owner = null
        previewView = null
        current?.let { runCatching { windowManager.removeViewImmediate(it) } }
            ?.onFailure { Log.w(TAG, "failed to remove BlinkVoice fullscreen overlay", it) }
        if (!isClosed) {
            isClosed = true
            FloatingChatBlinkVoiceBridge.notifyCaptureClosed()
            onClosed()
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
        val event = classifier.accept(
            SystemClock.elapsedRealtime(),
            result.faceLandmarks().isNotEmpty(),
            leftEyeEar,
            rightEyeEar
        )?.takeIf { options.eventTypes.contains(it.eventType) }
        mainHandler.post {
            faceCount = result.faceLandmarks().size
            this.inferenceTimeMs = inferenceTimeMs.coerceAtLeast(0L)
            event?.let { showRecognizedEvent(it) }
        }
    }

    override fun onError(message: String) {
        Log.w(TAG, "BlinkVoice detector error: $message")
        mainHandler.post {
            statusText = "识别模块异常"
            detailText = "请关闭后重新进入眨眼测试"
        }
    }

    /** Local camera interface: bind CameraX and BlinkDetector to the overlay lifecycle, no remote API is simulated. */
    private fun startDetection(lifecycleOwner: LifecycleOwner, preview: PreviewView) {
        runCatching {
            detector = BlinkDetector(context, this).also {
                it.setup()
                it.elaCloseThreshold = BlinkDetector.ELA_CLOSE_THRESHOLD
            }
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({ bindCamera(future.get(), lifecycleOwner, preview) }, ContextCompat.getMainExecutor(context))
            }
        }.onFailure {
            statusText = "模型加载失败"
            detailText = "无法启动眨眼识别，请关闭后重试"
        }
    }

    @Suppress("DEPRECATION")
    private fun bindCamera(provider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner, preview: PreviewView) {
        runCatching {
            cameraProvider = provider
            val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::processImage) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, analysis)
            statusText = blinkVoiceRealtimeStatusLabel(null)
            detailText = "支持单眨、双眨和长闭眼，识别结果会记录并回传聊天窗口"
        }.onFailure {
            statusText = "摄像头启动失败"
            detailText = "当前前置摄像头不可用，请关闭后重试"
        }
    }

    private fun processImage(image: ImageProxy) {
        try {
            val mpImage = BitmapImageBuilder(image.toBitmap()).build()
            detector?.detectAsync(mpImage, image.imageInfo.timestamp / 1_000_000L, image.imageInfo.rotationDegrees)
        } catch (error: Throwable) {
            Log.w(TAG, "failed to process BlinkVoice frame", error)
        } finally {
            image.close()
        }
    }

    /** Existing FloatingChatBlinkVoiceBridge is the result interface used by the chat input and result message path. */
    private fun showRecognizedEvent(event: BlinkCaptureResult) {
        val type = event.eventType.name
        statusText = blinkVoiceRealtimeStatusLabel(type)
        detailText = "持续 ${event.durationMs.coerceAtLeast(0L)}ms，置信度 ${confidencePercent(event.confidence)}%，检测到 ${faceCount.coerceAtLeast(1)} 张脸"
        logs += BlinkRecognitionLog(
            id = SystemClock.elapsedRealtime(),
            title = blinkVoiceStatusLogEntry(type),
            detail = "持续 ${event.durationMs.coerceAtLeast(0L)}ms，置信度 ${confidencePercent(event.confidence)}%"
        )
        while (logs.size > MAX_LOG_COUNT) logs.removeAt(0)
        val now = SystemClock.elapsedRealtime()
        if (type == lastDeliveredEventType && now - lastDeliveredEventAtMs < MIN_DELIVERY_INTERVAL_MILLIS) return
        lastDeliveredEventType = type
        lastDeliveredEventAtMs = now
        FloatingChatBlinkVoiceBridge.deliverResult(type, event.durationMs, event.confidence)
    }

    fun logs(): List<BlinkRecognitionLog> = logs.toList()

    fun cameraPreviewView(): PreviewView? = previewView

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private companion object {
        const val TAG = "BlinkVoiceFullscreen"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
        const val MAX_LOG_COUNT = 20
        const val MIN_DELIVERY_INTERVAL_MILLIS = 1200L
    }
}

private enum class BlinkVoiceTab(val label: String) { Recognition("识别"), Records("记录") }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BlinkVoiceFullscreenScreen(controller: BlinkVoiceFullscreenOverlayController) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { BlinkVoiceTab.entries.size }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：复用 UI组件 的 M3 工具栏，30dp 状态区由工具栏 padding 承载，避免独立空白区域跳变。
        // 测试流程：点击右侧眨眼测试，确认页面自下向上进入；点击左上返回，确认页面向顶部退出。
        FloatingWorkspaceTopAppBar(title = "眨眼测试", onBack = controller::dismiss)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            BlinkVoiceTab.entries.forEachIndexed { index, tab ->
                Tab(selected = pagerState.currentPage == index, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(tab.label, fontWeight = FontWeight.Normal) })
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (BlinkVoiceTab.entries[page]) {
                BlinkVoiceTab.Recognition -> BlinkRecognitionPage(controller)
                BlinkVoiceTab.Records -> BlinkRecordPage(controller)
            }
        }
    }
}

@Composable
private fun BlinkRecognitionPage(controller: BlinkVoiceFullscreenOverlayController) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("前置摄像头识别", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    Text("正视摄像头后依次测试单眨、快速双眨与长闭眼", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    AndroidView(factory = { controller.cameraPreviewView() ?: PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(260.dp))
                }
            }
        }
        item { BlinkStatusCard(controller) }
    }
}

@Composable
private fun BlinkStatusCard(controller: BlinkVoiceFullscreenOverlayController) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("实时状态", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text(controller.statusText, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            Text(controller.detailText, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("人脸 ${controller.faceCount}，推理 ${controller.inferenceTimeMs}ms", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BlinkRecordPage(controller: BlinkVoiceFullscreenOverlayController) {
    val entries = controller.logs()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BlinkStatusCard(controller) }
        if (entries.isEmpty()) {
            item { Text("暂无识别记录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(entry.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Normal)
                        Text(entry.detail, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun confidencePercent(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).roundToInt()
