package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation

@Composable
internal fun ImageThumbnailSurface(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier,
    mediaBitmap: Bitmap? = rememberAsyncImageThumbnailBitmap(message)
) {
    MediaThumbnailSurface(
        message = message,
        modifier = modifier,
        mediaBitmap = mediaBitmap
    )
}

@Composable
internal fun MediaThumbnailSurface(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier,
    mediaBitmap: Bitmap? = rememberAsyncMediaThumbnailBitmap(message),
    showChrome: Boolean = true,
    useAspectFit: Boolean = false
) {
    Box(modifier = modifier) {
        if (mediaBitmap != null) {
            Image(
                bitmap = mediaBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (useAspectFit) ContentScale.Fit else ContentScale.Crop
            )
        } else {
            PlaceholderImageCanvas(
                orientation = message.thumbnailOrientation,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showChrome && message.thumbnailOrientation == FloatingChatThumbnailOrientation.Vertical) {
            VerticalImageCropOverlay(modifier = Modifier.fillMaxSize())
        }
        if (message.type == FloatingChatMessageType.VideoPreview) {
            VideoPlayGlyph(modifier = Modifier.fillMaxSize())
        }
        if (showChrome) {
            TextLabel(
                text = mediaWatermarkText(
                    resourceUrl = message.resourceUrl,
                    thumbnailUrl = message.thumbnailUrl
                ),
                size = 8.sp,
                color = OverlayTokens.imageWatermark,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 86.dp, bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun PlaceholderImageCanvas(
    orientation: FloatingChatThumbnailOrientation?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = OverlayTokens.imageBlock,
            topLeft = Offset(size.width * 0.25f, size.height * 0.12f),
            size = Size(size.width * 0.68f, size.height * 0.76f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawCircle(
            color = OverlayTokens.imageHighlight,
            radius = size.minDimension * 0.15f,
            center = Offset(size.width * 0.74f, size.height * 0.30f)
        )
        if (orientation == FloatingChatThumbnailOrientation.Vertical) {
            drawImageFade(heightFraction = 0.18f)
        }
    }
}

@Composable
internal fun PlaceholderVideoCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = OverlayTokens.videoFrame,
            topLeft = Offset(size.width * 0.05f, size.height * 0.14f),
            size = Size(size.width * 0.90f, size.height * 0.72f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }
}

@Composable
private fun VerticalImageCropOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawImageFade(heightFraction = 0.15f)
    }
}

private fun DrawScope.drawImageFade(heightFraction: Float) {
    drawRect(
        color = OverlayTokens.imageFade,
        topLeft = Offset.Zero,
        size = Size(size.width, size.height * heightFraction)
    )
    drawRect(
        color = OverlayTokens.imageFade,
        topLeft = Offset(0f, size.height * (1f - heightFraction)),
        size = Size(size.width, size.height * heightFraction)
    )
}

@Composable
internal fun VideoPlayGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = OverlayTokens.primaryText
        )
    }
}
