package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.paifa.ubikitouch.accessibility.WeightedLruCache
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAsyncImageThumbnailBitmap(message: FloatingChatMessage): Bitmap? {
    val context = LocalContext.current
    return rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = message.thumbnailUrl
    )
}

@Composable
fun rememberAsyncImageThumbnailBitmap(
    context: Context,
    uriText: String?,
    maxSizePx: Int = REAL_MEDIA_DECODE_MAX_SIZE_PX,
    cacheNamespace: String = IMAGE_THUMBNAIL_CACHE_NAMESPACE
): Bitmap? {
    val cacheKey = imageThumbnailCacheKey(uriText, cacheNamespace)
    return produceState(
        initialValue = cacheKey?.let(SharedBitmapMemoryCache::get),
        context,
        uriText,
        maxSizePx,
        cacheNamespace
    ) {
        if (value != null || cacheKey == null) return@produceState
        val inFlightKey = "$cacheKey@$maxSizePx"
        val deferred = inFlightImageLoads[inFlightKey]
            ?.takeIf { it.isActive }
            ?: async(Dispatchers.IO) {
                loadImageThumbnailBitmap(
                    context = context.applicationContext,
                    uriText = uriText,
                    maxSizePx = maxSizePx,
                    cacheNamespace = cacheNamespace
                )
            }.let { candidate ->
                inFlightImageLoads.putIfAbsent(inFlightKey, candidate)?.also {
                    candidate.cancel()
                } ?: candidate
            }
        try {
            value = deferred.await()
        } finally {
            inFlightImageLoads.remove(inFlightKey, deferred)
        }
    }.value
}

@Composable
internal fun rememberAsyncMediaThumbnailBitmap(message: FloatingChatMessage): Bitmap? {
    val context = LocalContext.current
    return produceState(
        initialValue = cachedMediaThumbnailBitmap(message),
        context,
        message.type,
        message.resourceUrl,
        message.thumbnailUrl
    ) {
        value = withContext(Dispatchers.IO) {
            when (message.type) {
                FloatingChatMessageType.VideoPreview -> loadVideoPreviewBitmap(
                    context = context.applicationContext,
                    thumbnailUriText = message.thumbnailUrl,
                    resourceUriText = message.resourceUrl
                )
                else -> loadImageThumbnailBitmap(
                    context = context.applicationContext,
                    uriText = message.thumbnailUrl
                )
            }
        }
    }.value
}

