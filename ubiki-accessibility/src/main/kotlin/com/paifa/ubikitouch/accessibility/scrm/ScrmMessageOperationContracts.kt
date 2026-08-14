package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 悬浮聊天消息扩展契约。除 getCardTemplates 外均会下发 Android 任务或发送消息。
 * 当前仅定义接口，待消息菜单、更多面板或调试面板 UI 对接；不得后台自动执行。
 */
internal interface ScrmMessageOperationApi {
    fun getCardTemplates(query: ScrmCardTemplateQuery): ScrmCardTemplateResponse
    fun sendEmoji(request: ScrmSendEmojiRequest): ScrmTaskSubmissionResult
    fun sendWeAppCard(request: ScrmSendWeAppCardRequest): ScrmTaskSubmissionResult
    fun sendBatch(request: ScrmBatchSendMessageRequest): ScrmBatchSendMessageResponse
    fun sendBatchByFilter(
        request: ScrmBatchSendMessageByFilterRequest
    ): ScrmBatchSendMessageResponse

    fun syncConversationUnread(request: ScrmConversationMessageStateRequest): ScrmTaskSubmissionResult
    fun syncHistory(request: ScrmSyncHistoryMessagesRequest): ScrmTaskSubmissionResult
    fun syncMessageIds(request: ScrmSyncMessageIdsRequest): ScrmTaskSubmissionResult
    fun syncReadState(request: ScrmConversationMessageStateRequest): ScrmTaskSubmissionResult
    fun syncUnreadList(request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult
    fun clearAllChatMessages(request: ScrmClearAllChatMessagesRequest): ScrmTaskSubmissionResult
    fun forwardMessages(
        request: ScrmForwardMessagesRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult

    fun pullEmojiDetail(messageId: Long, request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult
    fun forwardMessage(
        messageId: Long,
        request: ScrmForwardMessageRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult

    fun downloadMessageMedia(
        messageId: Long,
        request: ScrmMessageMediaDownloadRequest
    ): ScrmTaskSubmissionResult

    fun pullMessageDetail(
        messageId: Long,
        request: ScrmMessageDetailPullRequest
    ): ScrmTaskSubmissionResult

    fun pullMessageOriginal(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult

    fun revokeMessage(messageId: Long, request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult
    fun transcribeVoiceMessage(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult
}

internal data class ScrmCardTemplateQuery(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val conversationId: String? = null,
    val thumbUrl: String? = null
)

@Serializable
internal data class ScrmCardTemplateResponse(
    val success: Boolean = false,
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val conversationId: String? = null,
    val thumbUrl: String? = null,
    val thumbUploadCurl: String? = null,
    val nextStep: String? = null,
    val items: List<ScrmCardTemplateItem> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
internal data class ScrmCardTemplateItem(
    val kind: String? = null,
    val route: String? = null,
    val title: String? = null,
    val description: String? = null,
    val payload: JsonElement? = null,
    val payloadJson: String? = null,
    val curlExample: String? = null,
    val requiredFields: JsonElement? = null,
    val notes: List<String> = emptyList()
)

/** 待表情面板 UI 对接；发送操作禁止自动化测试。 */
@Serializable
internal data class ScrmSendEmojiRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val md5: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(md5.isNotBlank()) { "md5 cannot be blank" }
    }
}

/** 小程序卡片接口；url、缩略图必须是测试手机可访问的真实链接。 */
@Serializable
internal data class ScrmSendWeAppCardRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val appId: String,
    val title: String,
    val pagePath: String,
    val url: String,
    val thumb: String,
    val icon: String? = null,
    val source: String? = null,
    val sourceName: String? = null,
    val sourceUsername: String? = null,
    val version: Int = 0,
    val disForward: Boolean = false
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(appId.isNotBlank()) { "appId cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(pagePath.isNotBlank()) { "pagePath cannot be blank" }
        require(url.isNotBlank()) { "url cannot be blank" }
        require(thumb.isNotBlank()) { "thumb cannot be blank" }
        require(!appId.equals("wx_demo_appid", ignoreCase = true)) { "appId cannot be a demo value" }
        require(!url.contains("cc2.cx", ignoreCase = true) && !url.contains("example.com", ignoreCase = true)) {
            "url cannot be a demo value"
        }
    }
}

/**
 * 批量发送属于高风险写操作。待独立批量发送 UI 对接，必须展示目标预览、数量与内容，
 * 首次人工测试只允许一个测试会话；不得从悬浮聊天后台自动调用。
 */
@Serializable
internal data class ScrmBatchSendMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationIds: List<String>,
    val messageType: String,
    val content: String? = null,
    val voiceUrl: String? = null,
    val durationSeconds: Int? = null,
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val thumb: String? = null,
    val recordItem: String? = null,
    val appId: String? = null,
    val sourceName: String? = null,
    val sourceUsername: String? = null,
    val source: String? = null,
    val pagePath: String? = null,
    val icon: String? = null,
    val version: Int? = null,
    val disForward: Boolean? = null
) {
    init {
        require(conversationIds.isNotEmpty()) { "conversationIds cannot be empty" }
        require(conversationIds.all { it.isNotBlank() }) {
            "conversationIds cannot contain blank values"
        }
        validateBatchMessageType(messageType)
        require(durationSeconds == null || durationSeconds in 1..59) {
            "durationSeconds must be between 1 and 59"
        }
    }
}

/** 按筛选批量发送风险更高，UI 必须先显示命中预览并把 maxCount 默认设为 1。 */
@Serializable
internal data class ScrmBatchSendMessageByFilterRequest(
    val deviceUuid: String,
    val weChatId: String,
    val messageType: String,
    val content: String? = null,
    val voiceUrl: String? = null,
    val durationSeconds: Int? = null,
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val thumb: String? = null,
    val recordItem: String? = null,
    val appId: String? = null,
    val sourceName: String? = null,
    val sourceUsername: String? = null,
    val source: String? = null,
    val pagePath: String? = null,
    val icon: String? = null,
    val version: Int? = null,
    val disForward: Boolean? = null,
    val search: String? = null,
    val filterLabelIds: List<Int> = emptyList(),
    val filterLabelNames: List<String> = emptyList(),
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null,
    val maxCount: Int = 1
) {
    init {
        validateBatchMessageType(messageType)
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
        require(filterLabelIds.all { it > 0 }) { "filterLabelIds must contain positive values" }
        require(filterLabelNames.all { it.isNotBlank() }) {
            "filterLabelNames cannot contain blank values"
        }
    }
}

@Serializable
internal data class ScrmBatchSendMessageResponse(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val messageType: String? = null,
    val requestedCount: Int = 0,
    val acceptedCount: Int = 0,
    val successCount: Int = 0,
    val unknownCount: Int = 0,
    val failCount: Int = 0,
    val items: List<ScrmBatchTaskItemResult> = emptyList()
)

@Serializable
internal data class ScrmMessageOperationRequest(
    val deviceUuid: String,
    val weChatId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

@Serializable
internal data class ScrmConversationMessageStateRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
    }
}

@Serializable
internal data class ScrmSyncHistoryMessagesRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String? = null,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val flag: Int = 0,
    val count: Int = 50
) {
    init {
        require(startTime >= 0L && endTime >= 0L) { "time values cannot be negative" }
        require(count in 1..200) { "count must be between 1 and 200" }
    }
}

@Serializable
internal data class ScrmSyncMessageIdsRequest(
    val deviceUuid: String,
    val weChatId: String,
    val startTime: Long,
    val endTime: Long
) {
    init {
        require(startTime >= 0L) { "startTime cannot be negative" }
        require(endTime >= startTime) { "endTime must not be earlier than startTime" }
        require(endTime - startTime <= 10 * 60 * 1_000L) { "message ID sync window cannot exceed 10 minutes" }
    }
}

/** 清空微信本地全部聊天记录不可撤销；待独立危险操作 UI，必须输入式确认。 */
@Serializable
internal data class ScrmClearAllChatMessagesRequest(
    val deviceUuid: String,
    val weChatId: String,
    val flag: Int = 0,
    val confirmClearAllChatMessages: Boolean
) {
    init {
        require(confirmClearAllChatMessages) { "confirmClearAllChatMessages must be true" }
    }
}

/** 多消息转发要求同一原会话，UI 最多选择 50 条消息和 20 个目标。 */
@Serializable
internal data class ScrmForwardMessagesRequest(
    val deviceUuid: String,
    val weChatId: String,
    val messageIds: List<Long>,
    val targetConversationIds: List<String>,
    val sendRecord: Boolean = false
) {
    init {
        require(messageIds.isNotEmpty() && messageIds.size <= 50) { "messageIds size must be 1..50" }
        require(messageIds.all { it > 0L }) { "messageIds must contain positive values" }
        require(targetConversationIds.isNotEmpty() && targetConversationIds.size <= 20) {
            "targetConversationIds size must be 1..20"
        }
    }
}

@Serializable
internal data class ScrmForwardMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val targetConversationId: String
) {
    init {
        require(targetConversationId.isNotBlank()) { "targetConversationId cannot be blank" }
    }
}

@Serializable
internal data class ScrmMessageMediaDownloadRequest(
    val deviceUuid: String,
    val weChatId: String,
    val mediaId: Int? = null,
    val extensionId: Int? = null
) {
    init {
        require(mediaId == null || mediaId > 0) { "mediaId must be greater than 0" }
        require(extensionId == null || extensionId > 0) { "extensionId must be greater than 0" }
        require(mediaId == null || extensionId == null) { "mediaId and extensionId cannot both be set" }
    }
}

@Serializable
internal data class ScrmMessageDetailPullRequest(
    val deviceUuid: String,
    val weChatId: String,
    val getOriginal: Boolean = false
)

private fun validateBatchMessageType(messageType: String) {
    require(
        messageType in setOf(
            "text",
            "voice",
            "link-card",
            "official-article-card",
            "note-card",
            "weapp-card"
        )
    ) { "unsupported batch messageType" }
}
