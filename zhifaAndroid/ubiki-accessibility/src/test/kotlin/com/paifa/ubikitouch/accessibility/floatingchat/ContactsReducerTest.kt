package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactOperationState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.reduceContactsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ContactsReducerTest {
    @Test
    fun acceptingFriendRequestExposesProcessingThenSuccess() {
        val initial = ContactsUiState(
            friendRequests = listOf(request("request-1"), request("request-2"))
        )

        val processing = reduceContactsState(
            initial,
            ContactsUiEvent.AcceptRequest("request-1")
        )
        val succeeded = reduceContactsState(
            processing,
            ContactsUiEvent.RequestSucceeded("request-1")
        )

        assertEquals(ContactOperationState.Processing("request-1"), processing.operation)
        assertEquals(ContactOperationState.Succeeded("request-1"), succeeded.operation)
        assertEquals(listOf("request-2"), succeeded.friendRequests.map { it.id })
        assertNotSame(initial, processing)
    }

    @Test
    fun failedFriendRequestKeepsPendingRequestAndError() {
        val initial = ContactsUiState(friendRequests = listOf(request("request-3")))

        val failed = reduceContactsState(
            reduceContactsState(initial, ContactsUiEvent.RejectRequest("request-3")),
            ContactsUiEvent.RequestFailed("request-3", "device offline")
        )

        assertEquals(
            ContactOperationState.Failed("request-3", "device offline"),
            failed.operation
        )
        assertEquals(listOf("request-3"), failed.friendRequests.map { it.id })
    }

    @Test
    fun querySelectionAndBackUpdateOnlyNavigationState() {
        val initial = ContactsUiState(friendRequests = listOf(request("request-4")))

        val queried = reduceContactsState(initial, ContactsUiEvent.QueryChanged("alice"))
        val selected = reduceContactsState(queried, ContactsUiEvent.ContactSelected("contact-7"))
        val backed = reduceContactsState(selected, ContactsUiEvent.BackRequested)

        assertEquals("alice", backed.query)
        assertEquals(null, backed.selectedContactId)
        assertEquals(initial.friendRequests, backed.friendRequests)
    }

    private fun request(id: String): FriendRequestSummary {
        return FriendRequestSummary(id = id, displayName = id)
    }
}