internal fun loadImageThumbnailBitmap(
    context: Context,
    uriText: String?,
    maxSizePx: Int = REAL_MEDIA_DECODE_MAX_SIZE_PX,
    cacheNamespace: String = IMAGE_THUMBNAIL_CACHE_NAMESPACE
): Bitmap? {
    val cacheKey = imageThumbnailCacheKey(uriText, cacheNamespace) ?: return null
    val resolvedUriText = normalizedRemoteImageUri(uriText) ?: return null
    val remoteImage = isRemoteImageUri(resolvedUriText)
    if (remoteImage) {
        val uri = Uri.parse(resolvedUriText)
        Log.i(
            AVATAR_DIAGNOSTICS_TAG,
            "load remote image scheme=${uri.scheme.orEmpty()} host=${uri.host.orEmpty()} length=${resolvedUriText.length}"
        )
    }
    if (remoteImage && remoteImageRetrySuppressed(cacheKey)) return null
    return SharedBitmapMemoryCache.getOrPut(cacheKey) {
        val uri = Uri.parse(resolvedUriText)
        if (remoteImage) {
            loadPersistentRemoteImageBitmap(
                context = context,
                cacheKey = cacheKey,
                maxSizePx = maxSizePx
            )?.let { cached ->
                Log.i(AVATAR_DIAGNOSTICS_TAG, "persistent avatar cache hit host=${uri.host.orEmpty()}")
                return@getOrPut cached
            }
        }
        val bitmap = runCatching {
            when {
                remoteImage -> decodeRemoteImageBitmap(resolvedUriText, maxSizePx)
                uri.scheme == "file" -> decodeFileBitmapRespectingExif(
                    path = uri.path,
                    maxSizePx = maxSizePx
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val width = info.size.width.coerceAtLeast(1)
                        val height = info.size.height.coerceAtLeast(1)
                        val scale = (width.coerceAtLeast(height).toFloat() / maxSizePx.coerceAtLeast(1))
                            .coerceAtLeast(1f)
                        decoder.setTargetSize(
                            (width / scale).toInt().coerceAtLeast(1),
                            (height / scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }
        }.onFailure { error ->
            if (remoteImage) {
                Log.w(LOG_TAG, "failed to load remote image thumbnail host=${uri.host.orEmpty()}", error)
                Log.w(
                    AVATAR_DIAGNOSTICS_TAG,
                    "remote image failed host=${uri.host.orEmpty()} error=${error::class.java.simpleName} message=${error.message.orEmpty().take(160)}"
                )
            }
        }.getOrNull()
        if (remoteImage) {
            if (bitmap == null) {
                FailedRemoteImageLoads[cacheKey] = System.currentTimeMillis()
            } else {
                FailedRemoteImageLoads.remove(cacheKey)
                persistRemoteImageBitmap(context, cacheKey, bitmap)
            }
        }
        bitmap
    }
}

private fun persistentRemoteImageFile(context: Context, cacheKey: String): File {
    // externalFilesDir maps to Android/data/<package>/files and is removed with the app.
    val directory = context.getExternalFilesDir("floating-chat-avatars")
        ?: File(context.filesDir, "floating-chat-avatars")
    return File(directory, "${cacheFileDigest(cacheKey)}.png")
}

private fun cacheFileDigest(value: String): String {
    return value.toByteArray(Charsets.UTF_8)
        .fold(0x811c9dc5.toInt()) { hash, byte -> (hash xor byte.toInt()) * 0x01000193 }
        .toUInt()
        .toString(16)
}

private fun loadPersistentRemoteImageBitmap(
    context: Context,
    cacheKey: String,
    maxSizePx: Int
): Bitmap? {
    val file = persistentRemoteImageFile(context, cacheKey)
    if (!file.isFile || file.length() <= 0L) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = imageDecodeSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxSize = maxSizePx
            )
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    )
}

private fun persistRemoteImageBitmap(context: Context, cacheKey: String, bitmap: Bitmap) {
    val file = persistentRemoteImageFile(context, cacheKey)
    runCatching {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
        }
    }.onFailure { error ->
        Log.w(AVATAR_DIAGNOSTICS_TAG, "failed to persist avatar cache error=${error::class.java.simpleName}")
    }
}

private fun decodeRemoteImageBitmap(uriText: String, maxSizePx: Int): Bitmap? {
    return RemoteImageLoadSemaphore.withPermit {
        val connection = URL(uriText).openConnection().apply {
            connectTimeout = remoteImageConnectTimeoutMillis()
            readTimeout = remoteImageReadTimeoutMillis()
            useCaches = true
        }
        if (connection is HttpURLConnection) {
            Log.i(
                AVATAR_DIAGNOSTICS_TAG,
                "remote image response status=${connection.responseCode} contentType=${connection.contentType.orEmpty()} contentLength=${connection.contentLengthLong}"
            )
        }
        connection.getInputStream().use { input ->
            val bytes = input.readAtMostBytes(remoteImageMaxBytes()) ?: return@withPermit null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = imageDecodeSampleSize(
                        width = bounds.outWidth,
                        height = bounds.outHeight,
                        maxSize = maxSizePx
                    )
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inDither = true
                }
            )
        }
    }
}

private inline fun <T> Semaphore.withPermit(block: () -> T): T {
    acquire()
    return try {
        block()
    } finally {
        release()
    }
}

private fun imageThumbnailCacheKey(
    uriText: String?,
    cacheNamespace: String = IMAGE_THUMBNAIL_CACHE_NAMESPACE
): String? {
    return normalizedRemoteImageUri(uriText)
        ?.takeIf { uri -> isLocalMediaUri(uri) || isRemoteImageUri(uri) }
        ?.let { uri -> "$cacheNamespace:$uri" }
}

private fun videoThumbnailCacheKey(uriText: String?): String? {
    return uriText
        ?.takeIf(::isLocalMediaUri)
        ?.let { uri -> "video:$uri" }
}

