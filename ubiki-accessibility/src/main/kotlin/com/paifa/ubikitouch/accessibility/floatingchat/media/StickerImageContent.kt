package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatMessage

/** 表情包只显示原图，固定宽度并按真实位图比例计算高度。 */
@Composable
internal fun StickerImageContent(message: FloatingChatMessage) {
    val mediaBitmap = rememberAsyncStickerThumbnailBitmap(message)
    if (mediaBitmap == null) {
        Text(
            text = message.detail?.takeIf { it.isNotBlank() } ?: message.text,
            modifier = Modifier
                .width(100.dp)
                .padding(4.dp),
            fontSize = 9.sp
        )
        return
    }
    val stickerAspectRatio = if (
        mediaBitmap.width > 0 && mediaBitmap.height > 0
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
        Image(
            bitmap = mediaBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
