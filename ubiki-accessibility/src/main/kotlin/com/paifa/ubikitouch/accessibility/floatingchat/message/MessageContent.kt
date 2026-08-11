package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.DraftBadge
import com.paifa.ubikitouch.accessibility.floatingchat.media.FilePreviewContent
import com.paifa.ubikitouch.accessibility.floatingchat.media.ImageThumbnailContent
import com.paifa.ubikitouch.accessibility.floatingchat.media.VideoPreviewContent
import com.paifa.ubikitouch.accessibility.floatingchat.media.VoiceMessageContent
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

@Composable
internal fun MessageContent(
    message: FloatingChatMessage,
    index: Int,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    claimed: Boolean = false,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    val unavailableState = messageUnavailableStateFor(message)
    Column(verticalArrangement = Arrangement.spacedBy(if (isSystem) 0.dp else 7.dp)) {
        if (unavailableState != null) {
            UnavailableMessageContent(unavailableState)
        } else {
            when (messageRendererGroupFor(message.type)) {
                MessageRendererGroup.Text -> if (message.type == FloatingChatMessageType.Text) {
                    SimpleTextMessageContent(message = message, index = index)
                } else {
                    MixedTextMessageContent(message)
                }
                MessageRendererGroup.OversizedText -> EmojiMessageCard(message)
                MessageRendererGroup.Media -> when (message.type) {
                    FloatingChatMessageType.VideoPreview -> VideoPreviewContent(message, onPreviewMedia, onLongPressMessage, multiSelectMode, onToggleSelection, onContentBoundsChanged)
                    FloatingChatMessageType.StickerGif -> if (message.thumbnailUrl != null || message.resourceUrl != null) {
                        // GIF payloads use the same thumbnail pipeline as images when a remote media URL exists.
                        ImageThumbnailContent(message.copy(type = FloatingChatMessageType.ImageThumbnail), onPreviewMedia, onOpenMediaActions, onLongPressMessage, multiSelectMode, onToggleSelection, onContentBoundsChanged)
                    } else {
                        StickerMessageCard(message)
                    }
                    else -> ImageThumbnailContent(message.copy(type = FloatingChatMessageType.ImageThumbnail), onPreviewMedia, onOpenMediaActions, onLongPressMessage, multiSelectMode, onToggleSelection, onContentBoundsChanged)
                }
                MessageRendererGroup.ChannelVideo -> VideoPreviewContent(message.copy(type = FloatingChatMessageType.VideoPreview), onPreviewMedia, onLongPressMessage, multiSelectMode, onToggleSelection, onContentBoundsChanged)
                MessageRendererGroup.Voice -> VoiceMessageContent(message)
                MessageRendererGroup.Document -> FilePreviewContent(message)
                MessageRendererGroup.Location -> if (message.type == FloatingChatMessageType.InlineLocation) InlineLocationContent(message) else LocationMessageContent(message)
                MessageRendererGroup.Profile -> when (message.type) {
                    FloatingChatMessageType.InlineContact -> InlineContactContent(message)
                    else -> ContactLinkCardContent(message.copy(type = FloatingChatMessageType.ContactLink))
                }
                MessageRendererGroup.Link -> if (message.type == FloatingChatMessageType.MiniProgramLink) MiniProgramLinkContent(message, claimed) else LinkMessageCard(message)
                MessageRendererGroup.Stacked -> if (message.type == FloatingChatMessageType.ChatHistory) ChatHistoryMessageContent(message) else StackedMessageCard(message)
                MessageRendererGroup.Money -> MoneyMessageCard(message)
                MessageRendererGroup.Call -> CallMessageCard(message)
                MessageRendererGroup.Notice -> NoticeMessageCard(message)
                MessageRendererGroup.Quote -> QuoteMessageContent(message)
            }
        }
        if (message.kind == FloatingChatMessageKind.AiDraft && !isSystem) {
            DraftBadge()
        }
    }
}
