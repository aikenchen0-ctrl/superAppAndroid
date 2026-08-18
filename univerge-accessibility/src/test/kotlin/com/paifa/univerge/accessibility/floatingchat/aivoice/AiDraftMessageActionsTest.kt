package com.paifa.univerge.accessibility.floatingchat.aivoice

import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageKind
import com.paifa.univerge.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDraftMessageActionsTest {
    @Test(timeout = 60_000)
    fun `sending draft reports created outgoing message and thread`() {
        val localMessages = mutableListOf(draft())
        val created = mutableListOf<Pair<FloatingChatMessage, String>>()
        val thread = ChatThreadSelection.Private("contact-a")
        val actions = actions(
            localMessages = localMessages,
            thread = thread,
            onOutgoingMessageCreated = { message, threadId -> created += message to threadId }
        )

        actions.sendDraftMessage(localMessages.single())

        assertEquals(1, created.size)
        assertEquals(thread.toLocalThreadId(), created.single().second)
        assertTrue(created.single().first.id.startsWith("local-ai-send-"))
        assertEquals(FloatingChatMessageKind.Normal, created.single().first.kind)
        assertEquals(localMessages.single(), created.single().first)
    }

    @Test(timeout = 60_000)
    fun `editing draft does not report outgoing message`() {
        val localMessages = mutableListOf(draft())
        val created = mutableListOf<Pair<FloatingChatMessage, String>>()
        val actions = actions(
            localMessages = localMessages,
            thread = ChatThreadSelection.Private("contact-a"),
            onOutgoingMessageCreated = { message, threadId -> created += message to threadId }
        )

        actions.updateDraftText(localMessages.single(), "edited")

        assertTrue(created.isEmpty())
    }

    private fun actions(
        localMessages: MutableList<FloatingChatMessage>,
        thread: ChatThreadSelection,
        onOutgoingMessageCreated: (FloatingChatMessage, String) -> Unit
    ): AiDraftMessageActions = AiDraftMessageActions(
        localMessages = localMessages,
        hiddenMessageIds = mutableMapOf(),
        sentDraftMessageIds = mutableMapOf(),
        selectedThread = thread,
        selectedAccountId = "account-a",
        nextSequence = { 1 },
        onDraftMessagesChanged = {},
        prepareOutgoingMessage = { message, _ -> message },
        onPersistLocalMessage = { _, _ -> },
        onOutgoingMessageCreated = onOutgoingMessageCreated,
        onDraftOverlaysClosed = {}
    )

    private fun draft(): FloatingChatMessage = FloatingChatMessage(
        id = "draft-a",
        type = FloatingChatMessageType.Text,
        text = "reply",
        fromMe = false,
        senderName = "AI",
        time = "10:00",
        kind = FloatingChatMessageKind.AiDraft,
        threadContactId = "contact-a"
    )
}
