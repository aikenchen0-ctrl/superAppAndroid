package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.accessibility.floatingchat.contract.PaymentUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.PaymentUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactSummary

private val PanelBackground = Color(0xFFF7F7F7)
private val PrimaryText = Color(0xFF222222)
private val SecondaryText = Color(0xFF888888)

@Composable
internal fun PaymentPanel(
    state: PaymentUiState,
    onEvent: (PaymentUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by remember(state.title) { mutableStateOf(state.amount) }
    var note by remember(state.title) { mutableStateOf(state.note.ifBlank { state.defaultNote }) }
    var recipientId by remember(state.title, state.recipients) { mutableStateOf(state.selectedRecipientId) }
    Column(modifier = modifier.fillMaxWidth().background(PanelBackground).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextLabel(state.title, 13.sp, color = PrimaryText, maxLines = 1)
        ToolInput(amount, state.amountLabel) { amount = it; onEvent(PaymentUiEvent.AmountChanged(it)) }
        ToolInput(note, state.noteLabel) { note = it; onEvent(PaymentUiEvent.NoteChanged(it)) }
        if (state.recipients.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recipients.take(6).forEach { recipient ->
                    TextLabel(
                        recipient.displayName,
                        11.sp,
                        color = if (recipient.id == recipientId) Color(0xFF1AAD19) else SecondaryText,
                        modifier = Modifier.clickable { recipientId = recipient.id; onEvent(PaymentUiEvent.RecipientSelected(recipient.id)) },
                        maxLines = 1
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextLabel(state.confirmLabel, 12.sp, color = Color(0xFF49679E), modifier = Modifier.clickable { onEvent(PaymentUiEvent.ConfirmRequested(amount.trim(), note.trim(), recipientId)) }, maxLines = 1)
        }
    }
}

@Composable
private fun ToolInput(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isBlank()) TextLabel(placeholder, 12.sp, color = SecondaryText, maxLines = 1)
            inner()
        }
    )
}

@Composable
internal fun PaymentComposerPanel(
    title: String,
    amountLabel: String,
    noteLabel: String,
    defaultNote: String,
    confirmLabel: String,
    recipients: List<FloatingChatContact> = emptyList(),
    onConfirm: (String, String, FloatingChatContact?) -> Unit
) {
    PaymentPanel(
        state = PaymentUiState(
            title = title,
            amountLabel = amountLabel,
            noteLabel = noteLabel,
            defaultNote = defaultNote,
            confirmLabel = confirmLabel,
            recipients = recipients.map { ContactSummary(it.id, it.name) }
        ),
        onEvent = { event ->
            if (event is PaymentUiEvent.ConfirmRequested) {
                onConfirm(event.amount, event.note, recipients.firstOrNull { it.id == event.recipientId })
            }
        }
    )
}
