package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class ContactSummary(
    val id: String,
    val displayName: String,
    val secondaryText: String? = null,
    val avatarUrl: String? = null,
    val avatarColor: Int? = null
)

data class ContactGroupSummary(
    val title: String,
    val contacts: List<ContactSummary>
)

enum class ContactsShortcut {
    NewFriends,
    Groups,
    Tags,
    OfficialAccounts,
    WeComContacts,
    Assistant
}

data class FriendRequestSummary(
    val id: String,
    val displayName: String,
    val message: String? = null,
    val avatarUrl: String? = null
)

data class FriendRequestUiState(
    val requests: List<FriendRequestSummary> = emptyList(),
    val enabled: Boolean = true
)

data class ContactProfileUiState(
    val editing: Boolean = false,
    val contactId: String? = null,
    val displayName: String = "",
    val originalName: String = "",
    val initials: String = "",
    val avatarUrl: String? = null,
    val avatarColor: Int = 0,
    val wechatId: String = "",
    val region: String = "",
    val description: String = "",
    val remark: String = "",
    val phone: String = "",
    val tags: String = "",
    val memo: String = "",
    val friendCircleVisible: Boolean = true,
    val onlyChat: Boolean = false,
    val commonGroupCount: Int = 0,
    val source: String = "",
    val addedTime: String = "",
    val loading: Boolean = false
)

sealed interface ContactProfileUiEvent {
    data object BackRequested : ContactProfileUiEvent
    data object EditRequested : ContactProfileUiEvent
    data object MomentsRequested : ContactProfileUiEvent
    data object MessageRequested : ContactProfileUiEvent
    data object VoiceCallRequested : ContactProfileUiEvent
    data object VideoCallRequested : ContactProfileUiEvent
    data class RemarkChanged(val value: String) : ContactProfileUiEvent
    data class TagsChanged(val value: String) : ContactProfileUiEvent
    data class MemoChanged(val value: String) : ContactProfileUiEvent
    data class FriendCircleVisibilityChanged(val visible: Boolean) : ContactProfileUiEvent
    data class OnlyChatChanged(val enabled: Boolean) : ContactProfileUiEvent
    data object DeleteRequested : ContactProfileUiEvent
    data object DoneRequested : ContactProfileUiEvent
}

sealed interface ContactProfileIntroAction {
    data object Back : ContactProfileIntroAction
    data object Edit : ContactProfileIntroAction
    data object ShowMoments : ContactProfileIntroAction
    data object SendMessage : ContactProfileIntroAction
    data object StartVoiceCall : ContactProfileIntroAction
    data object StartVideoCall : ContactProfileIntroAction
    data object Ignore : ContactProfileIntroAction
}

fun contactProfileIntroAction(event: ContactProfileUiEvent): ContactProfileIntroAction {
    return when (event) {
        ContactProfileUiEvent.BackRequested -> ContactProfileIntroAction.Back
        ContactProfileUiEvent.EditRequested -> ContactProfileIntroAction.Edit
        ContactProfileUiEvent.MomentsRequested -> ContactProfileIntroAction.ShowMoments
        ContactProfileUiEvent.MessageRequested -> ContactProfileIntroAction.SendMessage
        ContactProfileUiEvent.VoiceCallRequested -> ContactProfileIntroAction.StartVoiceCall
        ContactProfileUiEvent.VideoCallRequested -> ContactProfileIntroAction.StartVideoCall
        else -> ContactProfileIntroAction.Ignore
    }
}

sealed interface ContactProfileEditorAction {
    data object Back : ContactProfileEditorAction
    data object Save : ContactProfileEditorAction
    data class UpdateRemark(val value: String) : ContactProfileEditorAction
    data class UpdateTags(val value: String) : ContactProfileEditorAction
    data class UpdateMemo(val value: String) : ContactProfileEditorAction
    data class SetFriendCircleVisibility(val visible: Boolean) : ContactProfileEditorAction
    data class SetOnlyChat(val enabled: Boolean) : ContactProfileEditorAction
    data object Delete : ContactProfileEditorAction
    data object Ignore : ContactProfileEditorAction
}

