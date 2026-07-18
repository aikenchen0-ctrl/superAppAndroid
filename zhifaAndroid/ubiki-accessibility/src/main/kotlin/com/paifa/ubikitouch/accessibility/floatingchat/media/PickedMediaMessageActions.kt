package com.paifa.ubikitouch.accessibility.floatingchat.media

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedDocumentEvent
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedMediaEvent
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal class PickedMediaMessageActions(
    private val conversation: FloatingChatConversation,
    private val selectedThread: ChatThreadSelection,
    private val selectedAccountId: String,
    private val nextSequence: () -> Int,
    private val prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage,
    private val onPickedMessageCreated: (FloatingChatMessage, String) -> Unit
) {
    fun addPickedMediaMessage(event: FloatingChatPickedMediaEvent) {
        val sequence = nextSequence()
        val baseMessage = pickedMediaMessageForEvent(
            event = event,
            conversation = conversation,
            selection = selectedThread,
            accountId = selectedAccountId,
            sequence = sequence
        )
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(baseMessage, threadId)
        onPickedMessageCreated(message, threadId)
    }

    fun addPickedDocumentMessage(event: FloatingChatPickedDocumentEvent) {
        val sequence = nextSequence()
        val baseMessage = pickedDocumentMessageForEvent(
            event = event,
            conversation = conversation,
            selection = selectedThread,
            accountId = selectedAccountId,
            sequence = sequence
        )
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(baseMessage, threadId)
        onPickedMessageCreated(message, threadId)
    }
}
