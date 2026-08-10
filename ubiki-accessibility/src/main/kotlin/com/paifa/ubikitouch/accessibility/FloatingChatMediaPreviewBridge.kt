package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.core.model.FloatingChatMessage

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
        UbikiAccessibilityService.instance?.onFloatingChatMediaPreviewClosed()
    }
}

data class FloatingChatMediaPreviewSession(
    val mediaMessages: List<FloatingChatMessage>,
    val initialIndex: Int,
    val accountId: String = ""
)
