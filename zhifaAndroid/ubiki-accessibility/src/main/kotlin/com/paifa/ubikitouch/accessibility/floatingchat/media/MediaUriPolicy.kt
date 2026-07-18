package com.paifa.ubikitouch.accessibility.floatingchat.media

import java.net.URL

internal fun isLocalContentUri(uriText: String?): Boolean {
    return uriText?.startsWith("content://") == true
}

internal fun isLocalMediaUri(uriText: String?): Boolean {
    return isLocalContentUri(uriText) || uriText?.startsWith("file://") == true
}

internal fun isRemoteImageUri(uriText: String?): Boolean {
    return uriText?.startsWith("https://") == true || uriText?.startsWith("http://") == true
}

internal fun normalizedRemoteImageUri(uriText: String?): String? {
    val raw = uriText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!raw.startsWith("http://", ignoreCase = true)) return raw
    val host = runCatching { URL(raw).host.lowercase() }.getOrNull() ?: return raw
    return if (host in WeChatAvatarHttpsHosts) {
        raw.replaceFirst("http://", "https://", ignoreCase = true)
    } else {
        raw
    }
}

internal fun avatarImageDecodeMaxSizePx(): Int = AVATAR_IMAGE_DECODE_MAX_SIZE_PX

internal fun avatarImageLoadsUseDedicatedSmallDecodeSize(): Boolean {
    return AVATAR_IMAGE_DECODE_MAX_SIZE_PX < REAL_MEDIA_DECODE_MAX_SIZE_PX
}

internal fun remoteAvatarMaxConcurrentLoads(): Int = REMOTE_IMAGE_MAX_CONCURRENT_LOADS

internal fun remoteImageFailureRetryDelayMillis(): Int = REMOTE_IMAGE_FAILURE_RETRY_DELAY_MS

internal fun remoteImageRetrySuppressedByRecentFailure(
    lastFailureUptimeMillis: Long?,
    nowUptimeMillis: Long,
    retryDelayMillis: Long
): Boolean {
    val lastFailure = lastFailureUptimeMillis ?: return false
    return nowUptimeMillis - lastFailure in 0 until retryDelayMillis
}

internal fun imageDecodeSampleSize(width: Int, height: Int, maxSize: Int): Int {
    if (width <= 0 || height <= 0 || maxSize <= 0) return 1
    val largestDimension = maxOf(width, height)
    var sampleSize = 1
    while (largestDimension / sampleSize > maxSize && sampleSize <= Int.MAX_VALUE / 2) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun remoteImageConnectTimeoutMillis(): Int = REMOTE_IMAGE_CONNECT_TIMEOUT_MS

internal fun remoteImageReadTimeoutMillis(): Int = REMOTE_IMAGE_READ_TIMEOUT_MS

internal fun remoteImageMaxBytes(): Int = REMOTE_IMAGE_MAX_BYTES

private val WeChatAvatarHttpsHosts = setOf(
    "mmbiz.qpic.cn",
    "wx.qlogo.cn",
    "thirdwx.qlogo.cn"
)

private const val REAL_MEDIA_DECODE_MAX_SIZE_PX = 720
private const val AVATAR_IMAGE_DECODE_MAX_SIZE_PX = 96
private const val REMOTE_IMAGE_CONNECT_TIMEOUT_MS = 2_500
private const val REMOTE_IMAGE_READ_TIMEOUT_MS = 3_500
private const val REMOTE_IMAGE_MAX_BYTES = 2 * 1024 * 1024
private const val REMOTE_IMAGE_MAX_CONCURRENT_LOADS = 2
private const val REMOTE_IMAGE_FAILURE_RETRY_DELAY_MS = 10 * 60 * 1000
