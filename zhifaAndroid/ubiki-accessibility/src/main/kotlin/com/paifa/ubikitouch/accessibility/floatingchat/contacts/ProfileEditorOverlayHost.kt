package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.floatingchat.account.AccountEditOverlay
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun ProfileEditorOverlayHost(
    contactEditorTarget: ContactEditorTarget?,
    accountEditorTarget: FloatingChatContact?,
    selectedAccount: FloatingChatContact,
    groupProfiles: Map<String, LocalGroupProfile>,
    visibleMessages: List<FloatingChatMessage>,
    contacts: List<FloatingChatContact>,
    contactProfiles: Map<String, LocalContactProfile>,
    accountProfile: (FloatingChatContact) -> FloatingChatAccountProfile,
    groupMemberAddFriendTargetId: String?,
    groupMemberAddFriendLoading: Boolean,
    groupMemberAddFriendStatus: String?,
    groupMemberAddFriendError: String?,
    onGroupProfileChange: (LocalGroupProfile) -> Unit,
    onContactProfileChange: (LocalContactProfile) -> Unit,
    onDeleteFriend: (FloatingChatContact) -> Unit,
    onOpenPrivateChat: (FloatingChatContact) -> Unit,
    onAddFriendFromGroupMember: (FloatingChatContact) -> Unit,
    onContactEditorTargetChanged: (ContactEditorTarget?) -> Unit,
    onPickAccountAvatar: (FloatingChatContact) -> Unit,
    onSaveAccountProfile: (FloatingChatContact, FloatingChatAccountProfile) -> Unit,
    onAccountEditorTargetChanged: (FloatingChatContact?) -> Unit,
    modifier: Modifier = Modifier
) {
    contactEditorTarget?.let { target ->
        ContactEditOverlay(
            target = target,
            accountId = selectedAccount.id,
            groupProfiles = groupProfiles,
            visibleMessages = visibleMessages,
            contacts = contacts,
            onGroupProfileChange = onGroupProfileChange,
            contactProfiles = contactProfiles,
            onContactProfileChange = onContactProfileChange,
            onDeleteFriend = onDeleteFriend,
            groupMemberAddFriendTargetId = groupMemberAddFriendTargetId,
            groupMemberAddFriendLoading = groupMemberAddFriendLoading,
            groupMemberAddFriendStatus = groupMemberAddFriendStatus,
            groupMemberAddFriendError = groupMemberAddFriendError,
            onOpenPrivateChat = { contact ->
                onOpenPrivateChat(contact)
                onContactEditorTargetChanged(null)
            },
            onAddFriendFromGroupMember = onAddFriendFromGroupMember,
            onDismiss = { onContactEditorTargetChanged(null) },
            modifier = modifier.fillMaxSize()
        )
    }
    accountEditorTarget?.let { account ->
        AccountEditOverlay(
            account = account,
            profile = accountProfile(account),
            onPickAvatar = { onPickAccountAvatar(account) },
            onSave = { profile ->
                onSaveAccountProfile(account, profile)
                onAccountEditorTargetChanged(null)
            },
            onDismiss = { onAccountEditorTargetChanged(null) },
            modifier = modifier.fillMaxSize()
        )
    }
}
