package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatMessage

/** 表情包只显示原图，固定宽度并按真实位图比例计算高度。 */
@Composable
internal fun StickerImageContent(message: FloatingChatMessage) {
    val mediaBitmap = rememberAsyncStickerThumbnailBitmap(message)
    val stickerAspectRatio = if (
        mediaBitmap != null && mediaBitmap.width > 0 && mediaBitmap.height > 0
    ) {
        mediaBitmap.width.toFloat() / mediaBitmap.height.toFloat()
    } else {
        message.mediaAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: 1f
    }

    Box(
        modifier = Modifier
            .width(100.dp)
            .aspectRatio(stickerAspectRatio)
    ) {
        if (mediaBitmap != null) {
            Image(
                bitmap = mediaBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
