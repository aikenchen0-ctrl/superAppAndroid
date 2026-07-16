package com.paifa.ubikitouch.accessibility.floatingchat.shell

import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatEffect
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MediaUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ChatUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.chat.reduceChatState

internal class FloatingChatCoordinator(
    initialState: FloatingChatShellState,
    private val onStateChanged: (FloatingChatShellState) -> Unit = {},
    private val effectSink: (FloatingChatEffect) -> Unit = {}
) {
    var state: FloatingChatShellState = initialState
        private set

    fun onEvent(event: FloatingChatShellEvent) {
        val next = when (event) {
            FloatingChatShellEvent.ExpandRequested -> state.copy(expanded = true)
            FloatingChatShellEvent.CollapseRequested -> state.copy(expanded = false)
            is FloatingChatShellEvent.AccountSelected -> state.copy(activeAccountId = event.accountId)
            is FloatingChatShellEvent.PanelSelected -> state.copy(activePanel = event.panel)
            FloatingChatShellEvent.PreviewDismissed -> state.copy(previewVisible = false)
            is FloatingChatShellEvent.Chat -> when (event.event) {
                ChatUiEvent.CollapseRequested -> state.copy(expanded = false)
                else -> state.copy(chat = reduceChatState(state.chat, event.event))
            }
        }
        if (next == state) return
        state = next
        onStateChanged(next)
    }

    fun onMediaEvent(event: MediaUiEvent) {
        when (event) {
            is MediaUiEvent.DocumentClicked -> {
                effectSink(FloatingChatEffect.OpenDocument(event.messageId))
            }
        }
    }
}
