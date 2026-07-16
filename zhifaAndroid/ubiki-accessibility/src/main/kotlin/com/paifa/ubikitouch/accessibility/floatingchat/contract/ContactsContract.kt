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
    data object VideoCallRequested : ContactProfileUiEvent
    data class RemarkChanged(val value: String) : ContactProfileUiEvent
    data class TagsChanged(val value: String) : ContactProfileUiEvent
    data class MemoChanged(val value: String) : ContactProfileUiEvent
    data class FriendCircleVisibilityChanged(val visible: Boolean) : ContactProfileUiEvent
    data class OnlyChatChanged(val enabled: Boolean) : ContactProfileUiEvent
    data object DeleteRequested : ContactProfileUiEvent
    data object DoneRequested : ContactProfileUiEvent
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
