package com.paifa.univerge.accessibility.floatingchat.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.data.LocalContactProfile
import com.paifa.univerge.accessibility.data.LocalGroupProfile
import com.paifa.univerge.accessibility.floatingchat.contract.GroupMemberAction
import com.paifa.univerge.accessibility.floatingchat.contract.GroupMemberScreenUiState
import com.paifa.univerge.accessibility.floatingchat.contract.groupMemberAction
import com.paifa.univerge.accessibility.floatingchat.group.GroupInfoHost
import com.paifa.univerge.accessibility.floatingchat.group.GroupMemberScreen
import com.paifa.univerge.accessibility.floatingchat.group.groupInfoMemberIsFriend
import com.paifa.univerge.accessibility.floatingchat.group.groupInfoMembersForGroup
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatMessage

@Composable
internal fun ContactEditOverlay(
    target: ContactEditorTarget,
    accountId: String,
    groupProfiles: Map<String, LocalGroupProfile>,
    visibleMessages: List<FloatingChatMessage>,
    contacts: List<FloatingChatContact>,
    onGroupProfileChange: (LocalGroupProfile) -> Unit,
    contactProfiles: Map<String, LocalContactProfile>,
    onContactProfileChange: (LocalContactProfile) -> Unit,
    onDeleteFriend: (FloatingChatContact) -> Unit,
    groupMemberAddFriendTargetId: String?,
    groupMemberAddFriendLoading: Boolean,
    groupMemberAddFriendStatus: String?,
    groupMemberAddFriendError: String?,
    onOpenPrivateChat: (FloatingChatContact) -> Unit,
    onAddFriendFromGroupMember: (FloatingChatContact) -> Unit,
    onDismiss: () -> Unit,
    useFullScreenWorkspace: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 群信息复用已附着的无障碍悬浮根视图，不创建 Dialog、Activity 或新 Window，避免 BadTokenException。
    // 测试流程：右侧“群信息”以透明全屏工作区打开；从成员页返回后仍停留在同一工作区。
    val isGroupInfoWorkspace = useFullScreenWorkspace && target is ContactEditorTarget.Group
    var selectedGroupMember by remember(target) { mutableStateOf<FloatingChatContact?>(null) }
    var friendProfileTarget by remember(target) { mutableStateOf<FloatingChatContact?>(null) }
    Box(
        modifier = if (isGroupInfoWorkspace) {
            modifier.background(MaterialTheme.colorScheme.surface)
        } else {
            modifier
                .background(OverlayTokens.centerPanelScrim)
                .pointerInput(target) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        },
        contentAlignment = if (isGroupInfoWorkspace) Alignment.TopStart else Alignment.Center
    ) {
        val fullScreenProfile = target is ContactEditorTarget.User || friendProfileTarget != null
        val fullScreenGroupInfo = target is ContactEditorTarget.Group
        MaterialSurface(
            modifier = if (fullScreenProfile) {
                Modifier
                    .fillMaxSize()
                    // 悬浮窗不自动消费系统状态栏 inset，显式保留顶部安全区。
                    .padding(top = ContactProfileStatusBarReserve)
            } else if (fullScreenGroupInfo) {
                // 群信息自身绘制 30dp 安全区和进出场 translationY 动画，不能再作为居中对话框展示。
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .widthIn(min = 320.dp, max = 390.dp)
                    .heightIn(max = 620.dp)
            }
                .pointerInput(target) {
                    detectTapGestures(onTap = {})
                },
            shape = if (fullScreenProfile || fullScreenGroupInfo) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp),
            color = if (isGroupInfoWorkspace) MaterialTheme.colorScheme.surface else OverlayTokens.panel,
            border = if (fullScreenProfile || fullScreenGroupInfo) null else BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = if (fullScreenProfile || fullScreenGroupInfo) 0.dp else 10.dp
        ) {
            val activeFriendProfile = friendProfileTarget
            val activeGroupMember = selectedGroupMember
            when {
                activeFriendProfile != null -> ContactProfileEditorHost(
                    contact = activeFriendProfile,
                    profile = contactProfiles[contactProfileKey(accountId, activeFriendProfile.id)]
                        ?: defaultLocalContactProfileFor(
                            accountId = accountId,
                            contact = activeFriendProfile
                        ),
                    onProfileChange = onContactProfileChange,
                    onDeleteFriend = onDeleteFriend,
                    onDismiss = { friendProfileTarget = null }
                )
                activeGroupMember != null -> GroupMemberScreen(
                    state = GroupMemberScreenUiState(
                        member = com.paifa.univerge.accessibility.floatingchat.contract.GroupMemberUiState(
                            id = activeGroupMember.id,
                            displayName = activeGroupMember.name,
                            initials = activeGroupMember.initials,
                            avatarUrl = activeGroupMember.avatarUrl,
                            avatarColor = activeGroupMember.avatarColor.toInt(),
                            isFriend = groupInfoMemberIsFriend(activeGroupMember, contacts)
                        ),
                        addFriendLoading = groupMemberAddFriendTargetId == activeGroupMember.id && groupMemberAddFriendLoading,
                        addFriendStatus = groupMemberAddFriendStatus.takeIf { groupMemberAddFriendTargetId == activeGroupMember.id },
                        addFriendError = groupMemberAddFriendError.takeIf { groupMemberAddFriendTargetId == activeGroupMember.id }
                    ),
                    onEvent = { event ->
                        when (groupMemberAction(event)) {
                            GroupMemberAction.Back -> selectedGroupMember = null
                            GroupMemberAction.OpenChat -> onOpenPrivateChat(activeGroupMember)
                            GroupMemberAction.OpenProfile -> friendProfileTarget = activeGroupMember
                            GroupMemberAction.OpenMoments -> Unit
                            GroupMemberAction.StartVideoCall -> Unit
                            GroupMemberAction.AddFriend -> onAddFriendFromGroupMember(activeGroupMember)
                        }
                    }
                )
                target is ContactEditorTarget.Group -> GroupInfoHost(
                    accountId = accountId,
                    group = target.group,
                    profile = groupProfiles[groupProfileKey(accountId, target.group.id)]
                        ?: defaultLocalGroupProfileFor(accountId = accountId, group = target.group),
                    contacts = contacts,
                    members = groupInfoMembersForGroup(
                        group = target.group,
                        contacts = contacts,
                        messages = visibleMessages
                    ),
                    onProfileChange = onGroupProfileChange,
                    onMemberClick = { member -> selectedGroupMember = member },
                    onDismiss = onDismiss
                )
                target is ContactEditorTarget.User -> ContactProfileEditorHost(
                    contact = target.contact,
                    profile = contactProfiles[contactProfileKey(accountId, target.contact.id)]
                        ?: defaultLocalContactProfileFor(
                            accountId = accountId,
                            contact = target.contact
                        ),
                    onProfileChange = onContactProfileChange,
                    onDeleteFriend = onDeleteFriend,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private val ContactProfileStatusBarReserve = 30.dp
