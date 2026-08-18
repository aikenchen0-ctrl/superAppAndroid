package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.accessibility.floatingchat.contract.PaymentUiEvent
import com.paifa.univerge.accessibility.floatingchat.contract.PaymentUiState
import com.paifa.univerge.accessibility.floatingchat.contract.ContactSummary
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmPaymentApi
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.PaymentRequestFactory
import com.paifa.univerge.accessibility.scrm.PaymentTaskRunner
import com.paifa.univerge.accessibility.scrm.PaymentTaskState
import com.paifa.univerge.accessibility.scrm.WalletBalanceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var packetCount by remember(state.title) { mutableStateOf(1) }
    Column(modifier = modifier.fillMaxWidth().background(PanelBackground).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextLabel(state.title, 13.sp, color = PrimaryText, maxLines = 1)
        ToolInput(amount, state.amountLabel) { amount = it; onEvent(PaymentUiEvent.AmountChanged(it)) }
        ToolInput(note, state.noteLabel) { note = it; onEvent(PaymentUiEvent.NoteChanged(it)) }
        if (state.title == "发红包") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextLabel("红包个数 $packetCount/100", 12.sp, color = SecondaryText, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextLabel("−", 18.sp, color = if (packetCount > 1) Color(0xFF1AAD19) else SecondaryText, modifier = Modifier.clickable(enabled = packetCount > 1) { packetCount -= 1 }, maxLines = 1)
                    TextLabel("+", 18.sp, color = if (packetCount < 100) Color(0xFF1AAD19) else SecondaryText, modifier = Modifier.clickable(enabled = packetCount < 100) { packetCount += 1 }, maxLines = 1)
                }
            }
        }
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
            TextLabel(
                state.confirmLabel,
                12.sp,
                color = Color(0xFF49679E),
                modifier = Modifier.clickable {
                    onEvent(PaymentUiEvent.ConfirmRequested(amount.trim(), note.trim(), recipientId, packetCount))
                },
                maxLines = 1
            )
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
    scrmRoute: ScrmFloatingAccountRoute? = null,
    operationStatus: String? = null,
    operationInProgress: Boolean = false,
    onConfirm: (String, String, FloatingChatContact?, String, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by remember(title) { mutableStateOf(false) }
    var draftAmount by remember(title) { mutableStateOf("") }
    var draftNote by remember(title) { mutableStateOf(defaultNote) }
    var draftRecipient by remember(title) { mutableStateOf<FloatingChatContact?>(null) }
    var draftPacketCount by remember(title) { mutableStateOf(1) }
    var walletStatus by remember(title, scrmRoute) { mutableStateOf("零钱 未查询") }
    var walletLoading by remember(title, scrmRoute) { mutableStateOf(false) }
    if (confirming) {
        PaymentConfirmationPanel(
            title = title,
            amount = draftAmount,
            note = draftNote,
            recipient = draftRecipient,
            walletStatus = walletStatus,
            walletLoading = walletLoading,
            operationStatus = operationStatus,
            operationInProgress = operationInProgress,
            onQueryWallet = {
                val route = scrmRoute
                if (route == null) {
                    walletStatus = "未选择可用 SCRM 账号"
                } else {
                    walletLoading = true
                    walletStatus = "零钱 查询中"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val session = ScrmSettingsManager(context.applicationContext)
                                    .loadSelectedSessionOrBootstrap()
                                val api = session.readApi as? ScrmPaymentApi
                                    ?: error("当前 SCRM 客户端不支持零钱查询")
                                PaymentTaskRunner(session.taskApi).submitAndAwait {
                                    api.getWalletBalance(PaymentRequestFactory.walletBalance(route))
                                }
                            }
                        }.onSuccess { outcome ->
                            val balance = outcome.data?.let(WalletBalanceParser::parse)
                            walletStatus = balance?.fen?.let { "零钱 ${balance.displayText}" }
                                ?: when (outcome.state) {
                                    PaymentTaskState.PROCESSING -> "零钱任务处理中 · #${outcome.taskId}"
                                    PaymentTaskState.FAILED -> "零钱查询失败：${outcome.message}"
                                    PaymentTaskState.UNKNOWN -> "零钱状态待人工核对 · #${outcome.taskId}"
                                    PaymentTaskState.SUCCESS -> "零钱结果未包含余额 · #${outcome.taskId}"
                                }
                        }.onFailure { error ->
                            walletStatus = "零钱查询失败：${error.message ?: "未知错误"}"
                        }
                        walletLoading = false
                    }
                }
            },
            onBack = { confirming = false },
            onConfirm = { paymentPassword ->
                onConfirm(draftAmount, draftNote, draftRecipient, paymentPassword, draftPacketCount)
            }
        )
        return
    }
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
                draftAmount = event.amount
                draftNote = event.note
                draftRecipient = recipients.firstOrNull { it.id == event.recipientId }
                draftPacketCount = event.packetCount
                confirming = event.amount.isNotBlank()
            }
        }
    )
}

/** iOS confirmation-page equivalent. It only confirms the local request draft. */
@Composable
private fun PaymentConfirmationPanel(
    title: String,
    amount: String,
    note: String,
    recipient: FloatingChatContact?,
    walletStatus: String,
    walletLoading: Boolean,
    operationStatus: String?,
    operationInProgress: Boolean,
    onQueryWallet: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().background(PanelBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextLabel("确认$title", 16.sp, color = PrimaryText, maxLines = 1)
        TextLabel("金额  ¥${amount.ifBlank { "0.00" }}", 22.sp, color = PrimaryText, maxLines = 1)
        TextLabel("备注  ${note.ifBlank { "无" }}", 12.sp, color = SecondaryText, maxLines = 2)
        recipient?.let { TextLabel("收款人  ${it.name}", 12.sp, color = SecondaryText, maxLines = 1) }
        TextLabel(
            walletStatus,
            12.sp,
            color = SecondaryText,
            modifier = Modifier.clickable(enabled = !walletLoading, onClick = onQueryWallet),
            maxLines = 1
        )
        operationStatus?.takeIf { it.isNotBlank() }?.let { status ->
            TextLabel(
                status,
                12.sp,
                color = if (operationInProgress) Color(0xFF49679E) else SecondaryText,
                maxLines = 3
            )
        }
        BasicTextField(
            value = password,
            onValueChange = { password = it.filter(Char::isDigit).take(6) },
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { inner ->
                TextLabel("支付密码  ${"●".repeat(password.length)}${"○".repeat(6 - password.length)}", 13.sp, color = SecondaryText, maxLines = 1)
                inner()
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextLabel("返回", 12.sp, color = SecondaryText, modifier = Modifier.clickable(onClick = onBack), maxLines = 1)
            TextLabel(
                if (operationInProgress) "任务处理中..." else "确认并提交",
                12.sp,
                color = if (password.length == 6 && !operationInProgress) Color(0xFF1AAD19) else SecondaryText,
                modifier = Modifier.padding(start = 18.dp).clickable(
                    enabled = password.length == 6 && !operationInProgress,
                    onClick = { onConfirm(password) }
                ),
                maxLines = 1
            )
        }
    }
}
