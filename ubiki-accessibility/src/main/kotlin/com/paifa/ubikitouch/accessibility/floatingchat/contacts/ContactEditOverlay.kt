package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberScreenUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.groupMemberAction
import com.paifa.ubikitouch.accessibility.floatingchat.group.GroupInfoHost
import com.paifa.ubikitouch.accessibility.floatingchat.group.GroupMemberScreen
import com.paifa.ubikitouch.accessibility.floatingchat.group.groupInfoMemberIsFriend
import com.paifa.ubikitouch.accessibility.floatingchat.group.groupInfoMembersForGroup
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage

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
    modifier: Modifier = Modifier
) {
    var selectedGroupMember by remember(target) { mutableStateOf<FloatingChatContact?>(null) }
    var friendProfileTarget by remember(target) { mutableStateOf<FloatingChatContact?>(null) }
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(target) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 620.dp)
                .pointerInput(target) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 10.dp
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
                        member = com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberUiState(
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
