package com.paifa.ubikitouch.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.FloatingChatMediaTarget
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FloatingChatMediaPickerActivity : Activity() {
    private val pickerLifecycle = FloatingChatMediaPickerLifecycle(
        notifyPickerClosed = { FloatingChatMediaPickerBridge.notifyPickerClosed() },
        finishPicker = { finish() }
    )
    private val mediaKind: FloatingChatPrototype.PickedMediaKind by lazy {
        intent.getStringExtra(FloatingChatMediaPickerBridge.EXTRA_MEDIA_KIND)
            ?.let { value ->
                runCatching { FloatingChatPrototype.PickedMediaKind.valueOf(value) }.getOrNull()
            }
            ?: FloatingChatPrototype.PickedMediaKind.Image
    }
    private val mediaTarget: FloatingChatMediaTarget by lazy {
        intent.getStringExtra(FloatingChatMediaPickerBridge.EXTRA_MEDIA_TARGET)
            ?.let { value ->
                runCatching { FloatingChatMediaTarget.valueOf(value) }.getOrNull()
            }
            ?: FloatingChatMediaTarget.Chat
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            startActivityForResult(pickIntentFor(mediaKind), REQUEST_PICK_MEDIA)
        }
    }

    @Deprecated("Used for the platform media picker result bridge.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_MEDIA && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val pickedKind = resolvedMediaKindFor(uri, mediaKind)
                grantReadAccess(uri)
                pickerLifecycle.startPickedMediaProcessing()
                processPickedMediaInBackground(pickedKind, uri, mediaTarget)
                return
            }
        }
        pickerLifecycle.cancelPicker()
    }

    override fun onDestroy() {
        pickerLifecycle.onDestroy(isChangingConfigurations)
        super.onDestroy()
    }

    private fun pickIntentFor(kind: FloatingChatPrototype.PickedMediaKind): Intent {
        val type = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any -> "image/* video/*"
            FloatingChatPrototype.PickedMediaKind.Image -> "image/*"
            FloatingChatPrototype.PickedMediaKind.Video -> "video/*"
        }
        val mediaUri = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any -> MediaStore.Files.getContentUri("external")
            FloatingChatPrototype.PickedMediaKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FloatingChatPrototype.PickedMediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        return Intent(Intent.ACTION_PICK, mediaUri).apply {
            if (kind == FloatingChatPrototype.PickedMediaKind.Any) {
                setType("*/*")
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            } else {
                setType(type)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolvedMediaKindFor(
        uri: Uri,
        requestedKind: FloatingChatPrototype.PickedMediaKind
    ): FloatingChatPrototype.PickedMediaKind {
        if (requestedKind != FloatingChatPrototype.PickedMediaKind.Any) return requestedKind
        val mimeType = contentResolver.getType(uri).orEmpty()
        return if (mimeType.startsWith("video/")) {
            FloatingChatPrototype.PickedMediaKind.Video
        } else {
            FloatingChatPrototype.PickedMediaKind.Image
        }
    }

    private fun grantReadAccess(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun processPickedMediaInBackground(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        uri: Uri,
        target: FloatingChatMediaTarget
    ) {
        mediaProcessingExecutor.execute {
            val result = runCatching {
                val previewUri = cachePreviewFor(mediaKind, uri) ?: uri
                val playbackUri = cacheOriginalMediaFor(mediaKind, uri) ?: uri
                val mediaMeta = mediaMetadataFor(mediaKind, uri)
                ProcessedPickedMedia(
                    mediaUri = playbackUri,
                    previewUri = previewUri,
                    orientation = mediaMeta.orientation,
                    aspectRatio = mediaMeta.aspectRatio
                )
            }.onFailure { error ->
                Log.w(TAG, "failed to process picked floating chat media", error)
            }.getOrElse {
                ProcessedPickedMedia(
                    mediaUri = uri,
                    previewUri = uri,
                    orientation = if (mediaKind == FloatingChatPrototype.PickedMediaKind.Video) {
                        FloatingChatThumbnailOrientation.Horizontal
                    } else {
                        FloatingChatThumbnailOrientation.Vertical
                    },
                    aspectRatio = null
                )
            }
            mainHandler.post {
                pickerLifecycle.completePickedMediaProcessing {
                    FloatingChatMediaPickerBridge.deliverPickedMedia(
                        mediaKind = mediaKind,
                        mediaUri = result.mediaUri,
                        previewUri = result.previewUri,
                        orientation = result.orientation,
                        aspectRatio = result.aspectRatio,
                        target = target
                    )
                }
            }
        }
    }

    private fun cachePreviewFor(
        kind: FloatingChatPrototype.PickedMediaKind,
        sourceUri: Uri
    ): Uri? {
        val bitmap = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any,
            FloatingChatPrototype.PickedMediaKind.Image -> decodeImagePreview(sourceUri)
            FloatingChatPrototype.PickedMediaKind.Video -> decodeVideoPreview(sourceUri)
        } ?: return null

        val previewDir = File(cacheDir, PREVIEW_CACHE_DIR).apply {
            mkdirs()
        }
        val filePrefix = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any,
            FloatingChatPrototype.PickedMediaKind.Image -> "image"
            FloatingChatPrototype.PickedMediaKind.Video -> "video"
        }
        val previewFile = File(previewDir, "$filePrefix-${System.currentTimeMillis()}.jpg")
        return runCatching {
            previewFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output)
            }
            Uri.fromFile(previewFile)
        }.getOrNull()
    }

    private fun cacheOriginalMediaFor(
        kind: FloatingChatPrototype.PickedMediaKind,
        sourceUri: Uri
    ): Uri? {
        val originalDir = File(cacheDir, ORIGINAL_MEDIA_CACHE_DIR).apply {
            mkdirs()
        }
        val filePrefix = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any,
            FloatingChatPrototype.PickedMediaKind.Image -> "image"
            FloatingChatPrototype.PickedMediaKind.Video -> "video"
        }
        val extension = when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any,
            FloatingChatPrototype.PickedMediaKind.Image -> "jpg"
            FloatingChatPrototype.PickedMediaKind.Video -> "mp4"
        }
        val mediaFile = File(originalDir, "$filePrefix-${System.currentTimeMillis()}.$extension")
        return runCatching {
            contentResolver.openInputStream(sourceUri).use { input ->
                mediaFile.outputStream().use { output ->
                    if (input == null) error("Source media unavailable")
                    input.copyTo(output)
                }
            }
            Uri.fromFile(mediaFile)
        }.getOrNull()
    }

    private fun decodeImagePreview(sourceUri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, sourceUri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width.coerceAtLeast(1)
                    val height = info.size.height.coerceAtLeast(1)
                    val scale = (width.coerceAtLeast(height).toFloat() / PREVIEW_MAX_SIZE_PX)
                        .coerceAtLeast(1f)
                    decoder.setTargetSize(
                        (width / scale).toInt().coerceAtLeast(1),
                        (height / scale).toInt().coerceAtLeast(1)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, sourceUri)
            }
        }.getOrNull()
    }

    private fun decodeVideoPreview(sourceUri: Uri): Bitmap? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(this, sourceUri)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
    }

    private fun mediaMetadataFor(
        kind: FloatingChatPrototype.PickedMediaKind,
        sourceUri: Uri
    ): PickedMediaMeta {
        return when (kind) {
            FloatingChatPrototype.PickedMediaKind.Any,
            FloatingChatPrototype.PickedMediaKind.Image -> imageMetadataFor(sourceUri)
            FloatingChatPrototype.PickedMediaKind.Video -> videoMetadataFor(sourceUri)
        }
    }

    private fun imageMetadataFor(sourceUri: Uri): PickedMediaMeta {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, sourceUri)
                val info = ImageDecoder.decodeDrawable(source)
                val width = info.intrinsicWidth.coerceAtLeast(1)
                val height = info.intrinsicHeight.coerceAtLeast(1)
                pickedMediaMeta(width, height)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, sourceUri)?.let { bitmap ->
                    pickedMediaMeta(bitmap.width, bitmap.height)
                } ?: PickedMediaMeta(FloatingChatThumbnailOrientation.Vertical, null)
            }
        }.getOrElse {
            PickedMediaMeta(FloatingChatThumbnailOrientation.Vertical, null)
        }
    }

    private fun videoMetadataFor(sourceUri: Uri): PickedMediaMeta {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(this, sourceUri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                    ?: 0
                pickedMediaMeta(width, height, rotation)
            }
        }.getOrElse {
            PickedMediaMeta(FloatingChatThumbnailOrientation.Horizontal, null)
        }
    }

    private fun pickedMediaMeta(width: Int, height: Int, rotationDegrees: Int = 0): PickedMediaMeta {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val aspectRatio = pickedMediaAspectRatioFromDimensions(safeWidth, safeHeight, rotationDegrees)
            ?: (safeWidth.toFloat() / safeHeight.toFloat())
        return PickedMediaMeta(
            orientation = if (aspectRatio <= 1f) {
                FloatingChatThumbnailOrientation.Vertical
            } else {
                FloatingChatThumbnailOrientation.Horizontal
            },
            aspectRatio = aspectRatio
        )
    }

    private fun pickedMediaAspectRatioFromDimensions(
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

    private data class PickedMediaMeta(
        val orientation: FloatingChatThumbnailOrientation,
        val aspectRatio: Float?
    )

    private data class ProcessedPickedMedia(
        val mediaUri: Uri,
        val previewUri: Uri,
        val orientation: FloatingChatThumbnailOrientation,
        val aspectRatio: Float?
    )

    companion object {
        private const val TAG = "FloatingChatMediaPicker"
        private const val REQUEST_PICK_MEDIA = 9001
        private const val PREVIEW_CACHE_DIR = "floating-chat-media"
        private const val ORIGINAL_MEDIA_CACHE_DIR = "floating-chat-original-media"
        private const val PREVIEW_MAX_SIZE_PX = 960
        private const val PREVIEW_JPEG_QUALITY = 86
        private val mediaProcessingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