fun contactProfileEditorAction(event: ContactProfileUiEvent): ContactProfileEditorAction {
    return when (event) {
        ContactProfileUiEvent.BackRequested -> ContactProfileEditorAction.Back
        ContactProfileUiEvent.DoneRequested -> ContactProfileEditorAction.Save
        is ContactProfileUiEvent.RemarkChanged -> ContactProfileEditorAction.UpdateRemark(event.value)
        is ContactProfileUiEvent.TagsChanged -> ContactProfileEditorAction.UpdateTags(event.value)
        is ContactProfileUiEvent.MemoChanged -> ContactProfileEditorAction.UpdateMemo(event.value)
        is ContactProfileUiEvent.FriendCircleVisibilityChanged -> {
            ContactProfileEditorAction.SetFriendCircleVisibility(event.visible)
        }
        is ContactProfileUiEvent.OnlyChatChanged -> ContactProfileEditorAction.SetOnlyChat(event.enabled)
        ContactProfileUiEvent.DeleteRequested -> ContactProfileEditorAction.Delete
        else -> ContactProfileEditorAction.Ignore
    }
}

data class GroupInfoMemberUiState(
    val id: String,
    val displayName: String,
    val initials: String,
    val avatarUrl: String? = null,
    val avatarColor: Int = 0
)

data class GroupInfoUiState(
    val memberCount: Int = 0,
    val members: List<GroupInfoMemberUiState> = emptyList(),
    val canManageMembers: Boolean = false,
    val groupName: String = "",
    val announcement: String = "",
    val remark: String = "",
    val myNickname: String = "",
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val savedToContacts: Boolean = false,
    val memberNicknamesVisible: Boolean = true,
    val memberAvatarsVisible: Boolean = true,
    val backgroundLabel: String = "",
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null
)

data class GroupMemberUiState(
    val id: String,
    val displayName: String,
    val initials: String,
    val avatarUrl: String? = null,
    val avatarColor: Int = 0,
    val isFriend: Boolean = false
)

data class GroupMemberScreenUiState(
    val member: GroupMemberUiState,
    val addFriendLoading: Boolean = false,
    val addFriendStatus: String? = null,
    val addFriendError: String? = null
)

sealed interface GroupMemberUiEvent {
    data object BackRequested : GroupMemberUiEvent
    data object OpenChatRequested : GroupMemberUiEvent
    data object OpenProfileRequested : GroupMemberUiEvent
    data object OpenMomentsRequested : GroupMemberUiEvent
    data object StartVideoCallRequested : GroupMemberUiEvent
    data object AddFriendRequested : GroupMemberUiEvent
}

sealed interface GroupMemberAction {
    data object Back : GroupMemberAction
    data object OpenChat : GroupMemberAction
    data object OpenProfile : GroupMemberAction
    data object OpenMoments : GroupMemberAction
    data object StartVideoCall : GroupMemberAction
    data object AddFriend : GroupMemberAction
}

fun groupMemberAction(event: GroupMemberUiEvent): GroupMemberAction = when (event) {
    GroupMemberUiEvent.BackRequested -> GroupMemberAction.Back
    GroupMemberUiEvent.OpenChatRequested -> GroupMemberAction.OpenChat
    GroupMemberUiEvent.OpenProfileRequested -> GroupMemberAction.OpenProfile
    GroupMemberUiEvent.OpenMomentsRequested -> GroupMemberAction.OpenMoments
    GroupMemberUiEvent.StartVideoCallRequested -> GroupMemberAction.StartVideoCall
    GroupMemberUiEvent.AddFriendRequested -> GroupMemberAction.AddFriend
}

sealed interface GroupInfoUiEvent {
    data object BackRequested : GroupInfoUiEvent
    data object AddMemberRequested : GroupInfoUiEvent
    data object RemoveMemberRequested : GroupInfoUiEvent
    data class MemberSelected(val memberId: String) : GroupInfoUiEvent
    data class GroupNameChanged(val value: String) : GroupInfoUiEvent
    data object RenameRequested : GroupInfoUiEvent
    data object QrCodeRequested : GroupInfoUiEvent
    data class AnnouncementChanged(val value: String) : GroupInfoUiEvent
    data object PublishAnnouncementRequested : GroupInfoUiEvent
    data class RemarkChanged(val value: String) : GroupInfoUiEvent
    data object SearchChatHistoryRequested : GroupInfoUiEvent
    data class MutedChanged(val enabled: Boolean) : GroupInfoUiEvent
    data class PinnedChanged(val enabled: Boolean) : GroupInfoUiEvent
    data class SavedToContactsChanged(val enabled: Boolean) : GroupInfoUiEvent
    data class MyNicknameChanged(val value: String) : GroupInfoUiEvent
    data class MemberNicknamesVisibleChanged(val visible: Boolean) : GroupInfoUiEvent
    data class MemberAvatarsVisibleChanged(val visible: Boolean) : GroupInfoUiEvent
    data class BackgroundChanged(val value: String) : GroupInfoUiEvent
    data object ClearChatHistoryRequested : GroupInfoUiEvent
    data object ReportRequested : GroupInfoUiEvent
    data object ExitGroupRequested : GroupInfoUiEvent
}

