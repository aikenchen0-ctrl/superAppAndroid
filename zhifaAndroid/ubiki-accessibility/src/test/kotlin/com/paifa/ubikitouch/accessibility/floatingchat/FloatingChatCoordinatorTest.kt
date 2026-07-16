package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.ChatUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ChatUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatEffect
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MediaUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class FloatingChatCoordinatorTest {
    @Test
    fun documentClickRequestsExternalOpenByMessageId() {
        val effects = mutableListOf<FloatingChatEffect>()
        val coordinator = FloatingChatCoordinator(
            initialState = FloatingChatShellState(),
            effectSink = effects::add
        )

        coordinator.onMediaEvent(MediaUiEvent.DocumentClicked("message-42"))

        assertEquals(
            listOf(FloatingChatEffect.OpenDocument("message-42")),
            effects
        )
    }

    @Test
    fun inputChangeProducesNewChatStateAndSingleNotification() {
        val notifications = mutableListOf<FloatingChatShellState>()
        val initial = FloatingChatShellState(chat = ChatUiState.Empty)
        val coordinator = FloatingChatCoordinator(initial, notifications::add)

        coordinator.onEvent(
            FloatingChatShellEvent.Chat(ChatUiEvent.InputChanged("hello"))
        )

        assertEquals("hello", coordinator.state.chat.inputText)
        assertNotSame(initial, coordinator.state)
        assertEquals(listOf(coordinator.state), notifications)
    }

    @Test
    fun threadSelectionClearsMessageSelectionButKeepsDraft() {
        val initial = FloatingChatShellState(
            chat = ChatUiState.Empty.copy(
                inputText = "draft",
                selectedMessageIds = setOf("message-1")
            )
        )
        val coordinator = FloatingChatCoordinator(initial)

        coordinator.onEvent(
            FloatingChatShellEvent.Chat(ChatUiEvent.ThreadSelected("thread-2"))
        )

        assertEquals("thread-2", coordinator.state.chat.selectedThreadId)
        assertEquals(emptySet<String>(), coordinator.state.chat.selectedMessageIds)
        assertEquals("draft", coordinator.state.chat.inputText)
    }

    @Test
    fun shellExpandAndCollapseOnlyChangeExpandedState() {
        val coordinator = FloatingChatCoordinator(FloatingChatShellState())

        coordinator.onEvent(FloatingChatShellEvent.ExpandRequested)
        assertEquals(true, coordinator.state.expanded)

        coordinator.onEvent(FloatingChatShellEvent.CollapseRequested)
        assertEquals(false, coordinator.state.expanded)
    }
}
