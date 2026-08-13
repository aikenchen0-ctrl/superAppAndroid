package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.core.model.FloatingChatMessage

/** Existing interaction callbacks kept outside the immutable render model. */
internal data class MessageRenderEnvironment(
    val onPreviewMedia: (FloatingChatMessage) -> Unit,
    val onOpenMediaActions: (FloatingChatMessage) -> Unit,
    val onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    val multiSelectMode: Boolean,
    val onToggleSelection: () -> Unit,
    val claimed: Boolean = false,
    val onContentBoundsChanged: ((Rect) -> Unit)? = null
)
