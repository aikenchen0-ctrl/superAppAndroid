package com.paifa.ubikitouch.accessibility.data

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingChatRemoteStateMappingTest {
    @Test
    fun `remote send state survives local message round trip`() {
        val message = FloatingChatMessage(
            id = "message-1",
            type = FloatingChatMessageType.Text,
            text = "hello",
            fromMe = true,
            senderName = "Tester",
            time = "10:00",
            remoteMessageServerId = "server-message-1",
            remoteTaskId = 42L,
            sendState = FloatingChatSendState.Processing,
            sendErrorCode = "WAITING",
            sendErrorMessage = "waiting for final task result",
            clientRequestId = "request-1"
        )

        val local = message.toLocalChatMessage(
            threadId = "private:friend-1",
            createdAt = 100L
        )
        val restored = local.toFloatingChatMessage()

        assertEquals("server-message-1", local.remoteMessageServerId)
        assertEquals(42L, local.remoteTaskId)
        assertEquals("PROCESSING", local.sendState)
        assertEquals("request-1", local.clientRequestId)
        assertEquals(message.remoteMessageServerId, restored.remoteMessageServerId)
        assertEquals(message.remoteTaskId, restored.remoteTaskId)
        assertEquals(message.sendState, restored.sendState)
        assertEquals(message.sendErrorCode, restored.sendErrorCode)
        assertEquals(message.sendErrorMessage, restored.sendErrorMessage)
        assertEquals(message.clientRequestId, restored.clientRequestId)
    }

    @Test
    fun `legacy message defaults to local only without remote ids`() {
        val message = FloatingChatMessage(
            id = "legacy-message",
            type = FloatingChatMessageType.Text,
            text = "legacy",
            fromMe = false,
            senderName = "Friend",
            time = "09:00"
        )

        assertEquals(FloatingChatSendState.LocalOnly, message.sendState)
        assertNull(message.remoteMessageServerId)
        assertNull(message.remoteTaskId)
        assertNull(message.clientRequestId)
    }

    @Test
    fun `thread model stores account and remote conversation routing`() {
        val thread = LocalChatThread(
            threadId = "private:friend-1",
            kind = "private",
            remoteConversationId = "wxid_friend_1",
            accountWeChatId = "wxid_account_1",
            createdAt = 1L,
            updatedAt = 2L
        )

        assertEquals("wxid_friend_1", thread.remoteConversationId)
        assertEquals("wxid_account_1", thread.accountWeChatId)
    }
}