sealed interface GroupInfoAction {
    data object Back : GroupInfoAction
    data object InviteMembers : GroupInfoAction
    data object RemoveMembers : GroupInfoAction
    data class OpenMember(val memberId: String) : GroupInfoAction
    data class UpdateGroupName(val value: String) : GroupInfoAction
    data object RenameGroup : GroupInfoAction
    data object LoadQrCode : GroupInfoAction
    data class UpdateAnnouncement(val value: String) : GroupInfoAction
    data object PublishAnnouncement : GroupInfoAction
    data class UpdateRemark(val value: String) : GroupInfoAction
    data object SearchChatHistory : GroupInfoAction
    data class SetMuted(val enabled: Boolean) : GroupInfoAction
    data class SetPinned(val enabled: Boolean) : GroupInfoAction
    data class SetSavedToContacts(val enabled: Boolean) : GroupInfoAction
    data class UpdateMyNickname(val value: String) : GroupInfoAction
    data class SetMemberNicknamesVisible(val visible: Boolean) : GroupInfoAction
    data class SetMemberAvatarsVisible(val visible: Boolean) : GroupInfoAction
    data class UpdateBackground(val value: String) : GroupInfoAction
    data object ClearChatHistory : GroupInfoAction
    data object Report : GroupInfoAction
    data object ExitGroup : GroupInfoAction
}

fun groupInfoAction(event: GroupInfoUiEvent): GroupInfoAction = when (event) {
    GroupInfoUiEvent.BackRequested -> GroupInfoAction.Back
    GroupInfoUiEvent.AddMemberRequested -> GroupInfoAction.InviteMembers
    GroupInfoUiEvent.RemoveMemberRequested -> GroupInfoAction.RemoveMembers
    is GroupInfoUiEvent.MemberSelected -> GroupInfoAction.OpenMember(event.memberId)
    is GroupInfoUiEvent.GroupNameChanged -> GroupInfoAction.UpdateGroupName(event.value)
    GroupInfoUiEvent.RenameRequested -> GroupInfoAction.RenameGroup
    GroupInfoUiEvent.QrCodeRequested -> GroupInfoAction.LoadQrCode
    is GroupInfoUiEvent.AnnouncementChanged -> GroupInfoAction.UpdateAnnouncement(event.value)
    GroupInfoUiEvent.PublishAnnouncementRequested -> GroupInfoAction.PublishAnnouncement
    is GroupInfoUiEvent.RemarkChanged -> GroupInfoAction.UpdateRemark(event.value)
    GroupInfoUiEvent.SearchChatHistoryRequested -> GroupInfoAction.SearchChatHistory
    is GroupInfoUiEvent.MutedChanged -> GroupInfoAction.SetMuted(event.enabled)
    is GroupInfoUiEvent.PinnedChanged -> GroupInfoAction.SetPinned(event.enabled)
    is GroupInfoUiEvent.SavedToContactsChanged -> GroupInfoAction.SetSavedToContacts(event.enabled)
    is GroupInfoUiEvent.MyNicknameChanged -> GroupInfoAction.UpdateMyNickname(event.value)
    is GroupInfoUiEvent.MemberNicknamesVisibleChanged -> GroupInfoAction.SetMemberNicknamesVisible(event.visible)
    is GroupInfoUiEvent.MemberAvatarsVisibleChanged -> GroupInfoAction.SetMemberAvatarsVisible(event.visible)
    is GroupInfoUiEvent.BackgroundChanged -> GroupInfoAction.UpdateBackground(event.value)
    GroupInfoUiEvent.ClearChatHistoryRequested -> GroupInfoAction.ClearChatHistory
    GroupInfoUiEvent.ReportRequested -> GroupInfoAction.Report
    GroupInfoUiEvent.ExitGroupRequested -> GroupInfoAction.ExitGroup
}

data class ContactsUiState(
    val query: String = "",
    val searchVisible: Boolean = false,
    val selectedContactId: String? = null,
    val contacts: List<ContactSummary> = emptyList(),
    val groups: List<ContactGroupSummary> = emptyList(),
    val friendRequests: List<FriendRequestSummary> = emptyList(),
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val operation: ContactOperationState = ContactOperationState.Idle
)

