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

internal data class ScrmMessagePreflightFailure(
    val code: String,
    val message: String
) {
    init {
        require(code.isNotBlank()) { "code cannot be blank" }
        require(message.isNotBlank()) { "message cannot be blank" }
    }
}

@Serializable
internal data class ScrmQueuedMediaPayload(
    val mediaUrl: String,
    val mimeType: String? = null,
    val fileName: String? = null,
    val durationSeconds: Int? = null
) {
    init {
        require(mediaUrl.isNotBlank()) { "mediaUrl cannot be blank" }
        require(mimeType == null || mimeType.isNotBlank()) { "mimeType cannot be blank" }
        require(fileName == null || fileName.isNotBlank()) { "fileName cannot be blank" }
        require(durationSeconds == null || durationSeconds > 0) {
            "durationSeconds must be greater than 0"
        }
    }
}

@Serializable
internal data class ScrmQueuedLinkCardPayload(
    val url: String,
    val title: String,
    val description: String? = null,
    val thumb: String? = null,
    val appId: String? = null,
    val sourceName: String? = null,
    val source: String? = null
) {
    init {
        require(url.isNotBlank()) { "url cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(description == null || description.isNotBlank()) { "description cannot be blank" }
        require(thumb == null || thumb.isNotBlank()) { "thumb cannot be blank" }
        require(appId == null || appId.isNotBlank()) { "appId cannot be blank" }
        require(sourceName == null || sourceName.isNotBlank()) { "sourceName cannot be blank" }
        require(source == null || source.isNotBlank()) { "source cannot be blank" }
    }
}

@Serializable
internal data class ScrmQueuedNoteCardPayload(
    val title: String,
    val description: String? = null,
    val thumb: String? = null,
    val recordItem: String
) {
    init {
        require(title.isNotBlank()) { "title cannot be blank" }
        require(description == null || description.isNotBlank()) { "description cannot be blank" }
        require(thumb == null || thumb.isNotBlank()) { "thumb cannot be blank" }
        require(recordItem.isNotBlank()) { "recordItem cannot be blank" }
    }
}

@Serializable
internal data class ScrmQueuedQuotePayload(
    val content: String,
    val quoteMsgSvrId: Long? = null
) {
    init {
        require(content.isNotBlank()) { "content cannot be blank" }
        require(quoteMsgSvrId == null || quoteMsgSvrId > 0L) {
            "quoteMsgSvrId must be greater than 0"
        }
    }
}

private fun scrmMediaFileNameFromUrl(mediaUrl: String): String? {
    return mediaUrl
        .substringBefore('?')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() && it.contains('.') }
}

private const val ScrmAccountScopedThreadSeparator = "__"

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

internal fun scrmTextRouteForMessageThread(
    summary: ScrmSettingsSummary,
    message: FloatingChatMessage,
    threadId: String
): ScrmTextMessageRoute? {
    if (!summary.isConfigured) return null
    val conversationId = remoteConversationIdForThread(
        threadId = threadId,
        scopedAccountId = message.connectionTargetId
    ) ?: return null
    val messageAccountRoute = message.connectionTargetId
        ?.let(::scrmFloatingAccountRouteForContactId)
    if (messageAccountRoute != null) {
        return ScrmTextMessageRoute(
            deviceUuid = messageAccountRoute.deviceUuid,
            weChatId = messageAccountRoute.weChatId,
            conversationId = conversationId
        )
    }
    return scrmTextRouteForThread(summary, threadId)
}

internal fun FloatingChatMessage.withScrmQueueState(
    clientRequestId: String
): FloatingChatMessage {
    require(clientRequestId.isNotBlank()) { "clientRequestId 不能为空" }
    return copy(
        sendState = FloatingChatSendState.Queued,
        sendErrorCode = null,
        sendErrorMessage = null,
        clientRequestId = clientRequestId
    )
}

