package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoMemberUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoUiState

@Composable
internal fun GroupInfoScreen(
    state: GroupInfoUiState,
    onEvent: (GroupInfoUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(PageBackground),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item { GroupInfoTopBar(state.memberCount) { onEvent(GroupInfoUiEvent.BackRequested) } }
        if (state.status != null || state.error != null) {
            item { StatusRow(state.error ?: state.status.orEmpty(), state.error != null) }
        }
        items(memberRows(state), key = { row -> row.joinToString("-") { it.id } }) { row ->
            GroupInfoMemberGridRow(row, state.canManageMembers, onEvent)
        }
        item { GroupInfoSectionGap() }
        item {
            Section {
                GroupInfoEditableRow("缇よ亰鍚嶇О", state.groupName, "濉啓缇よ亰鍚嶇О", {
                    onEvent(GroupInfoUiEvent.GroupNameChanged(it))
                }, actionLabel = "淇濆瓨", actionEnabled = !state.loading && state.groupName.isNotBlank()) {
                    onEvent(GroupInfoUiEvent.RenameRequested)
                }
                Divider()
                GroupInfoQrRow { onEvent(GroupInfoUiEvent.QrCodeRequested) }
                Divider()
                GroupInfoEditableRow("群公告", state.announcement, "未设置", {
                    onEvent(GroupInfoUiEvent.AnnouncementChanged(it))
                }, maxLines = 2, actionLabel = "发布", actionEnabled = !state.loading && state.announcement.isNotBlank()) {
                    onEvent(GroupInfoUiEvent.PublishAnnouncementRequested)
                }
                Divider()
                GroupInfoEditableRow("备注", state.remark, "添加备注", {
                    onEvent(GroupInfoUiEvent.RemarkChanged(it))
                })
            }
        }
        item { GroupInfoSectionGap() }
        item { Section { InfoRow("查找聊天记录") { onEvent(GroupInfoUiEvent.SearchChatHistoryRequested) } } }
        item { GroupInfoSectionGap() }
        item {
            Section {
                SwitchRow("消息免打扰", state.muted) { onEvent(GroupInfoUiEvent.MutedChanged(it)) }
                Divider()
                SwitchRow("置顶聊天", state.pinned) { onEvent(GroupInfoUiEvent.PinnedChanged(it)) }
                Divider()
                SwitchRow("保存到通讯录", state.savedToContacts) {
                    onEvent(GroupInfoUiEvent.SavedToContactsChanged(it))
                }
            }
        }
        item { GroupInfoSectionGap() }
        item {
            Section {
                GroupInfoEditableRow("我在群里的昵称", state.myNickname, "濉啓鏄电О", {
                    onEvent(GroupInfoUiEvent.MyNicknameChanged(it))
                })
                Divider()
                SwitchRow("显示群成员昵称", state.memberNicknamesVisible) {
                    onEvent(GroupInfoUiEvent.MemberNicknamesVisibleChanged(it))
                }
                Divider()
                SwitchRow("显示群成员头像", state.memberAvatarsVisible) {
                    onEvent(GroupInfoUiEvent.MemberAvatarsVisibleChanged(it))
                }
            }
        }
        item { GroupInfoSectionGap() }
        item {
            Section {
                GroupInfoEditableRow("设置当前聊天背景", state.backgroundLabel, "榛樿鑳屾櫙", {
                    onEvent(GroupInfoUiEvent.BackgroundChanged(it))
                })
                Divider()
                InfoRow("清空聊天记录") { onEvent(GroupInfoUiEvent.ClearChatHistoryRequested) }
                Divider()
                InfoRow("鎶曡瘔") { onEvent(GroupInfoUiEvent.ReportRequested) }
            }
        }
        item { DestructiveRow { onEvent(GroupInfoUiEvent.ExitGroupRequested) } }
    }
}

private data class MemberCell(val id: String, val member: GroupInfoMemberUiState? = null, val action: Int = 0)

private fun memberRows(state: GroupInfoUiState): List<List<MemberCell>> {
    val cells = state.members.map { MemberCell(it.id, it) }.toMutableList()
    cells += MemberCell("add", action = 1)
    if (state.canManageMembers) cells += MemberCell("remove", action = -1)
    return cells.chunked(5)
}

@Composable
private fun GroupInfoTopBar(memberCount: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).background(CardBackground)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "杩斿洖", tint = PrimaryText)
        }
        TextLabel("聊天信息($memberCount)", 15.sp, modifier = Modifier.align(Alignment.Center),
            weight = FontWeight.SemiBold, color = PrimaryText, maxLines = 1)
    }
}

