package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceMotion
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val DefaultSplitBillAmount = "320.00"
private const val SplitBillAnimationDurationMillis = 240

/** SCRM 群成员与账号使用不同 ID 命名空间，需同时比较记录中的原始微信号。 */
internal fun splitBillMemberIsCurrentAccount(
    member: FloatingChatContact,
    account: FloatingChatContact
): Boolean {
    if (member.id == account.id) return true
    val memberWeChatId = member.description.substringAfterLast(" / ").trim()
    val accountWeChatId = account.description.substringAfterLast(" / ").trim()
    return memberWeChatId.isNotBlank() &&
        accountWeChatId.isNotBlank() &&
        memberWeChatId != member.description.trim() &&
        accountWeChatId != account.description.trim() &&
        memberWeChatId == accountWeChatId
}

/** 测试流程：从群聊右侧点击“AA收款”，确认页面实体从底部向上进入。 */
internal fun splitBillEnterOffsetDirection(): Int = FloatingWorkspaceMotion.EnterOffsetDirection

/** 测试流程：点击左上角返回，确认页面实体沿 FloatActivity 统一方向向顶部退出后再关闭。 */
internal fun splitBillExitOffsetDirection(): Int = FloatingWorkspaceMotion.ExitOffsetDirection

/**
 * AA 收款全屏工作区。页面复用现有悬浮根视图，不创建 Dialog、Activity 或额外 Window，避免 BadTokenException。
 *
 * 测试流程：在群聊打开页面，勾选成员并输入金额；空金额提交时应使用 320.00；提交后聊天中出现
 * “群收款 N 人”消息。切换“当前会话”可查看已有 AA 消息，私聊只显示不可发起提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitBillFullScreen(
    isGroupConversation: Boolean,
    groupName: String,
    members: List<FloatingChatContact>,
    existingMessages: List<FloatingChatMessage>,
    onSubmit: (amount: String, members: List<FloatingChatContact>) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    val pageTranslationY = remember { Animatable(0f) }
    val pageAlpha = remember { Animatable(0f) }
    var entered by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var selectedMemberIds by remember(members) {
        mutableStateOf<Set<String>>(members.mapTo(linkedSetOf()) { it.id })
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            pageTranslationY.snapTo(pageHeightPx * splitBillEnterOffsetDirection())
            pageAlpha.snapTo(0f)
            coroutineScope {
                launch {
                    pageTranslationY.animateTo(
                        0f,
                        tween(
                            durationMillis = SplitBillAnimationDurationMillis,
                            easing = LinearOutSlowInEasing
                        )
                    )
                }
                launch {
                    pageAlpha.animateTo(
                        1f,
                        tween(
                            durationMillis = SplitBillAnimationDurationMillis,
                            easing = LinearOutSlowInEasing
                        )
                    )
                }
            }
            entered = true
        }
    }

    fun closeWithExitAnimation() {
        if (exiting) return
        exiting = true
        scope.launch {
            coroutineScope {
                if (pageHeightPx > 0f) {
                    launch {
                        pageTranslationY.animateTo(
                            pageHeightPx * splitBillExitOffsetDirection(),
                            tween(
                                durationMillis = SplitBillAnimationDurationMillis,
                                easing = FastOutLinearInEasing
                            )
                        )
                    }
                }
                launch {
                    pageAlpha.animateTo(
                        0f,
                        tween(
                            durationMillis = SplitBillAnimationDurationMillis,
                            easing = FastOutLinearInEasing
                        )
                    )
                }
            }
            onBack()
        }
    }

    fun submitSplitBill() {
        if (!isGroupConversation) {
            validationMessage = "AA 收款只能在群聊中发起"
            return
        }
        val selectedMembers = members.filter { it.id in selectedMemberIds }
        if (selectedMembers.isEmpty()) {
            validationMessage = "请至少选择 1 个收款成员"
            return
        }
        val finalAmount = amount.trim().ifBlank { DefaultSplitBillAmount }
        val parsedAmount = finalAmount.toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
            validationMessage = "请输入有效的收款总金额"
            return
        }
        onSubmit(finalAmount, selectedMembers)
        closeWithExitAnimation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer {
                translationY = pageTranslationY.value
                this.alpha = pageAlpha.value
            }
    ) {
        FloatingWorkspaceTopAppBar(
            title = "AA收款",
            onBack = ::closeWithExitAnimation
        )
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent
        ) {
            listOf("发起收款", "当前会话").forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                SplitBillComposePage(
                    isGroupConversation = isGroupConversation,
                    groupName = groupName,
                    members = members,
                    selectedMemberIds = selectedMemberIds,
                    amount = amount,
                    validationMessage = validationMessage,
                    onAmountChange = {
                        amount = it.filter { character -> character.isDigit() || character == '.' }
                        validationMessage = null
                    },
                    onToggleAll = {
                        selectedMemberIds = if (selectedMemberIds.size == members.size) {
                            emptySet()
                        } else {
                            members.mapTo(linkedSetOf()) { it.id }
                        }
                        validationMessage = null
                    },
                    onToggleMember = { memberId ->
                        selectedMemberIds = if (memberId in selectedMemberIds) {
                            selectedMemberIds - memberId
                        } else {
                            selectedMemberIds + memberId
                        }
                        validationMessage = null
                    },
                    onSubmit = ::submitSplitBill
                )
            } else {
                SplitBillHistoryPage(existingMessages)
            }
        }
    }
}

@Composable
private fun SplitBillComposePage(
    isGroupConversation: Boolean,
    groupName: String,
    members: List<FloatingChatContact>,
    selectedMemberIds: Set<String>,
    amount: String,
    validationMessage: String?,
    onAmountChange: (String) -> Unit,
    onToggleAll: () -> Unit,
    onToggleMember: (String) -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isGroupConversation) groupName else "当前为私聊会话",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = isGroupConversation,
                label = { Text("收款总金额") },
                placeholder = { Text("可不填，默认 ¥$DefaultSplitBillAmount") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        if (!isGroupConversation) {
            item {
                Text(
                    text = "AA 收款只能在群聊中发起",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else if (members.isEmpty()) {
            item {
                Text(
                    text = "当前群聊没有可收款成员",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            item {
                ListItem(
                    headlineContent = { Text(if (selectedMemberIds.size == members.size) "取消全选" else "全选") },
                    supportingContent = { Text("已选择 ${selectedMemberIds.size} 人") },
                    leadingContent = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    trailingContent = {
                        Checkbox(
                            checked = selectedMemberIds.size == members.size,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onToggleAll)
                )
                HorizontalDivider()
            }
            items(members, key = { it.id }) { member ->
                val selected = member.id in selectedMemberIds
                ListItem(
                    headlineContent = { Text(member.name, fontWeight = FontWeight.Normal) },
                    supportingContent = { Text(member.description.ifBlank { "群成员" }) },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.initials.take(2), fontWeight = FontWeight.Normal)
                            }
                        }
                    },
                    trailingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                    modifier = Modifier.clickable { onToggleMember(member.id) }
                )
            }
        }
        validationMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = isGroupConversation && members.isNotEmpty() && selectedMemberIds.isNotEmpty()
            ) {
                Text("发起收款", fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SplitBillHistoryPage(messages: List<FloatingChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "当前会话的群收款",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "当前会话暂无 AA 收款",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        } else {
            items(messages, key = { it.id }) { message ->
                ListItem(
                    headlineContent = { Text(message.text, fontWeight = FontWeight.Normal) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            message.detail?.lineSequence()?.take(3)?.forEach { line ->
                                Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