internal fun FloatingChatMessage.withScrmFailureState(
    failure: ScrmMessagePreflightFailure
): FloatingChatMessage {
    return copy(
        sendState = FloatingChatSendState.FailedFinal,
        sendErrorCode = failure.code,
        sendErrorMessage = failure.message,
        clientRequestId = null
    )
}

internal fun scrmMessagePreflightFailure(
    message: FloatingChatMessage
): ScrmMessagePreflightFailure? {
    if (message.type == FloatingChatMessageType.Quote && message.quoteMsgSvrId() == null) {
        return ScrmMessagePreflightFailure(
            code = "MissingQuoteMsgSvrId",
            message = "被引用消息缺少服务端消息 ID，无法调用真实引用接口"
        )
    }
    return null
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

internal fun scrmMediaOutboxItemForMessage(
    message: FloatingChatMessage,
    route: ScrmTextMessageRoute,
    now: Long
): ScrmOutboxItem {
    val operationType = scrmMediaOperationType(message.type)
        ?: throw IllegalArgumentException("Unsupported SCRM media message type")
    require(message.fromMe) { "Only outgoing media messages can enter SCRM outbox" }
    val mediaUrl = scrmMediaUrlForMessage(message)
        ?: throw IllegalArgumentException("SCRM media message missing resourceUrl")
    val clientRequestId = message.clientRequestId
        ?: throw IllegalArgumentException("Media message missing clientRequestId")
    return ScrmOutboxItem(
        outboxId = "outbox-$clientRequestId",
        operationType = operationType,
        aggregateType = "message",
        aggregateId = message.id,
        accountWeChatId = route.weChatId,
        deviceUuid = route.deviceUuid,
        conversationId = route.conversationId,
        clientRequestId = clientRequestId,
        requestJson = ScrmTextOutboxJson.encodeToString(
            ScrmQueuedMediaPayload(
                mediaUrl = mediaUrl,
                mimeType = message.mediaMimeType?.takeIf { it.isNotBlank() },
                fileName = message.fileName?.takeIf { it.isNotBlank() }
                    ?: scrmMediaFileNameFromUrl(mediaUrl),
                durationSeconds = message.mediaDurationMs
                    ?.let { durationMs -> ((durationMs.coerceAtLeast(1) + 999) / 1000).coerceAtLeast(1) }
                    ?.takeIf { operationType == "message.voice" }
            )
        ),
        state = ScrmOutboxState.Queued,
        createdAt = now,
        updatedAt = now
    )
}

internal fun scrmOutboxItemForMessage(
    message: FloatingChatMessage,
    route: ScrmTextMessageRoute,
    now: Long
): ScrmOutboxItem {
    return when (scrmMessageOperationType(message)) {
        "message.text" -> scrmTextOutboxItemForMessage(message, route, now)
        "message.image",
        "message.video",
        "message.voice",
        "message.file" -> scrmMediaOutboxItemForMessage(message, route, now)
        "message.link-card",
        "message.quote" -> scrmCardOutboxItemForMessage(message, route, now)
        else -> throw IllegalArgumentException("Unsupported SCRM message type")
    }
}

internal fun scrmCardOutboxItemForMessage(
    message: FloatingChatMessage,
    route: ScrmTextMessageRoute,
    now: Long
): ScrmOutboxItem {
    val operationType = scrmMessageOperationType(message)
        ?: throw IllegalArgumentException("Unsupported SCRM card message type")
    require(operationType == "message.link-card" || operationType == "message.quote") {
        "Unsupported SCRM card message operation"
    }
    require(message.fromMe) { "Only outgoing card messages can enter SCRM outbox" }
    val clientRequestId = message.clientRequestId
        ?: throw IllegalArgumentException("Card message missing clientRequestId")
    return ScrmOutboxItem(
        outboxId = "outbox-$clientRequestId",
        operationType = operationType,
        aggregateType = "message",
        aggregateId = message.id,
        accountWeChatId = route.weChatId,
        deviceUuid = route.deviceUuid,
        conversationId = route.conversationId,
        clientRequestId = clientRequestId,
        requestJson = when (operationType) {
            "message.link-card" -> ScrmTextOutboxJson.encodeToString(
                linkCardPayloadForMessage(message)
            )
            "message.quote" -> ScrmTextOutboxJson.encodeToString(
                ScrmQueuedQuotePayload(
                    content = message.text.trim(),
                    quoteMsgSvrId = message.quoteMsgSvrId()
                )
            )
            else -> error("Unsupported SCRM card message operation")
        },
        state = ScrmOutboxState.Queued,
        createdAt = now,
        updatedAt = now
    )
}

private fun FloatingChatMessage.quoteMsgSvrId(): Long? {
    return remoteMessageServerId
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
}

private fun linkCardPayloadForMessage(message: FloatingChatMessage): ScrmQueuedLinkCardPayload {
    val url = message.resourceUrl?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Link card message missing resourceUrl")
    val title = when (message.type) {
        FloatingChatMessageType.ContactLink -> message.cardName
        FloatingChatMessageType.MiniProgramLink -> message.text
        else -> message.text
    }?.takeIf { it.isNotBlank() }
        ?: message.text.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Link card message missing title")
    return ScrmQueuedLinkCardPayload(
        url = url,
        title = title,
        description = when (message.type) {
            FloatingChatMessageType.ContactLink -> message.cardSubtitle
            FloatingChatMessageType.MiniProgramLink -> message.detail
            else -> message.detail
        }?.takeIf { it.isNotBlank() },
        thumb = message.thumbnailUrl?.takeIf { it.isNotBlank() },
        sourceName = message.appName?.takeIf { it.isNotBlank() }
    )
}

internal fun scrmMessageOperationType(message: FloatingChatMessage): String? {
    return when (message.type) {
        FloatingChatMessageType.Text -> "message.text"
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.MiniProgramLink -> "message.link-card"
        FloatingChatMessageType.Quote -> "message.quote"
        else -> scrmMediaOperationType(message.type)
    }
}

internal fun scrmMediaOperationType(type: FloatingChatMessageType): String? {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> "message.image"
        FloatingChatMessageType.VideoPreview -> "message.video"
        FloatingChatMessageType.Voice -> "message.voice"
        FloatingChatMessageType.FilePreview -> "message.file"
        else -> null
    }
}

