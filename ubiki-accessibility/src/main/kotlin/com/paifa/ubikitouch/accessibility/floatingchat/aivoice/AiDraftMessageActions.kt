package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import com.paifa.ubikitouch.accessibility.aiDraftSendAlreadyHandled
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.accessibility.preparedAiDraftTextMessageForSend
import com.paifa.ubikitouch.core.model.FloatingChatInlineToken
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal fun editedAiDraftMessage(message: FloatingChatMessage, text: String): FloatingChatMessage {
    val trimmedText = text.trim()
    return message.copy(
        text = trimmedText,
        type = FloatingChatMessageType.MixedText,
        kind = FloatingChatMessageKind.AiDraft,
        presentation = FloatingChatMessagePresentation.Bubble,
        inlineTokens = listOf(
            FloatingChatInlineToken(
                FloatingChatInlineTokenType.Ai,
                "AI"
            ),
            FloatingChatInlineToken(
                FloatingChatInlineTokenType.Plain,
                " $trimmedText"
            )
        )
    )
}

internal class AiDraftMessageActions(
    private val localMessages: MutableList<FloatingChatMessage>,
    private val hiddenMessageIds: MutableMap<String, Boolean>,
    private val sentDraftMessageIds: MutableMap<String, Boolean>,
    private val selectedThread: ChatThreadSelection,
    private val selectedAccountId: String,
    private val nextSequence: () -> Int,
    private val onDraftMessagesChanged: () -> Unit,
    private val prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage,
    private val onPersistLocalMessage: (FloatingChatMessage, String) -> Unit,
    private val onDraftOverlaysClosed: () -> Unit
) {
    fun upsertDraftMessage(draft: FloatingChatMessage, replaceMessageId: String? = null) {
        val replacedIndex = replaceMessageId?.let { messageId ->
            localMessages.indexOfFirst { message -> message.id == messageId }
        } ?: -1
        if (replacedIndex >= 0) {
            localMessages[replacedIndex] = draft
        } else {
            replaceMessageId?.let { messageId -> hiddenMessageIds[messageId] = true }
            localMessages += draft
        }
        onDraftMessagesChanged()
    }

    fun removeDraftMessage(message: FloatingChatMessage) {
        val removed = localMessages.removeAll { draft -> draft.id == message.id }
        if (!removed) {
            hiddenMessageIds[message.id] = true
        }
        onDraftMessagesChanged()
    }

    fun updateDraftText(message: FloatingChatMessage, text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        val edited = editedAiDraftMessage(message, trimmedText)
        val localIndex = localMessages.indexOfFirst { draft -> draft.id == message.id }
        if (localIndex >= 0) {
            localMessages[localIndex] = edited
        } else {
            hiddenMessageIds[message.id] = true
            localMessages += edited.copy(
                id = "local-ai-draft-edited-${selectedThread.toLocalThreadId()}-$selectedAccountId-${nextSequence()}"
            )
        }
        onDraftMessagesChanged()
    }

    fun sendDraftMessage(message: FloatingChatMessage, overrideText: String? = null) {
        val text = (overrideText ?: message.text).trim()
        if (text.isBlank()) return
        if (aiDraftSendAlreadyHandled(message.id, sentDraftMessageIds)) return
        sentDraftMessageIds[message.id] = true
        val sequence = nextSequence()
        val threadId = selectedThread.toLocalThreadId()
        val sentMessage = preparedAiDraftTextMessageForSend(
            draft = editedAiDraftMessage(message, text),
            messageId = "local-ai-send-$threadId-$selectedAccountId-$sequence",
            threadId = threadId,
            prepareOutgoingMessage = prepareOutgoingMessage,
            time = "鍒氬垰"
        )
        val localIndex = localMessages.indexOfFirst { draft -> draft.id == message.id }
        if (localIndex >= 0) {
            localMessages[localIndex] = sentMessage
        } else {
            hiddenMessageIds[message.id] = true
            localMessages += sentMessage
        }
        onDraftMessagesChanged()
        onPersistLocalMessage(sentMessage, threadId)
        onDraftOverlaysClosed()
    }
}
