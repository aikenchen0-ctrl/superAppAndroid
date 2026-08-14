package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlinx.coroutines.launch

internal const val TransferStatusBarHeightDp = 30

/** 测试流程：从聊天工具栏打开转账，确认全屏实体从底部向上进入。 */
internal fun transferEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上角返回，确认全屏实体向顶部下方退出后才关闭。 */
internal fun transferExitOffsetDirection(): Int = 1

internal enum class TransferFullScreenTab(val label: String) {
    Compose("转账"),
    Records("记录")
}

/**
 * 转账全屏工作区：使用 M3 顶栏、Tab/Pager 和 LazyColumn，提交仍回调现有 SCRM 支付接口。
 * 测试流程：选择收款人、填写金额/备注/支付密码，点击提交，观察任务状态；切换“记录”查看最近一次状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferFullScreen(
    recipients: List<FloatingChatContact>,
    operationStatus: String?,
    operationInProgress: Boolean,
    onSubmit: (String, String, FloatingChatContact?, String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TransferFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    val pageTranslationY = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }
    var selectedRecipient by remember(recipients) { mutableStateOf(recipients.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !exiting) {
            pageTranslationY.snapTo(pageHeightPx * transferEnterOffsetDirection())
            pageTranslationY.animateTo(0f, tween(260))
        }
    }

    fun closeWithExitAnimation() {
        if (exiting) return
        exiting = true
        scope.launch {
            pageTranslationY.animateTo(pageHeightPx * transferExitOffsetDirection(), tween(220))
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { translationY = pageTranslationY.value }
    ) {
        Spacer(Modifier.height(TransferStatusBarHeightDp.dp))
        TopAppBar(
            title = {
                Text(
                    text = "转账",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
            },
            navigationIcon = {
                IconButton(onClick = ::closeWithExitAnimation) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            TransferFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (TransferFullScreenTab.entries[page]) {
                TransferFullScreenTab.Compose -> TransferComposePage(
                    recipients = recipients,
                    selectedRecipient = selectedRecipient,
                    onRecipientSelected = { selectedRecipient = it },
                    amount = amount,
                    onAmountChanged = { amount = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    note = note,
                    onNoteChanged = { note = it.take(100) },
                    password = password,
                    onPasswordChanged = { password = it.filter(Char::isDigit).take(6) },
                    operationStatus = operationStatus,
                    operationInProgress = operationInProgress,
                    onSubmit = { onSubmit(amount.trim(), note.trim(), selectedRecipient, password) }
                )
                TransferFullScreenTab.Records -> TransferRecordsPage(
                    operationStatus = operationStatus,
                    selectedRecipient = selectedRecipient,
                    amount = amount
                )
            }
        }
    }
}

@Composable
private fun TransferComposePage(
    recipients: List<FloatingChatContact>,
    selectedRecipient: FloatingChatContact?,
    onRecipientSelected: (FloatingChatContact) -> Unit,
    amount: String,
    onAmountChanged: (String) -> Unit,
    note: String,
    onNoteChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    operationStatus: String?,
    operationInProgress: Boolean,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("收款人", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(8.dp))
            if (recipients.isEmpty()) {
                Text("当前会话没有可用收款人", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(recipients, key = { it.id }) { recipient ->
            Card(
                onClick = { onRecipientSelected(recipient) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (recipient.id == selectedRecipient?.id) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(recipient.name, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Normal)
                }
            }
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("支付密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }
        item {
            Button(
                onClick = onSubmit,
                enabled = selectedRecipient != null && amount.isNotBlank() && password.length == 6 && !operationInProgress,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (operationInProgress) "提交中…" else "确认转账") }
        }
        operationStatus?.takeIf(String::isNotBlank)?.let { status ->
            item {
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun TransferRecordsPage(
    operationStatus: String?,
    selectedRecipient: FloatingChatContact?,
    amount: String
) {
    val status = operationStatus?.takeIf(String::isNotBlank)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (status == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("暂无转账记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("转账任务", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                            Text("收款人：${selectedRecipient?.name ?: "未选择"}", fontWeight = FontWeight.Normal)
                            Text("金额：${amount.ifBlank { "未填写" }}", fontWeight = FontWeight.Normal)
                            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
