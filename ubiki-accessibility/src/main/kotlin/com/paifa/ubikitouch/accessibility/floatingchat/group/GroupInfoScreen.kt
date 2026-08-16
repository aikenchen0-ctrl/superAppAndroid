package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoMemberUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoUiState

/**
 * iOS 群信息的 Android 全屏悬浮实现。
 *
 * 测试流程：从群聊右侧工具进入，检查 30dp 顶部安全区、三个分页及返回动画；依次刷新群资料、
 * 编辑资料、邀请成员和切换群设置，确认接口返回状态显示在页面顶部。进出场由聊天根的
 * `AnimatedVisibility` 统一执行，禁止页面自行创建第二段位移动画。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupInfoScreen(
    state: GroupInfoUiState,
    onEvent: (GroupInfoUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { GroupInfoFullScreenTab.entries.size })
    var requestedPage by remember { mutableStateOf<Int?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(requestedPage) {
        requestedPage?.let { page ->
            pagerState.animateScrollToPage(page)
            requestedPage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            // UI：右侧群信息复用 UI组件 的全屏工具栏；状态区由 toolbar 内嵌 padding 承载。
            // 测试流程：点击群信息后确认自下向上进入，点击左上返回后确认页面向顶部退出。
            FloatingWorkspaceTopAppBar(
                title = "群信息",
                onBack = { onEvent(GroupInfoUiEvent.BackRequested) },
                actions = {
                    IconButton(
                        onClick = { onEvent(GroupInfoUiEvent.RefreshRequested) },
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新群资料")
                    }
                }
            )
            TabRow(selectedTabIndex = pagerState.currentPage) {
                GroupInfoFullScreenTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { requestedPage = index },
                        text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                    )
                }
            }
            GroupInfoStatus(state)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                when (GroupInfoFullScreenTab.entries[page]) {
                    GroupInfoFullScreenTab.Profile -> GroupInfoProfilePage(state, onEvent)
                    GroupInfoFullScreenTab.Members -> GroupInfoMembersPage(state, onEvent)
                    GroupInfoFullScreenTab.Settings -> GroupInfoSettingsPage(
                        state = state,
                        onEvent = onEvent,
                        onExitRequested = { showExitConfirmation = true }
                    )
                }
            }
        }
        if (showExitConfirmation) {
            GroupInfoExitConfirmation(
                groupName = state.groupName,
                enabled = !state.loading,
                onDismiss = { showExitConfirmation = false },
                onConfirm = {
                    showExitConfirmation = false
                    onEvent(GroupInfoUiEvent.ExitGroupRequested)
                }
            )
        }
    }
}

@Composable
private fun GroupInfoStatus(state: GroupInfoUiState) {
    val message = state.error ?: state.status ?: return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = message,
                color = if (state.error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** iOS 群名称、公告、二维码、备注与群内昵称对应的资料分页。 */
@Composable
private fun GroupInfoProfilePage(state: GroupInfoUiState, onEvent: (GroupInfoUiEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { GroupInfoSectionTitle("群资料") }
        item {
            GroupInfoEditableField(
                label = "群聊名称",
                value = state.groupName,
                actionLabel = "保存",
                onValueChange = { onEvent(GroupInfoUiEvent.GroupNameChanged(it)) },
                onAction = { onEvent(GroupInfoUiEvent.RenameRequested) },
                enabled = !state.loading
            )
        }
        item {
            GroupInfoEditableField(
                label = "群公告",
                value = state.announcement,
                actionLabel = "发布",
                singleLine = false,
                onValueChange = { onEvent(GroupInfoUiEvent.AnnouncementChanged(it)) },
                onAction = { onEvent(GroupInfoUiEvent.PublishAnnouncementRequested) },
                enabled = !state.loading
            )
        }
        item {
            GroupInfoEditableField(
                label = "群备注",
                value = state.remark,
                actionLabel = "保存",
                onValueChange = { onEvent(GroupInfoUiEvent.RemarkChanged(it)) },
                onAction = { onEvent(GroupInfoUiEvent.SaveRemarkRequested) },
                enabled = !state.loading
            )
        }
        item {
            GroupInfoEditableField(
                label = "我在群里的昵称",
                value = state.myNickname,
                actionLabel = "保存",
                onValueChange = { onEvent(GroupInfoUiEvent.MyNicknameChanged(it)) },
                onAction = { onEvent(GroupInfoUiEvent.SaveMyNicknameRequested) },
                enabled = !state.loading
            )
        }
        item {
            OutlinedButton(
                onClick = { onEvent(GroupInfoUiEvent.QrCodeRequested) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("拉取群二维码", fontWeight = FontWeight.Normal) }
        }
    }
}

/** iOS 成员列表对应的 LazyColumn，邀请和移出复用已有真实成员接口。 */
@Composable
private fun GroupInfoMembersPage(state: GroupInfoUiState, onEvent: (GroupInfoUiEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { GroupInfoSectionTitle("成员 (${state.memberCount})") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { onEvent(GroupInfoUiEvent.AddMemberRequested) },
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("邀请成员", fontWeight = FontWeight.Normal)
                }
                OutlinedButton(
                    onClick = { onEvent(GroupInfoUiEvent.RemoveMemberRequested) },
                    enabled = !state.loading && state.canManageMembers,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Remove, null)
                    Spacer(Modifier.width(6.dp))
                    Text("移出成员", fontWeight = FontWeight.Normal)
                }
            }
        }
        items(state.members, key = { it.id }) { member ->
            GroupInfoMemberRow(member = member, onClick = { onEvent(GroupInfoUiEvent.MemberSelected(member.id)) })
        }
    }
}

