package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.launch

internal data class RedPacketDraft(
    val amount: String,
    val greeting: String,
    val packetCount: Int,
    val paymentPassword: String
)

private const val DefaultRedPacketCountLimit = 100

/**
 * 对应 iOS RedPacketComposerViewController 的 Android Material 3 全屏工作区。
 *
 * UI 仅复用既有 accessibility 悬浮根视图，不创建 Dialog 或 Window，避免 BadTokenException。
 * 测试流程：在聊天右侧点击“红包”，填写金额、祝福语和个数；切到“支付确认”查询零钱，
 * 输入 6 位支付密码后提交，确认设备任务完成且聊天中回显红包消息；点击左上返回时页面自上向下退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedPacketFullScreen(
    conversationLabel: String,
    maxPacketCount: Int = DefaultRedPacketCountLimit,
    onBack: () -> Unit,
    onQueryWallet: suspend () -> String,
    onSubmit: suspend (RedPacketDraft) -> String
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }
    var amount by remember { mutableStateOf("66.00") }
    var greeting by remember { mutableStateOf("恭喜发财，大吉大利") }
    var packetCount by remember { mutableStateOf(1) }
    var paymentPassword by remember { mutableStateOf("") }
    var walletStatus by remember { mutableStateOf("零钱未查询") }
    var walletLoading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var isClosing by remember { mutableStateOf(false) }
    val packetCountLimit = maxPacketCount.coerceIn(1, DefaultRedPacketCountLimit)

    fun close() {
        if (isClosing) return
        isClosing = true
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = "红包",
            onBack = ::close
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            listOf("填写红包", "支付确认").forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                RedPacketDraftPage(
                    conversationLabel = conversationLabel,
                    amount = amount,
                    onAmountChange = { amount = it },
                    greeting = greeting,
                    onGreetingChange = { greeting = it },
                    packetCount = packetCount,
                    packetCountLimit = packetCountLimit,
                    onPacketCountChange = { packetCount = it },
                    onContinue = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            } else {
                RedPacketConfirmationPage(
                    amount = amount,
                    greeting = greeting,
                    packetCount = packetCount,
                    paymentPassword = paymentPassword,
                    onPaymentPasswordChange = { paymentPassword = it },
                    walletStatus = walletStatus,
                    walletLoading = walletLoading,
                    submitting = submitting,
                    status = status,
                    onQueryWallet = {
                        if (!walletLoading) {
                            scope.launch {
                                walletLoading = true
                                walletStatus = runCatching { onQueryWallet() }
                                    .getOrElse { "零钱查询失败：${it.message ?: "未知错误"}" }
                                walletLoading = false
                            }
                        }
                    },
                    onSubmit = {
                        if (!submitting) {
                            if (paymentPassword.length != 6) {
                                status = "请输入 6 位支付密码"
                            } else {
                                scope.launch {
                                    submitting = true
                                    status = runCatching {
                                        onSubmit(RedPacketDraft(amount.trim(), greeting.trim(), packetCount, paymentPassword))
                                    }.getOrElse { "红包提交失败：${it.message ?: "未知错误"}" }
                                    submitting = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RedPacketDraftPage(
    conversationLabel: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    greeting: String,
    onGreetingChange: (String) -> Unit,
    packetCount: Int,
    packetCountLimit: Int,
    onPacketCountChange: (Int) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "发给${conversationLabel.ifBlank { "当前会话" }}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { onAmountChange(it.filter { character -> character.isDigit() || character == '.' }) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("红包金额") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        item {
            OutlinedTextField(
                value = greeting,
                onValueChange = onGreetingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("祝福语") },
                minLines = 2
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("红包个数", fontWeight = FontWeight.Normal)
                        Text(
                            text = "$packetCount/$packetCountLimit",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = { onPacketCountChange(packetCount - 1) },
                            enabled = packetCount > 1
                        ) { Icon(Icons.Filled.Remove, contentDescription = "减少红包个数") }
                        FilledTonalIconButton(
                            onClick = { onPacketCountChange(packetCount + 1) },
                            enabled = packetCount < packetCountLimit
                        ) { Icon(Icons.Filled.Add, contentDescription = "增加红包个数") }
                    }
                }
            }
        }
        item {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("进入支付确认", fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun RedPacketConfirmationPage(
    amount: String,
    greeting: String,
    packetCount: Int,
    paymentPassword: String,
    onPaymentPasswordChange: (String) -> Unit,
    walletStatus: String,
    walletLoading: Boolean,
    submitting: Boolean,
    status: String?,
    onQueryWallet: () -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "确认红包金额",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("¥${amount.ifBlank { "0.00" }}", style = MaterialTheme.typography.headlineMedium)
                    Text("$packetCount 个红包", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(greeting.ifBlank { "恭喜发财，大吉大利" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Button(
                onClick = onQueryWallet,
                modifier = Modifier.fillMaxWidth(),
                enabled = !walletLoading && !submitting
            ) {
                if (walletLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Wallet, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (walletLoading) "正在查询零钱" else "查询零钱", fontWeight = FontWeight.Normal)
            }
        }
        item {
            Text(
                text = walletStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            OutlinedTextField(
                value = paymentPassword,
                onValueChange = { onPaymentPasswordChange(it.filter(Char::isDigit).take(6)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("支付密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }
        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting
            ) {
                if (submitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text(if (submitting) "正在提交" else "塞钱进红包", fontWeight = FontWeight.Normal)
            }
        }
        status?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
