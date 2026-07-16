package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal object VideoPreviewMetadataResolver {
    fun aspectRatio(context: Context, message: FloatingChatMessage): Float? {
        val rawUri = message.resourceUrl ?: message.thumbnailUrl ?: return null
        if (!rawUri.startsWith("content://") && !rawUri.startsWith("file://")) return null
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, Uri.parse(rawUri))
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (width == null || height == null || width <= 0 || height <= 0) {
                    null
                } else {
                    val rotated = rotation == 90 || rotation == 270
                    if (rotated) height.toFloat() / width else width.toFloat() / height
                }
            }
        }.getOrNull()
    }
}
