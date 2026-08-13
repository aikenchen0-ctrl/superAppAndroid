package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.DraftBadge
import com.paifa.ubikitouch.accessibility.floatingchat.message.renderers.MessageRenderEnvironment
import com.paifa.ubikitouch.accessibility.floatingchat.message.renderers.MessageRenderModel
import com.paifa.ubikitouch.accessibility.floatingchat.message.renderers.MessageRenderState
import com.paifa.ubikitouch.accessibility.floatingchat.message.renderers.MessageRendererRegistry
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

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
    val model = MessageRenderModel.from(message = message, index = index)
    val state = MessageRenderState(selected = multiSelectMode)
    val environment = MessageRenderEnvironment(
        onPreviewMedia = onPreviewMedia,
        onOpenMediaActions = onOpenMediaActions,
        onLongPressMessage = onLongPressMessage,
        multiSelectMode = multiSelectMode,
        onToggleSelection = onToggleSelection,
        claimed = claimed,
        onContentBoundsChanged = onContentBoundsChanged
    )
    val registry = MessageRendererRegistry.default()
    Column(verticalArrangement = Arrangement.spacedBy(if (isSystem) 0.dp else 7.dp)) {
        if (unavailableState != null) {
            UnavailableMessageContent(unavailableState)
        } else {
            registry.resolve(model.type).render(model, state, environment)
        }
        if (message.kind == FloatingChatMessageKind.AiDraft && !isSystem) {
            DraftBadge()
        }
    }
}
