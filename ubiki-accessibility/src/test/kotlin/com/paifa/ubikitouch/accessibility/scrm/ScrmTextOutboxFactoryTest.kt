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

    @Test
    fun mediaOutboxUsesMessageTypeSpecificOperationAndPayload() {
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")
        val image = FloatingChatMessage(
            id = "image-1",
            type = FloatingChatMessageType.ImageThumbnail,
            text = "",
            fromMe = true,
            senderName = "me",
            time = "now",
            resourceUrl = "content://media/images/1",
            thumbnailUrl = "content://media/images/1",
            mediaMimeType = "image/jpeg"
        ).withScrmQueueState("image-request")

        val outbox = scrmMediaOutboxItemForMessage(image, route, now = 2_000L)

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals("outbox-image-request", outbox.outboxId)
        assertEquals("message.image", outbox.operationType)
        assertEquals("image-1", outbox.aggregateId)
        assertEquals("content://media/images/1", payload.getValue("mediaUrl").jsonPrimitive.content)
        assertEquals("image/jpeg", payload.getValue("mimeType").jsonPrimitive.content)
        assertFalse(outbox.requestJson.contains("api", ignoreCase = true))
    }

    @Test
    fun voiceMediaOutboxStoresDurationSecondsRoundedUp() {
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")
        val voice = FloatingChatMessage(
            id = "voice-1",
            type = FloatingChatMessageType.Voice,
            text = "voice",
            fromMe = true,
            senderName = "me",
            time = "now",
            resourceUrl = "content://media/audio/1",
            mediaDurationMs = 3_200,
            mediaMimeType = "audio/mp4"
        ).withScrmQueueState("voice-request")

        val outbox = scrmMediaOutboxItemForMessage(voice, route, now = 2_000L)

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals("message.voice", outbox.operationType)
        assertEquals("4", payload.getValue("durationSeconds").jsonPrimitive.content)
    }

    @Test
    fun filePreviewOutboxUsesFileOperationAndDocumentPayload() {
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")
        val file = FloatingChatMessage(
            id = "file-1",
            type = FloatingChatMessageType.FilePreview,
            text = "quote.pdf",
            fromMe = true,
            senderName = "me",
            time = "now",
            resourceUrl = "content://documents/quote",
            fileName = "quote.pdf",
            mediaMimeType = "application/pdf"
        ).withScrmQueueState("file-request")

        val outbox = scrmMediaOutboxItemForMessage(file, route, now = 2_000L)

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals("message.file", outbox.operationType)
        assertEquals("content://documents/quote", payload.getValue("mediaUrl").jsonPrimitive.content)
        assertEquals("quote.pdf", payload.getValue("fileName").jsonPrimitive.content)
        assertEquals("application/pdf", payload.getValue("mimeType").jsonPrimitive.content)
    }

    @Test
    fun linkCardOutboxUsesCardFieldsFromSpecialMessage() {
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")
        val card = FloatingChatMessage(
            id = "card-1",
            type = FloatingChatMessageType.MiniProgramLink,
            text = "订单详情",
            fromMe = true,
            senderName = "me",
            time = "now",
            detail = "点击查看报价",
            appName = "销售系统",
            resourceUrl = "https://example.com/order/1",
            thumbnailUrl = "https://example.com/thumb.jpg"
        ).withScrmQueueState("card-request")

        val outbox = scrmOutboxItemForMessage(card, route, now = 2_000L)

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals("message.link-card", outbox.operationType)
        assertEquals("https://example.com/order/1", payload.getValue("url").jsonPrimitive.content)
        assertEquals("订单详情", payload.getValue("title").jsonPrimitive.content)
        assertEquals("点击查看报价", payload.getValue("description").jsonPrimitive.content)
        assertEquals("销售系统", payload.getValue("sourceName").jsonPrimitive.content)
        assertEquals("https://example.com/thumb.jpg", payload.getValue("thumb").jsonPrimitive.content)
    }

    @Test
    fun quoteOutboxCarriesQuotedRemoteMessageServerId() {
        val route = ScrmTextMessageRoute("device-1", "wxid_account", "wxid_friend")
        val quote = FloatingChatMessage(
            id = "quote-1",
            type = FloatingChatMessageType.Quote,
            text = "这条我引用回复",
            fromMe = true,
            senderName = "me",
            time = "now",
            quoteAuthor = "Alice",
            quoteText = "原消息",
            remoteMessageServerId = "88990011"
        ).withScrmQueueState("quote-request")

        val outbox = scrmOutboxItemForMessage(quote, route, now = 2_000L)

        val payload = Json.parseToJsonElement(outbox.requestJson).jsonObject
        assertEquals("message.quote", outbox.operationType)
        assertEquals("这条我引用回复", payload.getValue("content").jsonPrimitive.content)
        assertEquals("88990011", payload.getValue("quoteMsgSvrId").jsonPrimitive.content)
    }

    @Test
    fun quotePreflightRequiresQuotedRemoteMessageServerId() {
        val quote = FloatingChatMessage(
            id = "quote-1",
            type = FloatingChatMessageType.Quote,
            text = "reply",
            fromMe = true,
            senderName = "me",
            time = "now",
            quoteAuthor = "Alice",
            quoteText = "source"
        )

        val failure = scrmMessagePreflightFailure(quote)

        assertEquals("MissingQuoteMsgSvrId", failure?.code)
        assertEquals(FloatingChatSendState.FailedFinal, quote.withScrmFailureState(failure!!).sendState)
        assertNull(scrmMessagePreflightFailure(quote.copy(remoteMessageServerId = "88990011")))
    }

    @Test
    fun routeUsesFloatingMessageAccountBeforeSavedDefaultAccount() {
        val summary = ScrmSettingsSummary(
            isConfigured = true,
            baseUrl = "https://api.example.com/openapi/v1",
            maskedApiKey = "****1234",
            selectedDeviceUuid = "default-device",
            selectedWeChatId = "wxid_default"
        )
        val accountId = scrmFloatingAccountId(
            deviceUuid = "device-2",
            weChatId = "wxid_account_2"
        )
        val contactId = scrmFloatingContactId("wxid_friend_2")
        val scopedContactId = "${accountId}__${contactId}"
        val message = FloatingChatMessage(
            id = "message-2",
            type = FloatingChatMessageType.Text,
            text = "hello",
            fromMe = true,
            senderName = "Backup WeChat",
            time = "now",
            connectionTargetId = accountId,
            threadContactId = scopedContactId
        )

        val route = scrmTextRouteForMessageThread(
            summary = summary,
            message = message,
            threadId = "private:$scopedContactId"
        )

        assertEquals(
            ScrmTextMessageRoute(
                deviceUuid = "device-2",
                weChatId = "wxid_account_2",
                conversationId = "wxid_friend_2"
            ),
            route
        )
    }
}