private fun cachedImageThumbnailBitmap(
    uriText: String?,
    cacheNamespace: String = IMAGE_THUMBNAIL_CACHE_NAMESPACE
): Bitmap? {
    return imageThumbnailCacheKey(uriText, cacheNamespace)?.let(SharedBitmapMemoryCache::get)
}

private fun cachedMediaThumbnailBitmap(message: FloatingChatMessage): Bitmap? {
    return if (message.type == FloatingChatMessageType.VideoPreview) {
        cachedImageThumbnailBitmap(message.thumbnailUrl)
            ?: videoThumbnailCacheKey(message.resourceUrl ?: message.thumbnailUrl)
                ?.let(SharedBitmapMemoryCache::get)
    } else {
        cachedImageThumbnailBitmap(message.thumbnailUrl)
    }
}

private val SharedBitmapMemoryCache = WeightedLruCache<String, Bitmap>(
    maxWeight = sharedBitmapMemoryCacheBytes(Runtime.getRuntime().maxMemory()),
    weightOf = { bitmap -> bitmap.allocationByteCount }
)

private val FailedRemoteImageLoads = ConcurrentHashMap<String, Long>()

/** 同一帧内多个头像引用同一资源时共享一次 IO/解码任务。 */
private val inFlightImageLoads = ConcurrentHashMap<String, Deferred<Bitmap?>>()

private val RemoteImageLoadSemaphore = Semaphore(remoteAvatarMaxConcurrentLoads(), true)

private fun sharedBitmapMemoryCacheBytes(runtimeMaxMemoryBytes: Long): Int {
    val targetBytes = (runtimeMaxMemoryBytes / 16L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return targetBytes.coerceIn(MIN_BITMAP_MEMORY_CACHE_BYTES, MAX_BITMAP_MEMORY_CACHE_BYTES)
}

private fun remoteImageRetrySuppressed(cacheKey: String): Boolean {
    return remoteImageRetrySuppressedByRecentFailure(
        lastFailureUptimeMillis = FailedRemoteImageLoads[cacheKey],
        nowUptimeMillis = System.currentTimeMillis(),
        retryDelayMillis = remoteImageFailureRetryDelayMillis().toLong()
    )
}

private fun InputStream.readAtMostBytes(maxBytes: Int): ByteArray? {
    val safeMaxBytes = maxBytes.coerceAtLeast(1)
    val buffer = ByteArray(DEFAULT_REMOTE_IMAGE_BUFFER_BYTES)
    val output = ByteArrayOutputStream()
    var totalBytes = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        totalBytes += read
        if (totalBytes > safeMaxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun decodeFileBitmapRespectingExif(
    path: String?,
    maxSizePx: Int = REAL_MEDIA_DECODE_MAX_SIZE_PX
): Bitmap? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = imageDecodeSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxSize = maxSizePx
            )
        }
    ) ?: return null
    val rotationDegrees = runCatching {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (rotationDegrees == 0f) return bitmap
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(rotationDegrees) },
        true
    )
}

internal fun loadVideoPreviewBitmap(
    context: Context,
    thumbnailUriText: String?,
    resourceUriText: String?
): Bitmap? {
    return loadImageThumbnailBitmap(context, thumbnailUriText)
        ?: loadVideoThumbnailBitmap(context, resourceUriText ?: thumbnailUriText)
}

private fun loadVideoThumbnailBitmap(
    context: Context,
    uriText: String?
): Bitmap? {
    val cacheKey = videoThumbnailCacheKey(uriText) ?: return null
    return SharedBitmapMemoryCache.getOrPut(cacheKey) {
        val uri = Uri.parse(uriText)
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
    }
}

private const val LOG_TAG = "FloatingChatOverlay"
private const val AVATAR_DIAGNOSTICS_TAG = "UbikiAvatar"
private const val REAL_MEDIA_DECODE_MAX_SIZE_PX = 720
private const val IMAGE_THUMBNAIL_CACHE_NAMESPACE = "image"
private const val DEFAULT_REMOTE_IMAGE_BUFFER_BYTES = 8 * 1024
private const val MIN_BITMAP_MEMORY_CACHE_BYTES = 8 * 1024 * 1024
private const val MAX_BITMAP_MEMORY_CACHE_BYTES = 32 * 1024 * 1024
