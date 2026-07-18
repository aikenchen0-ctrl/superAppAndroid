package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberScreenUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberUiEvent

private val PageBackground = Color(0xFFF5F5F5)
private val CardBackground = Color.White
private val PrimaryText = Color(0xFF222222)
private val SecondaryText = Color(0xFF888888)

@Composable
internal fun GroupMemberScreen(
    state: GroupMemberScreenUiState,
    onEvent: (GroupMemberUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val member = state.member
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(PageBackground),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(32.dp).clickable { onEvent(GroupMemberUiEvent.BackRequested) },
                    tint = PrimaryText
                )
                Spacer(Modifier.weight(1f))
                TextLabel("...", 18.sp, color = PrimaryText, maxLines = 1)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(CardBackground).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(Color(member.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    TextLabel(member.initials.take(2), 18.sp, weight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextLabel(member.displayName, 20.sp, weight = FontWeight.Bold, color = PrimaryText, maxLines = 1)
                    TextLabel("地区：未知", 12.sp, color = SecondaryText, maxLines = 1)
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().background(CardBackground)) {
                MemberActionRow("朋友资料", if (member.isFriend) "" else "签名") {
                    onEvent(if (member.isFriend) GroupMemberUiEvent.OpenProfileRequested else GroupMemberUiEvent.AddFriendRequested)
                }
                MemberActionRow("朋友圈", "") { onEvent(GroupMemberUiEvent.OpenMomentsRequested) }
            }
        }
        if (!member.isFriend && (state.addFriendLoading || state.addFriendStatus != null || state.addFriendError != null)) {
            item {
                TextLabel(
                    state.addFriendError ?: state.addFriendStatus ?: if (state.addFriendLoading) "正在发送..." else "",
                    12.sp,
                    color = if (state.addFriendError != null) Color(0xFFE45858) else SecondaryText,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    maxLines = 2
                )
            }
        }
        item {
            MemberPrimaryAction(
                label = if (member.isFriend) "发消息" else if (state.addFriendLoading) "正在发送..." else "添加到通讯录",
                enabled = !state.addFriendLoading
            ) { onEvent(if (member.isFriend) GroupMemberUiEvent.OpenChatRequested else GroupMemberUiEvent.AddFriendRequested) }
        }
        if (member.isFriend) {
            item { MemberPrimaryAction("音视频通话") { onEvent(GroupMemberUiEvent.StartVideoCallRequested) } }
        }
    }
}

@Composable
private fun MemberActionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(label, 14.sp, color = PrimaryText, modifier = Modifier.weight(1f), maxLines = 1)
        TextLabel(value.ifBlank { "›" }, 13.sp, color = SecondaryText, maxLines = 1)
    }
}

@Composable
private fun MemberPrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(CardBackground).clickable(enabled = enabled, onClick = onClick).padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(label, 15.sp, weight = FontWeight.SemiBold, color = Color(0xFF49679E), maxLines = 1)
    }
}
