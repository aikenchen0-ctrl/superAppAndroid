package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val ScrmTextOutboxJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
}

internal data class ScrmTextMessageRoute(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(conversationId.isNotBlank()) { "conversationId 不能为空" }
    }
}

internal fun scrmTextRouteForThread(
    summary: ScrmSettingsSummary,
    threadId: String
): ScrmTextMessageRoute? {
    if (!summary.isConfigured) return null
    val deviceUuid = summary.selectedDeviceUuid?.takeIf { it.isNotBlank() } ?: return null
    val weChatId = summary.selectedWeChatId?.takeIf { it.isNotBlank() } ?: return null
    val conversationId = remoteConversationIdForThread(threadId) ?: return null
    return ScrmTextMessageRoute(
        deviceUuid = deviceUuid,
        weChatId = weChatId,
        conversationId = conversationId
    )
}

internal fun FloatingChatMessage.withScrmQueueState(
    clientRequestId: String
): FloatingChatMessage {
    require(clientRequestId.isNotBlank()) { "clientRequestId 不能为空" }
    return copy(
        sendState = FloatingChatSendState.Queued,
        clientRequestId = clientRequestId
    )
}

internal fun scrmTextOutboxItemForMessage(
    message: FloatingChatMessage,
    route: ScrmTextMessageRoute,
    now: Long
): ScrmOutboxItem {
    require(message.type == FloatingChatMessageType.Text) { "只支持文本消息入 SCRM 文本队列" }
    require(message.text.isNotBlank()) { "消息内容不能为空" }
    val clientRequestId = message.clientRequestId
        ?: throw IllegalArgumentException("文本消息缺少 clientRequestId")
    return ScrmOutboxItem(
        outboxId = "outbox-$clientRequestId",
        operationType = "message.text",
        aggregateType = "message",
        aggregateId = message.id,
        accountWeChatId = route.weChatId,
        deviceUuid = route.deviceUuid,
        conversationId = route.conversationId,
        clientRequestId = clientRequestId,
        requestJson = ScrmTextOutboxJson.encodeToString(
            ScrmQueuedTextPayload(content = message.text)
        ),
        state = ScrmOutboxState.Queued,
        createdAt = now,
        updatedAt = now
    )
}

private fun remoteConversationIdForThread(threadId: String): String? {
    val conversationId = when {
        threadId.startsWith("private:") -> threadId.removePrefix("private:")
        threadId.startsWith("group:") -> threadId.removePrefix("group:")
        else -> return null
    }
    return conversationId.takeIf { it.startsWith("wxid_") || it.endsWith("@chatroom") }
}

@Serializable
internal data class ScrmQueuedTextPayload(
    val content: String,
    val atIds: String? = null
) {
    init {
        require(content.isNotBlank()) { "消息内容不能为空" }
        require(atIds == null || atIds.isNotBlank()) { "atIds 不能为空" }
    }
}
