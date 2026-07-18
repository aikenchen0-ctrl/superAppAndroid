package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageForwardingOverlayHost(
    forwardMessage: FloatingChatMessage?,
    forwardModeMessages: List<FloatingChatMessage>,
    pendingForwardMessages: List<FloatingChatMessage>,
    pendingForwardMode: MultiForwardMode?,
    forwardTargetConversation: FloatingChatConversation,
    forwardingActions: MessageForwardingActions,
    onForwardMessageChanged: (FloatingChatMessage?) -> Unit,
    onForwardModeMessagesChanged: (List<FloatingChatMessage>) -> Unit,
    onPendingForwardChanged: (List<FloatingChatMessage>, MultiForwardMode?) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    forwardMessage?.let { message ->
        MessageForwardTargetOverlay(
            conversation = forwardTargetConversation,
            onDismiss = { onForwardMessageChanged(null) },
            onTargetSelected = { target ->
                forwardingActions.addForwardedMessage(message, target)
                onForwardMessageChanged(null)
                onShowToast("已提交转发，发送结果以消息状态为准")
            },
            modifier = modifier.fillMaxSize()
        )
    }
    if (forwardModeMessages.isNotEmpty()) {
        MultiForwardModeOverlay(
            selectedCount = forwardModeMessages.size,
            onDismiss = { onForwardModeMessagesChanged(emptyList()) },
            onModeSelected = { mode ->
                onPendingForwardChanged(forwardModeMessages, mode)
                onForwardModeMessagesChanged(emptyList())
            },
            modifier = modifier.fillMaxSize()
        )
    }
    if (pendingForwardMessages.isNotEmpty() && pendingForwardMode != null) {
        MessageForwardTargetOverlay(
            conversation = forwardTargetConversation,
            onDismiss = { onPendingForwardChanged(emptyList(), null) },
            onTargetSelected = { target ->
                if (pendingForwardMode == MultiForwardMode.Combined) {
                    forwardingActions.addCombinedForwardedMessage(pendingForwardMessages, target)
                    onShowToast("已生成合并聊天记录（本地）")
                } else {
                    pendingForwardMessages.forEach { forwarded ->
                        forwardingActions.addForwardedMessage(forwarded, target)
                    }
                    onShowToast("已提交逐条转发，发送结果以消息状态为准")
                }
                onPendingForwardChanged(emptyList(), null)
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
