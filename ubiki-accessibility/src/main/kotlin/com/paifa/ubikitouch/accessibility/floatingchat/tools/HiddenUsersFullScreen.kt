package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlinx.coroutines.launch

private const val HiddenUsersAnimationDurationMillis = 260

/** 隐藏用户的高性能分页，分别查看已隐藏用户和管理参与者显示状态。 */
internal enum class HiddenUsersFullScreenTab(val label: String) {
    Hidden("已隐藏"),
    Manage("管理")
}

/**
 * iOS“隐藏用户”对应的 Android Material 3 全屏悬浮页。
 *
 * iOS 此功能只维护浮窗本地参与者显示状态，未接入 OpenAPI；Android 保持相同边界，
 * 通过回调更新会话轨道的本地隐藏集合，不伪造服务端接口。
 * 测试流程：从右侧“隐藏用户”进入，检查 30dp 安全区、两个分页和返回动画；
 * 在“管理”页隐藏用户，确认其从左侧会话轨道消失；在“已隐藏”页恢复后确认重新显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenUsersFullScreen(
    participants: List<FloatingChatContact>,
    groupIds: Set<String>,
    hiddenParticipantIds: Set<String>,
    onHide: (FloatingChatContact) -> Unit,
    onRestore: (FloatingChatContact) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { HiddenUsersFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }
    val uniqueParticipants = remember(participants) { participants.distinctBy { it.id } }
    val hiddenParticipants = remember(uniqueParticipants, hiddenParticipantIds) {
        uniqueParticipants.filter { participant -> participant.id in hiddenParticipantIds }
    }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            translationY.snapTo(pageHeightPx)
            translationY.animateTo(0f, tween(HiddenUsersAnimationDurationMillis))
            entered = true
        }
    }
    fun close() = scope.launch {
        translationY.animateTo(pageHeightPx, tween(HiddenUsersAnimationDurationMillis))
        onBack()
    }

    // 复用已有 accessibility overlay 根视图，不创建 Dialog 或新 Window，规避 BadTokenException。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { this.translationY = translationY.value }
    ) {
        Spacer(Modifier.height(30.dp))
        TopAppBar(
            title = { Text("隐藏用户", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal) },
            navigationIcon = {
                IconButton(onClick = ::close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(HiddenUsersFullScreenTab.Hidden.ordinal) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "查看已隐藏用户")
                }
            }
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            HiddenUsersFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (HiddenUsersFullScreenTab.entries[page]) {
                HiddenUsersFullScreenTab.Hidden -> HiddenUserListPage(
                    title = "已隐藏用户 (${hiddenParticipants.size})",
                    users = hiddenParticipants,
                    groupIds = groupIds,
                    actionLabel = "恢复显示",
                    onAction = onRestore,
                    emptyMessage = "暂无隐藏用户"
                )
                HiddenUsersFullScreenTab.Manage -> HiddenUserListPage(
                    title = "参与者 (${uniqueParticipants.size})",
                    users = uniqueParticipants,
                    groupIds = groupIds,
                    actionLabel = "隐藏",
                    onAction = onHide,
                    hiddenParticipantIds = hiddenParticipantIds,
                    emptyMessage = "暂无可管理用户"
                )
            }
        }
    }
}

@Composable
private fun HiddenUserListPage(
    title: String,
    users: List<FloatingChatContact>,
    groupIds: Set<String>,
    actionLabel: String,
    onAction: (FloatingChatContact) -> Unit,
    hiddenParticipantIds: Set<String> = emptySet(),
    emptyMessage: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { HiddenUsersSectionTitle(title) }
        if (users.isEmpty()) {
            item {
                Text(
                    emptyMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            items(users, key = { user -> user.id }) { user ->
                val isHidden = user.id in hiddenParticipantIds
                HiddenUserRow(
                    user = user,
                    kind = if (user.id in groupIds) "群聊" else "好友",
                    actionLabel = if (actionLabel == "隐藏" && isHidden) "已隐藏" else actionLabel,
                    enabled = actionLabel != "隐藏" || !isHidden,
                    onAction = { onAction(user) }
                )
            }
        }
    }
}

@Composable
private fun HiddenUserRow(
    user: FloatingChatContact,
    kind: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Surface(
                    modifier = Modifier.size(42.dp),
                    color = Color(user.avatarColor),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        user.initials.ifBlank { user.name.take(1) },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 10.dp, start = 12.dp)
                    )
                }
            },
            headlineContent = { Text(user.name, fontWeight = FontWeight.Normal) },
            supportingContent = {
                Text("$kind · ${user.description.ifBlank { "暂无备注" }}")
            },
            trailingContent = {
                FilledTonalButton(onClick = onAction, enabled = enabled) {
                    Text(actionLabel, fontWeight = FontWeight.Normal)
                }
            }
        )
    }
}

@Composable
private fun HiddenUsersSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}
