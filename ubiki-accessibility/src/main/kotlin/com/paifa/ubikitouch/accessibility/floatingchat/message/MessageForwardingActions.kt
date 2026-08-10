package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.selectedAccountForThread
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal data class ForwardStartSelection(
    val pendingMessages: List<FloatingChatMessage> = emptyList(),
    val pendingMode: MultiForwardMode? = null,
    val modeMessages: List<FloatingChatMessage> = emptyList()
)

internal fun forwardStartSelection(messages: List<FloatingChatMessage>): ForwardStartSelection? {
    val selected = messages.takeIf { it.isNotEmpty() } ?: return null
    return if (selected.size == 1) {
        ForwardStartSelection(
            pendingMessages = selected,
            pendingMode = MultiForwardMode.Separate
        )
    } else {
        ForwardStartSelection(modeMessages = selected)
    }
}

internal fun selectedMessagesForAction(
    messages: List<FloatingChatMessage>,
    selectedMessageIds: Map<String, Boolean>
): List<FloatingChatMessage> {
    val messagesById = messages.associateBy { message -> message.id }
    return selectedMessageIds
        .filterValues { selected -> selected }
        .keys
        .mapNotNull { id -> messagesById[id] }
}

internal class MessageForwardingActions(
    private val conversation: FloatingChatConversation,
    private val nextSequence: () -> Int,
    private val prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage,
    private val onForwardMessageCreated: (FloatingChatMessage, String) -> Unit
) {
    fun addForwardedMessage(source: FloatingChatMessage, target: ChatThreadSelection) {
        val account = selectedAccountForThread(conversation, target)
        val threadId = target.toLocalThreadId()
        val message = preparedForwardedMessageForSend(
            source = source,
            conversation = conversation,
            target = target,
            accountId = account.id,
            sequence = nextSequence(),
            prepareOutgoingMessage = prepareOutgoingMessage
        )
        onForwardMessageCreated(message, threadId)
    }

    fun addCombinedForwardedMessage(sources: List<FloatingChatMessage>, target: ChatThreadSelection) {
        if (sources.isEmpty()) return
        val account = selectedAccountForThread(conversation, target)
        val threadId = target.toLocalThreadId()
        val message = combinedForwardChatHistoryMessage(
            messages = sources,
            conversation = conversation,
            target = target,
            accountId = account.id,
            sequence = nextSequence()
        )
        onForwardMessageCreated(message, threadId)
    }
}
