package com.paifa.univerge.accessibility.floatingchat.chat

import com.paifa.univerge.accessibility.floatingchat.contract.ChatUiEvent
import com.paifa.univerge.accessibility.floatingchat.contract.ChatUiState

internal fun reduceChatState(
    state: ChatUiState,
    event: ChatUiEvent
): ChatUiState {
    return when (event) {
        is ChatUiEvent.InputChanged -> state.copy(inputText = event.value)
        is ChatUiEvent.ThreadSelected -> state.copy(
            selectedThreadId = event.threadId,
            selectedMessageIds = emptySet()
        )
        ChatUiEvent.SendClicked,
        is ChatUiEvent.MessageClicked,
        is ChatUiEvent.MessageLongPressed,
        ChatUiEvent.CollapseRequested -> state
    }
}
