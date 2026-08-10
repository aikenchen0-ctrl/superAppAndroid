package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation

internal enum class MessageHorizontalPlacement {
    Start,
    Center,
    End
}

internal fun messageHorizontalPlacement(
    presentation: FloatingChatMessagePresentation,
    fromMe: Boolean
): MessageHorizontalPlacement {
    return when (presentation) {
        FloatingChatMessagePresentation.System -> MessageHorizontalPlacement.Center
        FloatingChatMessagePresentation.Bubble,
        FloatingChatMessagePresentation.SpecialCard,
        FloatingChatMessagePresentation.MediaStandalone -> if (fromMe) {
            MessageHorizontalPlacement.End
        } else {
            MessageHorizontalPlacement.Start
        }
    }
}

internal fun fixedThumbnailHeightDp(orientation: FloatingChatThumbnailOrientation?): Int {
    return when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 120
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 56
    }
}

internal fun messageUsesBubbleChrome(presentation: FloatingChatMessagePresentation): Boolean {
    return presentation != FloatingChatMessagePresentation.MediaStandalone &&
        presentation != FloatingChatMessagePresentation.System
}

internal fun scrmSendStatusTextFor(message: FloatingChatMessage): String? {
    if (!message.fromMe) return null
    return when (message.sendState) {
        FloatingChatSendState.LocalOnly -> null
        FloatingChatSendState.Queued -> "待提交"
        FloatingChatSendState.Uploading -> "上传中"
        FloatingChatSendState.Submitted -> "已提交"
        FloatingChatSendState.Processing -> "处理中"
        FloatingChatSendState.Succeeded -> "已发送"
        FloatingChatSendState.FailedRetryable -> message.failureStatusText("发送失败，稍后重试")
        FloatingChatSendState.FailedFinal -> message.failureStatusText("发送失败")
        FloatingChatSendState.Unknown -> "结果待确认"
        FloatingChatSendState.Cancelled -> "已取消"
    }
}

private fun FloatingChatMessage.failureStatusText(prefix: String): String {
    val detail = sendErrorMessage
        ?.takeIf { it.isNotBlank() }
        ?.take(24)
    return if (detail == null) prefix else "$prefix：$detail"
}
