package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toPrototypeToolSelection
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal class OutgoingMessageActions(
    private val conversation: FloatingChatConversation,
    private val selectedThread: ChatThreadSelection,
    private val selectedAccount: FloatingChatContact,
    private val nextSequence: () -> Int,
    private val prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage,
    private val onOutgoingMessageCreated: (FloatingChatMessage, String) -> Unit
) {
    fun addTextMessage(text: String, quotedMessage: FloatingChatMessage?) {
        val sequence = nextSequence()
        val baseMessage = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingTextMessage(
                    conversation = conversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence
                )
            }
        }
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(
            outgoingTextMessageWithOptionalQuote(baseMessage, quotedMessage),
            threadId
        )
        onOutgoingMessageCreated(message, threadId)
    }

    fun addVoiceMessage(audioUri: String, durationMs: Int) {
        val sequence = nextSequence()
        val baseMessage = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingVoiceMessage(
                    conversation = conversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence
                )
            }
        }
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(baseMessage, threadId)
        onOutgoingMessageCreated(message, threadId)
    }

    fun addToolMessage(
        action: FloatingChatToolAction,
        customize: (FloatingChatMessage) -> FloatingChatMessage = { it }
    ) {
        val sequence = nextSequence()
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = action,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = sequence
        )
        val message = customize(baseMessage)
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }

    fun addAccountCardMessage(
        account: FloatingChatContact,
        customize: (FloatingChatMessage) -> FloatingChatMessage
    ) {
        val sequence = nextSequence()
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Card,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = account.id,
            sequence = sequence
        )
        val message = customize(baseMessage)
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }
}
