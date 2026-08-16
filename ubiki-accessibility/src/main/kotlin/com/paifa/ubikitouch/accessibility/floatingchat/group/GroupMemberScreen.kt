package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberScreenUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberUiEvent

/**
 * UI：群成员详情复用群信息的透明全屏悬浮工作区和 M3 工具栏。
 * 接口：成员操作仍通过 [GroupMemberUiEvent] 回传给既有群资料/SCRM 宿主。
 * 测试流程：从群成员列表打开成员，确认左上返回仍回到群信息，点击发消息后聊天根平滑切换。
 */
@Composable
internal fun GroupMemberScreen(
    state: GroupMemberScreenUiState,
    onEvent: (GroupMemberUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val member = state.member
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = member.displayName,
            onBack = { onEvent(GroupMemberUiEvent.BackRequested) }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = member.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (member.isFriend) "已是联系人" else "群成员",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingContent = { MemberAvatar(member.initials, member.avatarColor) }
                    )
                }
            }
            item {
                Card {
                    Column {
                        MemberActionRow(
                            label = if (member.isFriend) "联系人资料" else "添加到通讯录",
                            onClick = {
                                onEvent(
                                    if (member.isFriend) {
                                        GroupMemberUiEvent.OpenProfileRequested
                                    } else {
                                        GroupMemberUiEvent.AddFriendRequested
                                    }
                                )
                            }
                        )
                        MemberActionRow(
                            label = "朋友圈",
                            onClick = { onEvent(GroupMemberUiEvent.OpenMomentsRequested) }
                        )
                    }
                }
            }
            if (!member.isFriend && (state.addFriendLoading || state.addFriendStatus != null || state.addFriendError != null)) {
                item {
                    val isError = state.addFriendError != null
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                    ) {
                        Text(
                            text = state.addFriendError
                                ?: state.addFriendStatus
                                ?: "正在发送好友申请",
                            color = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onEvent(
                            if (member.isFriend) {
                                GroupMemberUiEvent.OpenChatRequested
                            } else {
                                GroupMemberUiEvent.AddFriendRequested
                            }
                        )
                    },
                    enabled = !state.addFriendLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (member.isFriend) {
                            "发消息"
                        } else if (state.addFriendLoading) {
                            "正在发送好友申请"
                        } else {
                            "添加到通讯录"
                        }
                    )
                }
            }
            if (member.isFriend) {
                item {
                    FilledTonalButton(
                        onClick = { onEvent(GroupMemberUiEvent.StartVideoCallRequested) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("音视频通话")
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAvatar(initials: String, color: Int) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(color)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun MemberActionRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