@Composable
private fun GroupInfoMemberRow(member: GroupInfoMemberUiState, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(member.displayName, fontWeight = FontWeight.Normal) },
        supportingContent = { Text("群成员") },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(member.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(member.initials.take(2), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Normal)
            }
        }
    )
}

/** 群通知、置顶、通讯录及成员显示偏好分页。 */
@Composable
private fun GroupInfoSettingsPage(
    state: GroupInfoUiState,
    onEvent: (GroupInfoUiEvent) -> Unit,
    onExitRequested: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { GroupInfoSectionTitle("群设置") }
        item {
            GroupInfoSwitchRow("新消息通知", !state.muted, !state.loading) {
                onEvent(GroupInfoUiEvent.MutedChanged(!it))
            }
        }
        item { HorizontalDivider() }
        item {
            GroupInfoSwitchRow("置顶聊天", state.pinned, !state.loading) {
                onEvent(GroupInfoUiEvent.PinnedChanged(it))
            }
        }
        item { HorizontalDivider() }
        item {
            GroupInfoSwitchRow("保存到通讯录", state.savedToContacts, !state.loading) {
                onEvent(GroupInfoUiEvent.SavedToContactsChanged(it))
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
        item { GroupInfoSectionTitle("成员显示") }
        item {
            GroupInfoSwitchRow("显示成员头像", state.memberAvatarsVisible, !state.loading) { next ->
                if (groupInfoCanHideMemberIdentity(next, state.memberNicknamesVisible)) {
                    onEvent(GroupInfoUiEvent.MemberAvatarsVisibleChanged(next))
                }
            }
        }
        item { HorizontalDivider() }
        item {
            GroupInfoSwitchRow("显示成员名称", state.memberNicknamesVisible, !state.loading) { next ->
                if (groupInfoCanHideMemberIdentity(state.memberAvatarsVisible, next)) {
                    onEvent(GroupInfoUiEvent.MemberNicknamesVisibleChanged(next))
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
        item {
            Button(
                onClick = onExitRequested,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("退出群聊", fontWeight = FontWeight.Normal) }
        }
    }
}

/**
 * UI：确认层直接绘制在全屏悬浮根内，不创建 Dialog Window，因此不会触发 BadTokenException。
 * 测试流程：点击“退出群聊”显示确认层；取消不调用接口；点击“确认退出”后才派发退出事件。
 */
@Composable
private fun GroupInfoExitConfirmation(
    groupName: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "确认退出群聊",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = if (groupName.isBlank()) {
                        "确定要退出当前群聊吗？"
                    } else {
                        "确定要退出“$groupName”吗？退出后将不再接收该群消息。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
                    Button(onClick = onConfirm, enabled = enabled) { Text("确认退出") }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun GroupInfoEditableField(
    label: String,
    value: String,
    actionLabel: String,
    singleLine: Boolean = true,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontWeight = FontWeight.Normal) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        FilledTonalButton(
            onClick = onAction,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) { Text(actionLabel, fontWeight = FontWeight.Normal) }
    }
}

@Composable
private fun GroupInfoSwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Normal) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
    )
}
