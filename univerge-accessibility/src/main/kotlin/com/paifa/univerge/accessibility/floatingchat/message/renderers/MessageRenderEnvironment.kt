package com.paifa.univerge.accessibility.floatingchat.message.renderers

import androidx.compose.ui.geometry.Rect
import com.paifa.univerge.core.model.FloatingChatMessage

/** Existing interaction callbacks kept outside the immutable render model. */
internal data class MessageRenderEnvironment(
    val onPreviewMedia: (FloatingChatMessage) -> Unit,
    val onOpenMediaActions: (FloatingChatMessage) -> Unit,
    val onMessageClick: (FloatingChatMessage) -> Unit,
    val onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    val multiSelectMode: Boolean,
    val onToggleSelection: () -> Unit,
    val claimed: Boolean = false,
    val onContentBoundsChanged: ((Rect) -> Unit)? = null
)