@Composable
private fun StatusRow(text: String, error: Boolean) {
    TextLabel(text, 10.sp, color = if (error) Color(0xFFE45858) else SecondaryText,
        lineHeight = 13.sp, maxLines = 2,
        modifier = Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun GroupInfoMemberGridRow(
    row: List<MemberCell>,
    canManageMembers: Boolean,
    onEvent: (GroupInfoUiEvent) -> Unit
) {
    Row(Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { cell ->
            when {
                cell.member != null -> GroupInfoMemberCell(cell.member, Modifier.weight(1f)) {
                    onEvent(GroupInfoUiEvent.MemberSelected(cell.member.id))
                }
                cell.action > 0 -> GroupInfoAddMemberCell(true, Modifier.weight(1f)) {
                    onEvent(GroupInfoUiEvent.AddMemberRequested)
                }
                else -> GroupInfoAddMemberCell(canManageMembers, Modifier.weight(1f)) {
                    onEvent(GroupInfoUiEvent.RemoveMemberRequested)
                }
            }
        }
        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun GroupInfoMemberCell(member: GroupInfoMemberUiState, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(Color(member.avatarColor)),
            contentAlignment = Alignment.Center) {
            TextLabel(member.initials.take(2), 12.sp, weight = FontWeight.Bold, color = PrimaryText, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        TextLabel(member.displayName, 10.sp, color = SecondaryText, maxLines = 1)
    }
}

@Composable
private fun GroupInfoAddMemberCell(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF4F5F6)),
            contentAlignment = Alignment.Center) {
            Icon(if (enabled) Icons.Filled.Add else Icons.Filled.Remove, null, tint = SecondaryText)
        }
        Spacer(Modifier.height(4.dp))
        TextLabel(if (enabled) "添加" else "移出", 10.sp, color = SecondaryText, maxLines = 1)
    }
}

@Composable
private fun GroupInfoQrRow(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        TextLabel("群二维码", 13.sp, color = PrimaryText, modifier = Modifier.weight(1f), maxLines = 1)
        TextLabel("›", 19.sp, color = SecondaryText, maxLines = 1)
        Spacer(Modifier.width(6.dp)); Chevron()
    }
}

@Composable
private fun GroupInfoEditableRow(
    label: String, value: String, placeholder: String, onValueChange: (String) -> Unit,
    maxLines: Int = 1, actionLabel: String? = null, actionEnabled: Boolean = true, onAction: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (maxLines == 1) 44.dp else 58.dp)
        .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextLabel(label, 13.sp, color = PrimaryText, modifier = Modifier.width(126.dp), maxLines = 1)
        BasicTextField(value, onValueChange, Modifier.weight(1f), singleLine = maxLines == 1, maxLines = maxLines,
            textStyle = TextStyle(color = SecondaryText, fontSize = 12.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(OverlayTokens.accent), decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isBlank()) TextLabel(placeholder, 12.sp, color = PlaceholderText, maxLines = 1)
                    field()
                }
            })
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(6.dp))
            Button(onClick = onAction, enabled = actionEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = OverlayTokens.accent),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) {
                TextLabel(actionLabel, 10.sp, color = Color.White, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp)); Chevron()
    }
}

@Composable private fun Section(content: @Composable () -> Unit) = Column(Modifier.fillMaxWidth().background(CardBackground)) { content() }

@Composable
private fun InfoRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TextLabel(label, 13.sp, color = PrimaryText, modifier = Modifier.weight(1f), maxLines = 1); Chevron()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        TextLabel(label, 13.sp, color = PrimaryText, modifier = Modifier.weight(1f), maxLines = 1)
        Switch(checked, onCheckedChange)
    }
}

@Composable private fun Divider() = Spacer(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
@Composable private fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ChevronText, modifier = Modifier.size(18.dp))
@Composable private fun GroupInfoSectionGap() = Spacer(Modifier.fillMaxWidth().height(8.dp).background(PageBackground))

@Composable
private fun DestructiveRow(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(CardBackground).clickable(onClick = onClick).padding(vertical = 17.dp),
        contentAlignment = Alignment.Center) {
        TextLabel("退出群聊", 14.sp, weight = FontWeight.SemiBold, color = Color(0xFFE95A5A), maxLines = 1)
    }
}

private val PageBackground = Color(0xFFF2F3F5)
private val CardBackground = Color.White
private val PrimaryText = Color(0xFF111111)
private val SecondaryText = Color(0xFF656A70)
private val PlaceholderText = Color(0xFFB2B8BE)
private val ChevronText = Color(0xFFB4BBC2)
private val DividerColor = Color(0xFFE8ECEF)
