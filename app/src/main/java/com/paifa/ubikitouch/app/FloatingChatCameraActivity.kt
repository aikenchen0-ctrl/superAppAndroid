package com.paifa.ubikitouch.app

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "FloatingChatCamera"

class FloatingChatCameraActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var pendingVideoFile: File? = null
    private var capturedMedia by mutableStateOf<CapturedMedia?>(null)
    private var recording by mutableStateOf(false)
    private var recordingProgress by mutableStateOf(0f)
    private var recordingStartingAtMs = 0L
    private val recordingProgressRunnable = object : Runnable {
        override fun run() {
            updateRecordingProgress()
            if (recording) {
                mainHandler.postDelayed(this, RECORDING_PROGRESS_FRAME_MS)
            }
        }
    }
    private val maxRecordingStopRunnable = Runnable {
        stopRecording()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS)
        }
        setContentView(cameraContentView())
        if (hasPermissions()) {
            bindCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSIONS && hasPermissions()) {
            bindCamera()
        } else {
            Toast.makeText(this, "需要相机和录音权限", Toast.LENGTH_SHORT).show()
            finishCapture()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(maxRecordingStopRunnable)
        mainHandler.removeCallbacks(recordingProgressRunnable)
        activeRecording?.close()
        cameraExecutor.shutdown()
        FloatingChatMediaPickerBridge.notifyPickerClosed()
        super.onDestroy()
    }

    private fun cameraContentView(): FrameLayout {
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(previewView)
            addView(
                ComposeView(this@FloatingChatCameraActivity).apply {
                    setContent {
                        CameraOverlay(
                            capturedMedia = capturedMedia,
                            recording = recording,
                            recordingProgress = recordingProgress,
                            onClose = ::finishCapture,
                            onCaptureTap = ::capturePhoto,
                            onRecordStart = ::startRecording,
                            onRecordStop = ::stopRecording,
                            onRetake = { capturedMedia = null },
                            onSend = ::sendCapturedMedia
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { preview ->
                preview.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(displayRotation())
                .build()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
                .apply { targetRotation = displayRotation() }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture
                )
            }.onFailure {
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        if (capturedMedia != null || recording) return
        val capture = imageCapture ?: return
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
                    mainHandler.post {
                        capturedMedia = CapturedMedia(
                            kind = FloatingChatPrototype.PickedMediaKind.Image,
                            uri = Uri.fromFile(normalizedFile),
                            orientation = meta.orientation,
                            aspectRatio = meta.aspectRatio
                        )
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainHandler.post {
                        Toast.makeText(this@FloatingChatCameraActivity, "拍照失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun startRecording() {
        if (capturedMedia != null || recording) return
        val capture = videoCapture ?: return
        capture.targetRotation = displayRotation()
        val outputFile = File(captureDir(), "video-${System.currentTimeMillis()}.mp4")
        pendingVideoFile = outputFile
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pendingRecording: PendingRecording = capture.output.prepareRecording(this, outputOptions)
        if (hasAudioPermission()) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }
        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recording = true
                    recordingStartingAtMs = System.currentTimeMillis()
                    recordingProgress = 0f
                    mainHandler.removeCallbacks(maxRecordingStopRunnable)
                    mainHandler.postDelayed(maxRecordingStopRunnable, CAMERA_VIDEO_MAX_DURATION_MS)
                    mainHandler.removeCallbacks(recordingProgressRunnable)
                    mainHandler.post(recordingProgressRunnable)
                }
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    activeRecording = null
                    mainHandler.removeCallbacks(maxRecordingStopRunnable)
                    mainHandler.removeCallbacks(recordingProgressRunnable)
                    recordingProgress = 0f
                    if (!event.hasError()) {
                        cameraExecutor.execute {
                            val meta = videoMetaFor(outputFile)
                            val previewFile = videoPosterFileFor(outputFile)
                            mainHandler.post {
                                capturedMedia = CapturedMedia(
                                    kind = FloatingChatPrototype.PickedMediaKind.Video,
                                    uri = Uri.fromFile(outputFile),
                                    previewUri = previewFile?.let(Uri::fromFile),
                                    orientation = meta.orientation,
                                    aspectRatio = meta.aspectRatio
                                )
                            }
                        }
                    } else {
                        Toast.makeText(this, "录像失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        mainHandler.removeCallbacks(maxRecordingStopRunnable)
        mainHandler.removeCallbacks(recordingProgressRunnable)
        val recordingToStop = activeRecording ?: return
        recordingToStop.stop()
        activeRecording = null
    }

    private fun updateRecordingProgress() {
        if (!recording || recordingStartingAtMs <= 0L) {
            recordingProgress = 0f
            return
        }
        val elapsedMs = System.currentTimeMillis() - recordingStartingAtMs
        recordingProgress = (elapsedMs.toFloat() / CAMERA_VIDEO_MAX_DURATION_MS).coerceIn(0f, 1f)
    }

    private fun sendCapturedMedia() {
        val media = capturedMedia ?: return
        FloatingChatMediaPickerBridge.deliverPickedMedia(
            mediaKind = media.kind,
            mediaUri = media.uri,
            previewUri = media.previewUri ?: media.uri,
            orientation = media.orientation,
            aspectRatio = media.aspectRatio
        )
        finishCapture()
    }

    private fun finishCapture() {
        FloatingChatMediaPickerBridge.notifyPickerClosed()
        finish()
    }

    private fun captureDir(): File {
        return File(cacheDir, "floating-chat-camera").apply { mkdirs() }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun displayRotation(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private fun imageMetaFor(file: File): CapturedMediaMeta {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return CapturedMediaMeta(FloatingChatThumbnailOrientation.Vertical, null)
        }
        return capturedMediaMeta(
            width = options.outWidth,
            height = options.outHeight,
            rotationDegrees = imageExifRotationDegrees(file)
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
        file.outputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, output)
        }
        bitmap.recycle()
        normalized.recycle()
        runCatching {
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                saveAttributes()
            }
        }
        return file
    }

    private fun imageExifRotationDegrees(file: File): Int {
        return runCatching {
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
    }

    private fun videoMetaFor(file: File): CapturedMediaMeta {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                    ?: 0
                capturedMediaMeta(width, height, rotation)
            }
        }.getOrElse {
            CapturedMediaMeta(FloatingChatThumbnailOrientation.Horizontal, null)
        }
    }

    private fun videoPosterFileFor(file: File): File? {
        return runCatching {
            val frame = MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L) * 1_000L
                videoPosterCandidateTimesUs(durationUs)
                    .firstNotNullOfOrNull { timeUs ->
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
            } ?: return@runCatching null
            val posterFile = File(captureDir(), "poster-${file.nameWithoutExtension}.jpg")
            posterFile.outputStream().use { output ->
                frame.compress(Bitmap.CompressFormat.JPEG, VIDEO_POSTER_JPEG_QUALITY, output)
            }
            frame.recycle()
            posterFile
        }.getOrNull()
    }

    private fun videoPosterCandidateTimesUs(durationUs: Long): List<Long> {
        val safeDurationUs = durationUs.coerceAtLeast(0L)
        return listOf(
            300_000L.coerceAtMost(safeDurationUs),
            600_000L.coerceAtMost(safeDurationUs),
            (safeDurationUs / 3L).coerceAtLeast(0L),
            0L
        ).distinct()
    }

    private fun capturedMediaMeta(width: Int, height: Int, rotationDegrees: Int): CapturedMediaMeta {
        val aspectRatio = capturedMediaAspectRatioFromDimensions(width, height, rotationDegrees)
        return CapturedMediaMeta(
            orientation = if ((aspectRatio ?: 1f) <= 1f) {
                FloatingChatThumbnailOrientation.Vertical
            } else {
                FloatingChatThumbnailOrientation.Horizontal
            },
            aspectRatio = aspectRatio
        )
    }

    private fun capturedMediaAspectRatioFromDimensions(
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): Float? {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val rotated = normalizedRotation == 90 || normalizedRotation == 270
        val displayWidth = if (rotated) safeHeight else safeWidth
        val displayHeight = if (rotated) safeWidth else safeHeight
        if (displayWidth <= 0 || displayHeight <= 0) return null
        return displayWidth.toFloat() / displayHeight.toFloat()
    }

    private companion object {
        const val REQUEST_CAMERA_PERMISSIONS = 9101
        const val CAMERA_VIDEO_MAX_DURATION_MS = 15_000L
        const val RECORDING_PROGRESS_FRAME_MS = 32L
        const val PHOTO_JPEG_QUALITY = 95
        const val VIDEO_POSTER_JPEG_QUALITY = 88
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

private data class CapturedMedia(
    val kind: FloatingChatPrototype.PickedMediaKind,
    val uri: Uri,
    val previewUri: Uri? = null,
    val orientation: FloatingChatThumbnailOrientation,
    val aspectRatio: Float?
)

private data class CapturedMediaMeta(
    val orientation: FloatingChatThumbnailOrientation,
    val aspectRatio: Float?
)

@Composable
private fun CameraOverlay(
    capturedMedia: CapturedMedia?,
    recording: Boolean,
    recordingProgress: Float,
    onClose: () -> Unit,
    onCaptureTap: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRetake: () -> Unit,
    onSend: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
        }

        if (capturedMedia == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShutterButton(
                    recording = recording,
                    progress = recordingProgress,
                    onTap = onCaptureTap,
                    onLongPressStart = onRecordStart,
                    onLongPressEnd = onRecordStop
                )
                Text(
                    text = "轻触拍照，长按录像",
                    color = Color(0xFFEAF0F2)
                )
            }
        } else {
            CapturedMediaPreview(
                capturedMedia = capturedMedia,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRetake,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA1A252C))
                ) {
                    Text("重拍", color = Color.White)
                }
                Button(
                    onClick = onSend,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF77C916))
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White)
                    Box(modifier = Modifier.width(6.dp))
                    Text("发送", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CapturedMediaPreview(
    capturedMedia: CapturedMedia,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 76.dp),
        contentAlignment = Alignment.Center
    ) {
        when (capturedMedia.kind) {
            FloatingChatPrototype.PickedMediaKind.Image,
            FloatingChatPrototype.PickedMediaKind.Any -> {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(min = 120.dp),
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            adjustViewBounds = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        view.setImageURI(capturedMedia.uri)
                    }
                )
            }
            FloatingChatPrototype.PickedMediaKind.Video -> {
                CapturedVideoPreview(
                    capturedMedia = capturedMedia,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun CapturedVideoPreview(
    capturedMedia: CapturedMedia,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playbackStarted by remember(capturedMedia.uri) { mutableStateOf(false) }
    var surfaceTexture by remember(capturedMedia.uri) { mutableStateOf<SurfaceTexture?>(null) }
    var mediaPlayerRef by remember(capturedMedia.uri) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(capturedMedia.uri) { mutableStateOf(false) }
    var playing by remember(capturedMedia.uri) { mutableStateOf(false) }
    var completed by remember(capturedMedia.uri) { mutableStateOf(false) }
    var failed by remember(capturedMedia.uri) { mutableStateOf(false) }
    val tapSource = remember { MutableInteractionSource() }
    val posterBitmap = remember(capturedMedia.previewUri) {
        capturedMedia.previewUri?.path
            ?.let(BitmapFactory::decodeFile)
    }

    fun togglePlayback() {
        val player = mediaPlayerRef
        when {
            !playbackStarted || failed -> {
                failed = false
                surfaceTexture = null
                playbackStarted = false
                playbackStarted = true
            }
            player == null || !prepared -> Unit
            player.isPlaying -> {
                player.pause()
                playing = false
            }
            completed -> {
                player.seekTo(0)
                player.start()
                completed = false
                playing = true
            }
            else -> {
                player.start()
                playing = true
            }
        }
    }

    DisposableEffect(capturedMedia.uri, playbackStarted, surfaceTexture) {
        val texture = surfaceTexture
        if (!playbackStarted || texture == null) {
            onDispose {}
        } else {
            prepared = false
            playing = false
            completed = false
            failed = false

            val surface = Surface(texture)
            val mediaPlayer = MediaPlayer()
            mediaPlayerRef = mediaPlayer
            var released = false

            fun releasePlayer() {
                if (released) return
                released = true
                runCatching {
                    mediaPlayer.setSurface(null)
                    mediaPlayer.setOnPreparedListener(null)
                    mediaPlayer.setOnCompletionListener(null)
                    mediaPlayer.setOnErrorListener(null)
                    if (mediaPlayer.isPlaying) mediaPlayer.stop()
                }.onFailure {
                    Log.w(TAG, "failed to stop captured video player", it)
                }
                runCatching {
                    mediaPlayer.reset()
                }.onFailure {
                    Log.w(TAG, "failed to reset captured video player", it)
                }
                runCatching {
                    mediaPlayer.release()
                }.onFailure {
                    Log.w(TAG, "failed to release captured video player", it)
                }
                runCatching {
                    surface.release()
                }.onFailure {
                    Log.w(TAG, "failed to release captured video surface", it)
                }
                if (mediaPlayerRef === mediaPlayer) {
                    mediaPlayerRef = null
                }
            }

            runCatching {
                mediaPlayer.setSurface(surface)
                mediaPlayer.isLooping = false
                mediaPlayer.setOnPreparedListener { player ->
                    prepared = true
                    failed = false
                    completed = false
                    player.start()
                    playing = true
                }
                mediaPlayer.setOnCompletionListener {
                    playing = false
                    completed = true
                }
                mediaPlayer.setOnErrorListener { _, _, _ ->
                    failed = true
                    playing = false
                    true
                }
                mediaPlayer.setDataSource(context, capturedMedia.uri)
                mediaPlayer.prepareAsync()
            }.onFailure {
                failed = true
                playing = false
                releasePlayer()
            }

            onDispose {
                prepared = false
                playing = false
                releasePlayer()
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (playbackStarted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                surfaceTexture = texture
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                surfaceTexture = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                    }
                },
                update = { view ->
                    if (view.isAvailable) {
                        surfaceTexture = view.surfaceTexture
                    }
                    applyCapturedVideoAspectFitTransform(view, capturedMedia.aspectRatio)
                }
            )
        }

        if (!playbackStarted || failed) {
            if (posterBitmap != null) {
                Image(
                    bitmap = posterBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111820)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("视频已录制", color = Color(0xFFEAF0F2))
                }
            }
        }

        if (failed) {
            Text(
                text = "播放失败，点击重试",
                color = Color(0xFFEAF0F2),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(Color(0xAA101820), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = tapSource,
                    indication = null,
                    onClick = ::togglePlayback
                ),
            contentAlignment = Alignment.Center
        ) {
            val showPlay = !playing
            IconButton(
                onClick = ::togglePlayback,
                modifier = Modifier
                    .size(58.dp)
                    .background(Color(0xAA101820), CircleShape)
            ) {
                if (showPlay) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

private fun applyCapturedVideoAspectFitTransform(
    textureView: TextureView,
    videoAspectRatio: Float?
) {
    val width = textureView.width
    val height = textureView.height
    if (width <= 0 || height <= 0) return
    val aspectRatio = videoAspectRatio?.takeIf { it > 0f } ?: return
    val viewRatio = width.toFloat() / height.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (viewRatio > aspectRatio) {
        scaleX = aspectRatio / viewRatio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewRatio / aspectRatio
    }
    textureView.setTransform(
        Matrix().apply {
            setScale(scaleX, scaleY, width / 2f, height / 2f)
        }
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ShutterButton(
    recording: Boolean,
    progress: Float,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit
) {
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pressed by remember { mutableStateOf(false) }
    var videoGestureActive by remember { mutableStateOf(false) }
    var pendingRecordStart by remember { mutableStateOf<Runnable?>(null) }

    fun clearPendingRecordStart() {
        pendingRecordStart?.let(handler::removeCallbacks)
        pendingRecordStart = null
    }

    DisposableEffect(Unit) {
        onDispose {
            clearPendingRecordStart()
        }
    }

    Box(
        modifier = Modifier
            .size(if (recording) 88.dp else 76.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        clearPendingRecordStart()
                        pressed = true
                        videoGestureActive = false
                        val recordStart = Runnable {
                            if (pressed && !videoGestureActive) {
                                videoGestureActive = true
                                onLongPressStart()
                            }
                        }
                        pendingRecordStart = recordStart
                        handler.postDelayed(recordStart, CAMERA_RECORD_START_DELAY_MS)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        clearPendingRecordStart()
                        pressed = false
                        if (videoGestureActive) {
                            videoGestureActive = false
                            onLongPressEnd()
                        } else {
                            onTap()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        clearPendingRecordStart()
                        pressed = false
                        if (videoGestureActive) {
                            videoGestureActive = false
                            onLongPressEnd()
                        }
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseStroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                color = if (recording) Color(0x66F8FCFF) else Color(0xCCEAF0F2),
                style = baseStroke
            )
            if (recording) {
                drawArc(
                    color = Color(0xFF77C916),
                    startAngle = -90f,
                    sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                    useCenter = false,
                    style = baseStroke
                )
            }
        }
        Box(
            modifier = Modifier
                .size(if (recording) 70.dp else 64.dp)
                .clip(CircleShape)
                .background(if (recording) Color(0xFFE53E3E) else Color(0xFFEAF0F2))
        )
        Box(
            modifier = Modifier
                .size(if (recording) 30.dp else 50.dp)
                .clip(CircleShape)
                .background(if (recording) Color(0xFFB91C1C) else Color(0xFFFDFEFE))
        )
    }
}

private const val CAMERA_RECORD_START_DELAY_MS = 180L
