package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.media.ImageThumbnailContent
import com.paifa.ubikitouch.accessibility.floatingchat.media.StickerImageContent
import com.paifa.ubikitouch.accessibility.floatingchat.media.VideoPreviewContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.StickerMessageCard
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object MediaMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.CapturedPhoto,
        FloatingChatMessageType.StickerGif,
        FloatingChatMessageType.VideoPreview,
        FloatingChatMessageType.ChannelsVideo
    )

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        val message = model.message
        when (model.type) {
            FloatingChatMessageType.VideoPreview -> VideoPreviewContent(
                message,
                environment.onPreviewMedia,
                environment.onLongPressMessage,
                environment.multiSelectMode,
                environment.onToggleSelection,
                environment.onContentBoundsChanged
            )
            FloatingChatMessageType.ChannelsVideo -> VideoPreviewContent(
                message.copy(type = FloatingChatMessageType.VideoPreview),
                environment.onPreviewMedia,
                environment.onLongPressMessage,
                environment.multiSelectMode,
                environment.onToggleSelection,
                environment.onContentBoundsChanged
            )
            FloatingChatMessageType.StickerGif -> if (message.thumbnailUrl != null || message.resourceUrl != null) {
                StickerImageContent(message)
            } else {
                StickerMessageCard(message)
            }
            FloatingChatMessageType.ImageThumbnail,
            FloatingChatMessageType.CapturedPhoto -> ImageThumbnailContent(
                message.copy(type = FloatingChatMessageType.ImageThumbnail),
                environment.onPreviewMedia,
                environment.onOpenMediaActions,
                environment.onLongPressMessage,
                environment.multiSelectMode,
                environment.onToggleSelection,
                environment.onContentBoundsChanged
            )
            else -> renderLegacyMessage(model, environment)
        }
    }
}
