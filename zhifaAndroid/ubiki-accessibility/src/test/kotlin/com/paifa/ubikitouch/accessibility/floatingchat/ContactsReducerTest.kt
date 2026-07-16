package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactOperationState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileEditorAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileIntroAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsScreenAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsShortcut
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactsScreenAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactProfileEditorAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactProfileIntroAction
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

    @Test
    fun contactsScreenEventsMapToPlatformIndependentActions() {
        val cases = listOf(
            ContactsUiEvent.QueryChanged("alice") to ContactsScreenAction.UpdateQuery("alice"),
            ContactsUiEvent.SearchVisibilityChanged(true) to ContactsScreenAction.SetSearchVisible(true),
            ContactsUiEvent.SearchSubmitted to ContactsScreenAction.SubmitSearch,
            ContactsUiEvent.SyncRequested to ContactsScreenAction.Sync,
            ContactsUiEvent.ContactSelected("contact-7") to ContactsScreenAction.OpenContact("contact-7"),
            ContactsUiEvent.ShortcutSelected(ContactsShortcut.NewFriends) to
                ContactsScreenAction.OpenFriendRequests,
            ContactsUiEvent.ShortcutSelected(ContactsShortcut.Groups) to
                ContactsScreenAction.ShowPlaceholder(ContactsShortcut.Groups),
            ContactsUiEvent.PlusMenuRequested to ContactsScreenAction.TogglePlusMenu,
            ContactsUiEvent.CloseRequested to ContactsScreenAction.Close
        )

        cases.forEach { (event, expected) ->
            assertEquals(expected, contactsScreenAction(event))
        }
    }

    @Test
    fun unifiedContactsStateOwnsSearchVisibility() {
        val initial = ContactsUiState(searchVisible = false)

        val visible = reduceContactsState(
            initial,
            ContactsUiEvent.SearchVisibilityChanged(true)
        )

        assertEquals(true, visible.searchVisible)
    }

    @Test
    fun contactProfileIntroEventsMapToDistinctHostActions() {
        val cases = listOf(
            ContactProfileUiEvent.BackRequested to ContactProfileIntroAction.Back,
            ContactProfileUiEvent.EditRequested to ContactProfileIntroAction.Edit,
            ContactProfileUiEvent.MessageRequested to ContactProfileIntroAction.SendMessage,
            ContactProfileUiEvent.VoiceCallRequested to ContactProfileIntroAction.StartVoiceCall,
            ContactProfileUiEvent.VideoCallRequested to ContactProfileIntroAction.StartVideoCall
        )

        cases.forEach { (event, expected) ->
            assertEquals(expected, contactProfileIntroAction(event))
        }
        assertEquals(
            ContactProfileIntroAction.Ignore,
            contactProfileIntroAction(ContactProfileUiEvent.RemarkChanged("Alice"))
        )
    }

    @Test
    fun contactProfileEditorEventsMapToEditsAndNeverIntroRoutes() {
        val cases = listOf(
            ContactProfileUiEvent.BackRequested to ContactProfileEditorAction.Back,
            ContactProfileUiEvent.DoneRequested to ContactProfileEditorAction.Save,
            ContactProfileUiEvent.RemarkChanged("Alice") to
                ContactProfileEditorAction.UpdateRemark("Alice"),
            ContactProfileUiEvent.TagsChanged("VIP") to
                ContactProfileEditorAction.UpdateTags("VIP")
        )

        cases.forEach { (event, expected) ->
            assertEquals(expected, contactProfileEditorAction(event))
        }
        assertEquals(
            ContactProfileEditorAction.Ignore,
            contactProfileEditorAction(ContactProfileUiEvent.MessageRequested)
        )
    }

    private fun request(id: String): FriendRequestSummary {
        return FriendRequestSummary(id = id, displayName = id)
    }
}
