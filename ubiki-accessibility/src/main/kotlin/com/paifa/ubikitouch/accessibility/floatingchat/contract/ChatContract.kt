package com.paifa.ubikitouch.accessibility.floatingchat.contract

import com.paifa.ubikitouch.core.model.FloatingChatConversation

data class ChatUiState(
    val conversation: FloatingChatConversation,
    val selectedThreadId: String? = null,
    val inputText: String = "",
    val selectedMessageIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        val Empty = ChatUiState(
            conversation = FloatingChatConversation(
                peerName = "",
                accountName = "",
                contacts = emptyList(),
                accountContacts = emptyList(),
                messages = emptyList(),
                toolActions = emptyList(),
                groupContacts = emptyList()
            )
        )
    }
}

sealed interface ChatUiEvent {
    data class InputChanged(val value: String) : ChatUiEvent
    data object SendClicked : ChatUiEvent
    data class MessageClicked(val messageId: String) : ChatUiEvent
    data class MessageLongPressed(val messageId: String) : ChatUiEvent
    data class ThreadSelected(val threadId: String) : ChatUiEvent
    data object CollapseRequested : ChatUiEvent
}
