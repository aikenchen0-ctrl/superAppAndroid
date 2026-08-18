package com.paifa.univerge.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.univerge.accessibility.floatingchat.media.FilePreviewContent
import com.paifa.univerge.accessibility.floatingchat.media.ImageThumbnailContent
import com.paifa.univerge.accessibility.floatingchat.media.StickerImageContent
import com.paifa.univerge.accessibility.floatingchat.media.VideoPreviewContent
import com.paifa.univerge.accessibility.floatingchat.media.VoiceMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.CallMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.ChatHistoryMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.ContactLinkCardContent
import com.paifa.univerge.accessibility.floatingchat.message.EmojiMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.InlineContactContent
import com.paifa.univerge.accessibility.floatingchat.message.InlineLocationContent
import com.paifa.univerge.accessibility.floatingchat.message.LinkMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.LocationMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.MiniProgramLinkContent
import com.paifa.univerge.accessibility.floatingchat.message.MixedTextMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.MoneyMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.NoticeMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.OfficialArticleMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.QuoteMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.SimpleTextMessageContent
import com.paifa.univerge.accessibility.floatingchat.message.StackedMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.StickerMessageCard
import com.paifa.univerge.accessibility.floatingchat.message.messageRendererGroupFor
import com.paifa.univerge.accessibility.floatingchat.message.MessageRendererGroup
import com.paifa.univerge.core.model.FloatingChatMessageType

/** Exact pre-registry rendering path for unregistered future message types. */
internal object LegacyMessageRenderer : MessageRenderer {
    override val supportedTypes: Set<FloatingChatMessageType> = emptySet()

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        renderLegacyMessage(model, environment)
    }
}

@Composable
internal fun renderLegacyMessage(
    model: MessageRenderModel,
    environment: MessageRenderEnvironment
) {
    val message = model.message
    when (messageRendererGroupFor(model.type)) {
        MessageRendererGroup.Text -> if (model.type == FloatingChatMessageType.Text) {
            SimpleTextMessageContent(message = message, index = model.index)
        } else {
            MixedTextMessageContent(message)
        }
        MessageRendererGroup.OversizedText -> EmojiMessageCard(message)
        MessageRendererGroup.Media -> when (model.type) {
            FloatingChatMessageType.VideoPreview -> VideoPreviewContent(
                message,
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
            else -> ImageThumbnailContent(
                message.copy(type = FloatingChatMessageType.ImageThumbnail),
                environment.onPreviewMedia,
                environment.onOpenMediaActions,
                environment.onLongPressMessage,
                environment.multiSelectMode,
                environment.onToggleSelection,
                environment.onContentBoundsChanged
            )
        }
        MessageRendererGroup.ChannelVideo -> VideoPreviewContent(
            message.copy(type = FloatingChatMessageType.VideoPreview),
            environment.onPreviewMedia,
            environment.onLongPressMessage,
            environment.multiSelectMode,
            environment.onToggleSelection,
            environment.onContentBoundsChanged
        )
        MessageRendererGroup.Voice -> VoiceMessageContent(message)
        MessageRendererGroup.Document -> FilePreviewContent(message)
        MessageRendererGroup.Location -> if (model.type == FloatingChatMessageType.InlineLocation) {
            InlineLocationContent(message)
        } else {
            LocationMessageContent(message)
        }
        MessageRendererGroup.Profile -> when (model.type) {
            FloatingChatMessageType.InlineContact -> InlineContactContent(message)
            else -> ContactLinkCardContent(message.copy(type = FloatingChatMessageType.ContactLink))
        }
        MessageRendererGroup.Link -> when (model.type) {
            FloatingChatMessageType.MiniProgramLink -> MiniProgramLinkContent(message, environment.claimed)
            FloatingChatMessageType.Article -> OfficialArticleMessageCard(message)
            else -> LinkMessageCard(message)
        }
        MessageRendererGroup.Stacked -> if (model.type == FloatingChatMessageType.ChatHistory) {
            ChatHistoryMessageContent(message)
        } else {
            StackedMessageCard(message)
        }
        MessageRendererGroup.Money -> MoneyMessageCard(message)
        MessageRendererGroup.Call -> CallMessageCard(message)
        MessageRendererGroup.Notice -> NoticeMessageCard(message)
        MessageRendererGroup.Quote -> QuoteMessageContent(message)
    }
}