private fun scrmMediaUrlForMessage(message: FloatingChatMessage): String? {
    return when (message.type) {
        FloatingChatMessageType.ImageThumbnail -> {
            message.resourceUrl?.takeIf { it.isNotBlank() }
                ?: message.thumbnailUrl?.takeIf { it.isNotBlank() }
        }
        FloatingChatMessageType.VideoPreview,
        FloatingChatMessageType.Voice,
        FloatingChatMessageType.FilePreview -> message.resourceUrl?.takeIf { it.isNotBlank() }
        else -> null
    }
}

private fun remoteConversationIdForThread(
    threadId: String,
    scopedAccountId: String? = null
): String? {
    val scopedConversationId = when {
        threadId.startsWith("private:") -> threadId.removePrefix("private:")
        threadId.startsWith("group:") -> threadId.removePrefix("group:")
        else -> return null
    }
    val conversationId = when {
        !scopedAccountId.isNullOrBlank() &&
            scopedConversationId.startsWith("$scopedAccountId$ScrmAccountScopedThreadSeparator") -> {
            scopedConversationId.removePrefix("$scopedAccountId$ScrmAccountScopedThreadSeparator")
        }
        scopedConversationId.contains(ScrmAccountScopedThreadSeparator) -> {
            scopedConversationId.substringAfter(ScrmAccountScopedThreadSeparator)
        }
        else -> scopedConversationId
    }
    scrmFloatingContactConversationId(conversationId)?.let { return it }
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
