package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.InlineImageThumbnailContent
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rootBoundsFromPosition
import com.paifa.ubikitouch.accessibility.floatingchat.message.fixedThumbnailHeightDp
import com.paifa.ubikitouch.accessibility.mediaWatermarkText
import com.paifa.ubikitouch.accessibility.standaloneMediaListMaxHeightDp
import com.paifa.ubikitouch.accessibility.standaloneMediaListMaxWidthDp
import com.paifa.ubikitouch.accessibility.standaloneMediaListMinHeightDp
import com.paifa.ubikitouch.accessibility.standaloneMediaListUsesAspectFit
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation

@Composable
internal fun ImageThumbnailContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    if (message.presentation == FloatingChatMessagePresentation.MediaStandalone) {
        StandaloneMediaMessageContent(
            message = message,
            onPreviewMedia = onPreviewMedia,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            onToggleSelection = onToggleSelection,
            onContentBoundsChanged = onContentBoundsChanged
        )
    } else {
        InlineImageThumbnailContent(message)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StandaloneMediaMessageContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)?
) {
    val mediaClickSource = remember { MutableInteractionSource() }
    var currentBounds by remember(message.id) { mutableStateOf<Rect?>(null) }
    val mediaFrame = standaloneMediaListFrameSize(
        orientation = message.thumbnailOrientation,
        mediaAspectRatio = message.mediaAspectRatio
    )
    Column(
        modifier = Modifier.width(mediaFrame.width),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(mediaFrame.width)
                .height(mediaFrame.height)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.standaloneMediaBorder, RoundedCornerShape(7.dp))
                .onGloballyPositioned { coordinates ->
                    val bounds = rootBoundsFromPosition(
                        positionInRoot = coordinates.positionInRoot(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                    currentBounds = bounds
                    onContentBoundsChanged?.invoke(bounds)
                }
                .combinedClickable(
                    interactionSource = mediaClickSource,
                    indication = null,
                    onClick = {
                        if (multiSelectMode) {
                            onToggleSelection()
                        } else {
                            onPreviewMedia(message)
                        }
                    },
                    onLongClick = { onLongPressMessage(message, currentBounds) }
                )
        ) {
            MediaThumbnailSurface(
                message = message,
                modifier = Modifier.fillMaxSize(),
                showChrome = false,
                useAspectFit = standaloneMediaListUsesAspectFit()
            )
        }
    }
}

@Composable
internal fun VideoPreviewContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    if (message.presentation == FloatingChatMessagePresentation.MediaStandalone) {
        StandaloneMediaMessageContent(
            message = message,
            onPreviewMedia = onPreviewMedia,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            onToggleSelection = onToggleSelection,
            onContentBoundsChanged = onContentBoundsChanged
        )
        return
    }

    val mediaBitmap = rememberAsyncMediaThumbnailBitmap(message)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedThumbnailHeightDp(message.thumbnailOrientation).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.videoBase)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
        ) {
            if (mediaBitmap != null) {
                Image(
                    bitmap = mediaBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PlaceholderVideoCanvas(modifier = Modifier.fillMaxSize())
            }
            VideoPlayGlyph(modifier = Modifier.fillMaxSize())
            TextLabel(
                text = message.text,
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 7.dp, end = 8.dp)
            )
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
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            )
        }
    }
}

private data class MediaListFrame(
    val width: Dp,
    val height: Dp
)

private fun standaloneMediaListFrameSize(
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float?
): MediaListFrame {
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    val maxWidth = standaloneMediaListMaxWidthDp().toFloat()
    var width = maxWidth
    var height = width / aspectRatio
    if (height > standaloneMediaListMaxHeightDp()) {
        height = standaloneMediaListMaxHeightDp().toFloat()
        width = height * aspectRatio
    }
    if (height < standaloneMediaListMinHeightDp()) {
        height = standaloneMediaListMinHeightDp().toFloat()
        width = height * aspectRatio
    }
    width = width.coerceIn(84f, maxWidth)
    return MediaListFrame(width = width.dp, height = height.dp)
}
