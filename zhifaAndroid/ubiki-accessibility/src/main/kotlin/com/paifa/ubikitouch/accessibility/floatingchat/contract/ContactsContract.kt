package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class ContactSummary(
    val id: String,
    val displayName: String,
    val secondaryText: String? = null,
    val avatarUrl: String? = null
)

data class FriendRequestSummary(
    val id: String,
    val displayName: String,
    val message: String? = null,
    val avatarUrl: String? = null
)

data class ContactsUiState(
    val query: String = "",
    val selectedContactId: String? = null,
    val contacts: List<ContactSummary> = emptyList(),
    val friendRequests: List<FriendRequestSummary> = emptyList(),
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
    data class ContactSelected(val contactId: String) : ContactsUiEvent
    data class AcceptRequest(val requestId: String) : ContactsUiEvent
    data class RejectRequest(val requestId: String) : ContactsUiEvent
    data class RequestSucceeded(val requestId: String) : ContactsUiEvent
    data class RequestFailed(val requestId: String, val message: String) : ContactsUiEvent
    data object BackRequested : ContactsUiEvent
}

fun reduceContactsState(
    state: ContactsUiState,
    event: ContactsUiEvent
): ContactsUiState {
    return when (event) {
        is ContactsUiEvent.QueryChanged -> state.copy(query = event.value)
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