sealed interface ContactOperationState {
    data object Idle : ContactOperationState
    data class Processing(val requestId: String) : ContactOperationState
    data class Succeeded(val requestId: String) : ContactOperationState
    data class Failed(val requestId: String, val message: String) : ContactOperationState
}

sealed interface ContactsUiEvent {
    data class QueryChanged(val value: String) : ContactsUiEvent
    data class SearchVisibilityChanged(val visible: Boolean) : ContactsUiEvent
    data object SearchSubmitted : ContactsUiEvent
    data object SyncRequested : ContactsUiEvent
    data object CloseRequested : ContactsUiEvent
    data object PlusMenuRequested : ContactsUiEvent
    data class ShortcutSelected(val shortcut: ContactsShortcut) : ContactsUiEvent
    data class ContactSelected(val contactId: String) : ContactsUiEvent
    data class AcceptRequest(val requestId: String) : ContactsUiEvent
    data class RejectRequest(val requestId: String) : ContactsUiEvent
    data class RequestSucceeded(val requestId: String) : ContactsUiEvent
    data class RequestFailed(val requestId: String, val message: String) : ContactsUiEvent
    data object BackRequested : ContactsUiEvent
}

sealed interface ContactsScreenAction {
    data class UpdateQuery(val value: String) : ContactsScreenAction
    data class SetSearchVisible(val visible: Boolean) : ContactsScreenAction
    data object SubmitSearch : ContactsScreenAction
    data object Sync : ContactsScreenAction
    data object Close : ContactsScreenAction
    data object TogglePlusMenu : ContactsScreenAction
    data object OpenFriendRequests : ContactsScreenAction
    data class ShowPlaceholder(val shortcut: ContactsShortcut) : ContactsScreenAction
    data class OpenContact(val contactId: String) : ContactsScreenAction
    data object Ignore : ContactsScreenAction
}

fun contactsScreenAction(event: ContactsUiEvent): ContactsScreenAction {
    return when (event) {
        is ContactsUiEvent.QueryChanged -> ContactsScreenAction.UpdateQuery(event.value)
        is ContactsUiEvent.SearchVisibilityChanged -> ContactsScreenAction.SetSearchVisible(event.visible)
        ContactsUiEvent.SearchSubmitted -> ContactsScreenAction.SubmitSearch
        ContactsUiEvent.SyncRequested -> ContactsScreenAction.Sync
        ContactsUiEvent.CloseRequested -> ContactsScreenAction.Close
        ContactsUiEvent.PlusMenuRequested -> ContactsScreenAction.TogglePlusMenu
        is ContactsUiEvent.ShortcutSelected -> {
            if (event.shortcut == ContactsShortcut.NewFriends) {
                ContactsScreenAction.OpenFriendRequests
            } else {
                ContactsScreenAction.ShowPlaceholder(event.shortcut)
            }
        }
        is ContactsUiEvent.ContactSelected -> ContactsScreenAction.OpenContact(event.contactId)
        else -> ContactsScreenAction.Ignore
    }
}

fun reduceContactsState(
    state: ContactsUiState,
    event: ContactsUiEvent
): ContactsUiState {
    return when (event) {
        is ContactsUiEvent.QueryChanged -> state.copy(query = event.value)
        is ContactsUiEvent.SearchVisibilityChanged -> state.copy(searchVisible = event.visible)
        ContactsUiEvent.SearchSubmitted,
        ContactsUiEvent.SyncRequested,
        ContactsUiEvent.CloseRequested,
        ContactsUiEvent.PlusMenuRequested,
        is ContactsUiEvent.ShortcutSelected -> state
        is ContactsUiEvent.ContactSelected -> state.copy(selectedContactId = event.contactId)
        is ContactsUiEvent.AcceptRequest -> state.copy(
            operation = ContactOperationState.Processing(event.requestId)
        )
        is ContactsUiEvent.RejectRequest -> state.copy(
            operation = ContactOperationState.Processing(event.requestId)
        )
        is ContactsUiEvent.RequestSucceeded -> state.copy(
            friendRequests = state.friendRequests.filterNot { request ->
                request.id == event.requestId
            },
            operation = ContactOperationState.Succeeded(event.requestId)
        )
        is ContactsUiEvent.RequestFailed -> state.copy(
            operation = ContactOperationState.Failed(
                requestId = event.requestId,
                message = event.message
            )
        )
        ContactsUiEvent.BackRequested -> state.copy(selectedContactId = null)
    }
}
