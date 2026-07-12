package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScrmTextOutboxFactoryTest {
    @Test
    fun routeRequiresSavedAccountAndRemoteConversationId() {
        val summary = ScrmSettingsSummary(
            isConfigured = true,
            baseUrl = "https://api.example.com/openapi/v1",
            maskedApiKey = "****1234",
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals(
            ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend"),
            scrmTextRouteForThread(summary, "private:wxid_friend")
        )
        assertEquals(
            ScrmTextMessageRoute("device-1", "wxid_account", "123@chatroom"),
            scrmTextRouteForThread(summary, "group:123@chatroom")
        )
        assertNull(scrmTextRouteForThread(summary, "private:li-si"))
        assertNull(
            scrmTextRouteForThread(
                summary.copy(selectedDeviceUuid = null),
                "private:wxid_friend"
            )
        )
    }

    @Test
    fun queuedMessageAndOutboxUseClientRequestIdWithoutCredentials() {
        val message = FloatingChatMessage(
            id = "message-1",
            type = FloatingChatMessageType.Text,
            text = "你好",
            fromMe = true,
            senderName = "我",
            time = "刚刚"
        )
        val queued = message.withScrmQueueState("request-1")
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")

        val outbox = scrmTextOutboxItemForMessage(
            message = queued,
            route = route,
            now = 1_000L
        )

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals(FloatingChatSendState.Queued, queued.sendState)
        assertEquals("request-1", queued.clientRequestId)
        assertEquals("outbox-request-1", outbox.outboxId)
        assertEquals("message.text", outbox.operationType)
        assertEquals("message-1", outbox.aggregateId)
        assertEquals("wxid_account", outbox.accountWeChatId)
        assertEquals("device-1", outbox.deviceUuid)
        assertEquals("wxid_friend", outbox.conversationId)
        assertEquals("你好", payload.getValue("content").jsonPrimitive.content)
        assertFalse(outbox.requestJson.contains("api", ignoreCase = true))
    }
}
