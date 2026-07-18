package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageInteractionOverlayHost(
    paymentDetailMessage: FloatingChatMessage?,
    longPressMessage: FloatingChatMessage?,
    longPressAnchorBounds: Rect?,
    multiSelectMode: Boolean,
    chatHistoryPreviewMessage: FloatingChatMessage?,
    selectedThread: ChatThreadSelection,
    selectedAccount: FloatingChatContact,
    selectedMessages: () -> List<FloatingChatMessage>,
    messageLongPressActions: MessageLongPressActions,
    onPaymentDetailMessageChanged: (FloatingChatMessage?) -> Unit,
    isPaymentClaimed: (FloatingChatMessage) -> Boolean,
    onClaimPayment: (FloatingChatMessage) -> Unit,
    onLongPressMessageChanged: (FloatingChatMessage?) -> Unit,
    onStartForwardingMessages: (List<FloatingChatMessage>) -> Unit,
    onClearSelectedMessages: () -> Unit,
    onMultiSelectModeChanged: (Boolean) -> Unit,
    onChatHistoryPreviewMessageChanged: (FloatingChatMessage?) -> Unit,
    modifier: Modifier = Modifier,
    multiSelectBarModifier: Modifier = Modifier
) {
    paymentDetailMessage?.let { message ->
        PaymentDetailOverlay(
            message = message,
            selectedThread = selectedThread,
            selectedAccount = selectedAccount,
            claimed = isPaymentClaimed(message),
            onClaim = { onClaimPayment(message) },
            onDismiss = { onPaymentDetailMessageChanged(null) },
            modifier = modifier.fillMaxSize()
        )
    }
    longPressMessage?.let { message ->
        MessageLongPressMenuOverlay(
            message = message,
            messageBounds = longPressAnchorBounds,
            onDismiss = { onLongPressMessageChanged(null) },
            onAction = { action -> messageLongPressActions.performLongPressAction(message, action) },
            modifier = modifier.fillMaxSize()
        )
    }
    if (multiSelectMode) {
        MultiSelectActionBar(
            selectedCount = selectedMessages().size,
            onForward = {
                onStartForwardingMessages(selectedMessages())
                onClearSelectedMessages()
                onMultiSelectModeChanged(false)
            },
            onFavorite = { messageLongPressActions.favoriteSelectedMessages(selectedMessages()) },
            onDelete = { messageLongPressActions.deleteSelectedMessages(selectedMessages()) },
            onCancel = {
                onClearSelectedMessages()
                onMultiSelectModeChanged(false)
            },
            modifier = multiSelectBarModifier
        )
    }
    chatHistoryPreviewMessage?.let { message ->
        ChatHistoryDetailOverlay(
            message = message,
            onDismiss = { onChatHistoryPreviewMessageChanged(null) },
            modifier = modifier.fillMaxSize()
        )
    }
}
