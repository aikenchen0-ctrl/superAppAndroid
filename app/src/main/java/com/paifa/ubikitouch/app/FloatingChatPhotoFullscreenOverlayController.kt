package com.paifa.ubikitouch.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.UbikiAccessibilityService
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.platform.ViewCompositionStrategy

internal data class PhotoFullscreenOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun photoFullscreenOverlayWindowPresentation() = PhotoFullscreenOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun photoFullscreenStatusBarHeightDp(): Int = 30

internal fun photoFullscreenEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

internal fun photoFullscreenExitTranslationY(heightPx: Int): Float = -heightPx.coerceAtLeast(0).toFloat()

/**
 * 拍摄照片悬浮页的宿主。测试流程：从右侧“拍摄照片”进入，拍照后发送，再检查聊天中新增真实图片消息。
 */
object FloatingChatPhotoOverlayHost {
    private var controller: FloatingChatPhotoFullscreenOverlayController? = null

    fun show(): Boolean {
        val service = UbikiAccessibilityService.instance ?: return false
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val active = controller ?: FloatingChatPhotoFullscreenOverlayController(
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

private class PhotoOverlayComposeOwner : LifecycleOwner, SavedStateRegistryOwner {
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

internal data class CapturedPhoto(
    val file: File,
    val orientation: FloatingChatThumbnailOrientation,
    val aspectRatio: Float?
)

/**
 * 全屏无障碍悬浮拍照页，负责 CameraX 预览、真实文件保存和媒体消息回传。
 * 测试流程：允许相机权限，点击快门，选择发送，确认 [FloatingChatMediaPickerBridge] 收到真实文件 URI。
 */
internal class FloatingChatPhotoFullscreenOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onClosed: () -> Unit
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var view: androidx.compose.ui.platform.ComposeView? = null
    private var owner: PhotoOverlayComposeOwner? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var isClosed = false

    var capturedPhoto by mutableStateOf<CapturedPhoto?>(null)
        private set
    var cameraError by mutableStateOf<String?>(null)
        private set
    var isCapturing by mutableStateOf(false)
        private set

    /** UI 入口：添加无障碍全屏 View，使用 translationY 从屏幕底部滑入。 */
    fun show() {
        if (view != null) return
        isClosed = false
        val composeOwner = PhotoOverlayComposeOwner()
        val preview = PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        previewView = preview
        owner = composeOwner
        val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = photoFullscreenEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { FloatingChatPhotoFullscreenScreen(this@FloatingChatPhotoFullscreenOverlayController) }
        }
        runCatching { windowManager.addView(composeView, layoutParams()) }
            .onSuccess {
                view = composeView
                composeView.post {
                    if (view !== composeView) return@post
                    composeView.animate().translationY(0f).alpha(1f)
                        .setDuration(SHOW_DURATION_MILLIS)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                    bindCamera(composeOwner, preview)
                }
            }
            .onFailure { error ->
                composeOwner.destroy()
                owner = null
                previewView = null
                cameraExecutor.shutdown()
                Log.w(TAG, "failed to add photo fullscreen overlay", error)
                FloatingChatMediaPickerBridge.notifyPickerClosed()
                onClosed()
            }
    }

    /** Toolbar 返回：View 的 translationY 向上离场，动画结束后释放相机和窗口。 */
    fun dismiss() {
        val current = view ?: return
        current.animate().cancel()
        val height = current.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        current.animate().translationY(photoFullscreenExitTranslationY(height)).alpha(0f)
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
        imageCapture = null
        cameraExecutor.shutdown()
        owner?.destroy()
        owner = null
        previewView = null
        current?.let { runCatching { windowManager.removeViewImmediate(it) } }
            ?.onFailure { Log.w(TAG, "failed to remove photo fullscreen overlay", it) }
        if (!isClosed) {
            isClosed = true
            FloatingChatMediaPickerBridge.notifyPickerClosed()
            onClosed()
        }
    }

    /** CameraX 后置相机绑定，不可用时明确展示错误，不发送伪造媒体。 */
    private fun bindCamera(lifecycleOwner: LifecycleOwner, preview: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(displayRotation())
                    .build()
                val cameraPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    cameraPreview,
                    capture
                )
                cameraProvider = provider
                imageCapture = capture
                cameraError = null
            }.onFailure { error ->
                Log.w(TAG, "failed to bind photo camera", error)
                cameraError = "相机启动失败，请返回后重试"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** 真实 CameraX 拍照：保存文件、归一化 EXIF 方向，并将结果交给预览页。 */
    fun capturePhoto() {
        if (isCapturing || capturedPhoto != null) return
        val capture = imageCapture ?: run {
            cameraError = "相机尚未准备完成"
            return
        }
        isCapturing = true
        cameraError = null
        capture.targetRotation = displayRotation()
        val outputFile = File(captureDir(), "photo-${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val normalizedFile = normalizeCapturedPhotoOrientation(outputFile)
                    val meta = imageMetaFor(normalizedFile)
                    view?.post {
                        isCapturing = false
                        capturedPhoto = CapturedPhoto(normalizedFile, meta.orientation, meta.aspectRatio)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "failed to capture photo", exception)
                    view?.post {
                        isCapturing = false
                        cameraError = "拍摄失败，请重试"
                    }
                }
            }
        )
    }

