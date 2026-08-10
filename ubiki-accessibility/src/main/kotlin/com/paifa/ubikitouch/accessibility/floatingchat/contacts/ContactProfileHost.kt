package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileEditorAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactProfileEditorAction
import com.paifa.ubikitouch.core.model.FloatingChatContact

@Composable
internal fun ContactProfileEditorHost(
    contact: FloatingChatContact,
    profile: LocalContactProfile,
    onProfileChange: (LocalContactProfile) -> Unit,
    onDeleteFriend: (FloatingChatContact) -> Unit,
    onDismiss: () -> Unit
) {
    val state = ContactProfileUiState(
        editing = true,
        contactId = contact.id,
        displayName = profile.remark.ifBlank { contact.name },
        originalName = contact.name,
        initials = contact.initials,
        avatarUrl = contact.avatarUrl,
        avatarColor = contact.avatarColor.toInt(),
        wechatId = profile.contactId.replace("-", "_"),
        description = contact.description,
        remark = profile.remark,
        phone = profile.phone.orEmpty(),
        tags = profile.tags,
        memo = profile.memo.ifBlank { defaultFriendProfileMemo(contact) },
        friendCircleVisible = profile.friendCircleVisible,
        onlyChat = profile.onlyChat,
        commonGroupCount = profile.commonGroupCount ?: 0,
        source = profile.source.orEmpty(),
        addedTime = profile.addedTime.orEmpty()
    )
    ContactProfileScreen(
        state = state,
        onEvent = { event ->
            val action = contactProfileEditorAction(event)
            val changedProfile = mergeContactProfileDraft(
                profile = profile,
                draft = state,
                action = action,
                updatedAt = System.currentTimeMillis()
            )
            if (changedProfile != null) {
                onProfileChange(changedProfile)
            }
            when (action) {
                ContactProfileEditorAction.Back,
                ContactProfileEditorAction.Save -> onDismiss()
                ContactProfileEditorAction.Delete -> onDeleteFriend(contact)
                else -> Unit
            }
        }
    )
}
