package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.accessibility.floatingchat.aivoice.MessageAsideAnalysisState
import com.paifa.univerge.accessibility.scrm.PaymentReadback
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatMessage

@Composable
internal fun MessageRevokeConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw inside the attached accessibility overlay root. A platform Dialog would need
        // an Activity window token and can throw WindowManager.BadTokenException here.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "确定撤销",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "撤销后，此消息将从当前会话中撤回。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = "确定",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageInteractionOverlayHost(
    paymentDetailMessage: FloatingChatMessage?,
    longPressMessage: FloatingChatMessage?,
    revokeConfirmationMessage: FloatingChatMessage?,
    longPressAnchorBounds: Rect?,
    asideAnalysisState: MessageAsideAnalysisState?,
    textZoomMessage: FloatingChatMessage?,
    multiSelectMode: Boolean,
    chatHistoryPreviewMessage: FloatingChatMessage?,
    selectedThread: ChatThreadSelection,
    selectedAccount: FloatingChatContact,
    selectedMessages: () -> List<FloatingChatMessage>,
    messageLongPressActions: MessageLongPressActions,
    onPaymentDetailMessageChanged: (FloatingChatMessage?) -> Unit,
    isPaymentClaimed: (FloatingChatMessage) -> Boolean,
    paymentReadback: PaymentReadback,
    onRefreshPaymentStatus: (FloatingChatMessage) -> Unit,
    onClaimPayment: (FloatingChatMessage) -> Unit,
    onLongPressMessageChanged: (FloatingChatMessage?) -> Unit,
    onRevokeConfirmationDismissed: () -> Unit,
    onRevokeConfirmed: (FloatingChatMessage) -> Unit,
    onAsideAnalysisDismissed: () -> Unit,
    onTextZoomDismissed: () -> Unit,
    onStartForwardingMessages: (List<FloatingChatMessage>) -> Unit,
    onStartCombinedForwardingMessages: (List<FloatingChatMessage>) -> Unit,
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
            readback = paymentReadback,
            onClaim = { onClaimPayment(message) },
            onRefresh = { onRefreshPaymentStatus(message) },
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
    revokeConfirmationMessage?.let { message ->
        MessageRevokeConfirmationDialog(
            onDismiss = onRevokeConfirmationDismissed,
            onConfirm = { onRevokeConfirmed(message) },
            modifier = modifier.fillMaxSize()
        )
    }
    asideAnalysisState?.let { state ->
        MessageAsideAnalysisOverlay(
            state = state,
            messageBounds = longPressAnchorBounds,
            onDismiss = onAsideAnalysisDismissed,
            modifier = modifier.fillMaxSize()
        )
    }
    textZoomMessage?.let { message ->
        MessageTextZoomOverlay(
            message = message,
            onDismiss = onTextZoomDismissed,
            modifier = modifier.fillMaxSize()
        )
    }
    if (multiSelectMode) {
        MultiSelectActionBar(
            onForward = {
                onStartForwardingMessages(selectedMessages())
                onClearSelectedMessages()
                onMultiSelectModeChanged(false)
            },
            onCombinedForward = {
                onStartCombinedForwardingMessages(selectedMessages())
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