    fun retakePhoto() {
        capturedPhoto = null
        cameraError = null
    }

    /** 接口回传：仅把 CameraX 实际写入的图片文件回传到现有聊天媒体桥。 */
    fun sendCapturedPhoto() {
        val photo = capturedPhoto ?: return
        FloatingChatMediaPickerBridge.deliverPickedMedia(
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = Uri.fromFile(photo.file),
            previewUri = Uri.fromFile(photo.file),
            orientation = photo.orientation,
            aspectRatio = photo.aspectRatio
        )
        dismiss()
    }

    fun cameraPreviewView(): PreviewView? = previewView

    private fun captureDir(): File = File(context.cacheDir, "floating-chat-camera").apply { mkdirs() }

    private fun displayRotation(): Int = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }

    private fun imageMetaFor(file: File): CapturedPhotoMeta {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth.coerceAtLeast(1)
        val height = options.outHeight.coerceAtLeast(1)
        val rotation = imageExifRotationDegrees(file)
        val rotated = rotation == 90 || rotation == 270
        val displayWidth = if (rotated) height else width
        val displayHeight = if (rotated) width else height
        val aspectRatio = displayWidth.toFloat() / displayHeight.toFloat()
        return CapturedPhotoMeta(
            orientation = if (aspectRatio <= 1f) FloatingChatThumbnailOrientation.Vertical else FloatingChatThumbnailOrientation.Horizontal,
            aspectRatio = aspectRatio
        )
    }

    private fun normalizeCapturedPhotoOrientation(file: File): File {
        val rotationDegrees = imageExifRotationDegrees(file)
        if (rotationDegrees == 0) return file
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        val normalized = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true
        )
        file.outputStream().use { output -> normalized.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, output) }
        bitmap.recycle()
        normalized.recycle()
        runCatching {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun imageExifRotationDegrees(file: File): Int = runCatching {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private data class CapturedPhotoMeta(
        val orientation: FloatingChatThumbnailOrientation,
        val aspectRatio: Float?
    )

    private companion object {
        const val TAG = "FloatingChatPhoto"
        const val PHOTO_JPEG_QUALITY = 95
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingChatPhotoFullscreenScreen(controller: FloatingChatPhotoFullscreenOverlayController) {
    val photo = controller.capturedPhoto
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.height(photoFullscreenStatusBarHeightDp().dp))
        TopAppBar(
            title = {
                Text(
                    text = "拍摄照片",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            navigationIcon = {
                IconButton(onClick = controller::dismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        if (photo == null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { controller.cameraPreviewView() ?: PreviewView(it) },
                    modifier = Modifier.fillMaxSize()
                )
                controller.cameraError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
            }
            PhotoCaptureControls(
                enabled = !controller.isCapturing,
                onCapture = controller::capturePhoto
            )
        } else {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { imageView -> imageView.setImageURI(Uri.fromFile(photo.file)) },
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)
            )
            PhotoPreviewControls(onRetake = controller::retakePhoto, onSend = controller::sendCapturedPhoto)
        }
    }
}

@Composable
private fun PhotoCaptureControls(enabled: Boolean, onCapture: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onCapture,
            enabled = enabled,
            shape = androidx.compose.foundation.shape.CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(72.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "拍摄照片", modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun PhotoPreviewControls(onRetake: () -> Unit, onSend: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Button(onClick = onRetake, colors = ButtonDefaults.outlinedButtonColors()) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("重拍", fontWeight = FontWeight.Normal)
        }
        Button(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("发送", fontWeight = FontWeight.Normal)
        }
    }
}
