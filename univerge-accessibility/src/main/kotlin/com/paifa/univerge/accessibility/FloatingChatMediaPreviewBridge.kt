package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.univerge.core.model.FloatingChatMessage

object FloatingChatMediaPreviewBridge {
    private var session: FloatingChatMediaPreviewSession? = null

    internal fun open(
        mediaMessages: List<FloatingChatMessage>,
        initialIndex: Int,
        runtimeState: FloatingChatOverlayRuntimeState,
        accountId: String = ""
    ) {
        if (mediaMessages.isEmpty()) return
        runtimeState.openMediaPreview(
            mediaMessages = mediaMessages,
            initialIndex = initialIndex,
            accountId = accountId
        )
    }

    fun currentSession(): FloatingChatMediaPreviewSession? = session

    fun close() {
        session = null
        UniVergeAccessibilityService.instance?.onFloatingChatMediaPreviewClosed()
    }
}

data class FloatingChatMediaPreviewSession(
    val mediaMessages: List<FloatingChatMessage>,
    val initialIndex: Int,
    val accountId: String = ""
)
