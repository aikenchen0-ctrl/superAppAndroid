package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.DraftBadge
import com.paifa.ubikitouch.accessibility.FilePreviewContent
import com.paifa.ubikitouch.accessibility.ImageThumbnailContent
import com.paifa.ubikitouch.accessibility.VideoPreviewContent
import com.paifa.ubikitouch.accessibility.VoiceMessageContent
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
    Column(verticalArrangement = Arrangement.spacedBy(if (isSystem) 0.dp else 7.dp)) {
        when (message.type) {
            FloatingChatMessageType.Location -> LocationMessageContent(message)
            FloatingChatMessageType.ContactLink -> ContactLinkCardContent(message)
            FloatingChatMessageType.MiniProgramLink -> MiniProgramLinkContent(message, claimed)
            FloatingChatMessageType.Text -> SimpleTextMessageContent(message = message, index = index)
            FloatingChatMessageType.MixedText -> MixedTextMessageContent(message)
            FloatingChatMessageType.Quote -> QuoteMessageContent(message)
            FloatingChatMessageType.ChatHistory -> ChatHistoryMessageContent(message)
            FloatingChatMessageType.FilePreview -> FilePreviewContent(message = message)
            FloatingChatMessageType.ImageThumbnail -> ImageThumbnailContent(
                message = message,
                onPreviewMedia = onPreviewMedia,
                onOpenMediaActions = onOpenMediaActions,
                onLongPressMessage = onLongPressMessage,
                multiSelectMode = multiSelectMode,
                onToggleSelection = onToggleSelection,
                onContentBoundsChanged = onContentBoundsChanged
            )
            FloatingChatMessageType.VideoPreview -> VideoPreviewContent(
                message = message,
                onPreviewMedia = onPreviewMedia,
                onLongPressMessage = onLongPressMessage,
                multiSelectMode = multiSelectMode,
                onToggleSelection = onToggleSelection,
                onContentBoundsChanged = onContentBoundsChanged
            )
            FloatingChatMessageType.Voice -> VoiceMessageContent(message)
            FloatingChatMessageType.InlineContact -> InlineContactContent(message)
            FloatingChatMessageType.InlineLocation -> InlineLocationContent(message)
        }
        if (message.kind == FloatingChatMessageKind.AiDraft && !isSystem) {
            DraftBadge()
        }
    }
}
