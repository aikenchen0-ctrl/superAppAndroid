package com.paifa.ubikitouch.accessibility.scrm

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface ScrmOpenApiRawApi {
    fun requestRaw(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null
    ): JsonElement
}

internal sealed class ScrmException(message: String) : Exception(message)

internal class ScrmConfigurationException(message: String) : ScrmException(message)

internal sealed class ScrmHttpException(
    val statusCode: Int,
    message: String
) : ScrmException(message)

internal class ScrmAuthenticationException(message: String) : ScrmHttpException(401, message)

internal class ScrmPermissionException(message: String) : ScrmHttpException(403, message)

internal class ScrmRateLimitException(
    message: String,
    val retryAfterSeconds: Long?
) : ScrmHttpException(429, message)

internal class ScrmServerException(statusCode: Int, message: String) :
    ScrmHttpException(statusCode, message)

internal class ScrmRequestException(statusCode: Int, message: String) :
    ScrmHttpException(statusCode, message)

internal class ScrmInvalidResponseException(message: String) : ScrmException(message)

internal class ScrmTimeoutException : ScrmException("SCRM 请求超时")

internal class ScrmNetworkException : ScrmException("SCRM 网络请求失败")

internal class ScrmLocalMediaException(message: String) : ScrmException(message)

internal interface ScrmReadApi {
    fun getMe(): ScrmMe
    fun getDevices(): List<ScrmDevice>
    fun getWechatAccounts(): List<ScrmWechatAccount>
    fun getQuickStart(
        deviceUuid: String? = null,
        weChatId: String? = null,
        scope: String = "p0",
        includeBlocked: Boolean = true
    ): ScrmQuickStart

    fun getCapabilities(deviceUuid: String, weChatId: String): ScrmCapabilities
    fun getChatBootstrap(
        deviceUuid: String,
        weChatId: String,
        conversationLimit: Int = 300
    ): ScrmChatBootstrap

    fun getChatHistory(
        deviceUuid: String,
        weChatId: String,
        conversationWxid: String,
        conversationId: Long = 0L,
        cursor: String? = null,
        pageSize: Int = 50
    ): ScrmChatHistory

    fun getChatChanges(
        deviceUuid: String,
        weChatId: String,
        afterSequence: Long,
        limit: Int = 200
    ): ScrmChatChanges
}

internal interface ScrmTaskApi {
    fun getTask(taskId: Long): ScrmTaskResult
    fun getRecentTasks(deviceUuid: String? = null, count: Int = 20): ScrmRecentTaskResults
}

internal interface ScrmPaymentApi {
    fun sendLuckyMoney(request: ScrmSendLuckyMoneyRequest, idempotencyKey: String): ScrmTaskSubmissionResult
    fun takeLuckyMoney(request: ScrmTakeLuckyMoneyByMessageRequest, idempotencyKey: String): ScrmTaskSubmissionResult
    fun getRedPacketDetail(request: ScrmRedPacketQueryByMessageRequest): ScrmTaskSubmissionResult
    fun getRedPacketStatus(request: ScrmRedPacketQueryByMessageRequest): ScrmTaskSubmissionResult
    fun sendRemittance(request: ScrmSendRemittanceRequest, idempotencyKey: String): ScrmTaskSubmissionResult
    fun takeTransfer(request: ScrmTakeTransferByMessageRequest, idempotencyKey: String): ScrmTaskSubmissionResult
    fun getWalletBalance(request: ScrmWalletBalanceRequest): ScrmTaskSubmissionResult
}

internal interface ScrmMessageApi {
    fun sendText(request: ScrmSendTextMessageRequest): ScrmTaskSubmissionResult
    fun sendImage(request: ScrmSendImageMessageRequest): ScrmTaskSubmissionResult
    fun sendVideo(request: ScrmSendVideoMessageRequest): ScrmTaskSubmissionResult
    fun sendVoice(request: ScrmSendVoiceMessageRequest): ScrmTaskSubmissionResult
    fun sendFile(request: ScrmSendFileMessageRequest): ScrmTaskSubmissionResult
    fun sendLinkCard(request: ScrmSendLinkCardMessageRequest): ScrmTaskSubmissionResult
    fun sendNoteCard(request: ScrmSendNoteCardMessageRequest): ScrmTaskSubmissionResult
    fun sendOfficialArticleCard(request: ScrmSendLinkCardMessageRequest): ScrmTaskSubmissionResult
    fun sendQuote(request: ScrmSendQuoteMessageRequest): ScrmTaskSubmissionResult
    fun uploadMedia(request: ScrmMediaUploadRequest): ScrmMediaUploadResponse
    fun uploadVoice(request: ScrmMediaUploadRequest): ScrmVoiceUploadResponse
}

internal interface ScrmMomentApi {
    fun postMoment(request: ScrmPostMomentRequest): ScrmTaskSubmissionResult
    fun syncMoments(request: ScrmSyncMomentsRequest): ScrmTaskSubmissionResult
    fun syncMomentMessages(request: ScrmSyncMomentMessagesRequest): ScrmTaskSubmissionResult
    fun getMomentDetail(request: ScrmMomentDetailRequest): ScrmTaskSubmissionResult
    fun likeMoment(request: ScrmMomentLikeRequest): ScrmTaskSubmissionResult
    fun commentMoment(request: ScrmMomentCommentRequest): ScrmTaskSubmissionResult
    fun deleteMomentComment(request: ScrmMomentCommentDeleteRequest): ScrmTaskSubmissionResult
    fun getMomentMaterials(query: ScrmMomentMaterialQuery = ScrmMomentMaterialQuery()): List<ScrmMomentMaterial>
    fun createMomentMaterial(request: ScrmMomentMaterialCreateRequest): ScrmMomentMaterial
    fun getMomentMaterial(materialId: Long, tenantId: String? = null): ScrmMomentMaterial
    fun updateMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialUpdateRequest
    ): ScrmMomentMaterialDetail
    fun getMomentMaterialDetail(materialId: Long, tenantId: String? = null): ScrmMomentMaterialDetail
    fun copyMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialCopyRequest
    ): ScrmMomentMaterial
    fun copyMomentToFinderMaterial(
        snsId: Long,
        request: ScrmMomentCopyFinderMaterialRequest
    ): ScrmMomentCopyFinderMaterialResult
    fun archiveMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialControlRequest
    ): ScrmMomentMaterial
}

internal interface ScrmContactApi {
    fun getContacts(query: ScrmContactQuery = ScrmContactQuery()): ScrmContactPage
    fun getCustomerProfile(contactId: Int, weChatId: String): ScrmCustomerProfile
    fun getContactDetail(
        contactId: Int,
        commonChatRoomLimit: Int = 20,
        relationLogLimit: Int = 20
    ): ScrmContactDetail
    fun getCommonChatRooms(
        friendId: String,
        query: ScrmCommonChatRoomQuery = ScrmCommonChatRoomQuery()
    ): ScrmCommonChatRoomPage
    fun getContactLabels(
        weChatId: String? = null,
        includeDeleted: Boolean = false
    ): List<ScrmContactLabel>
    fun getContactWxids(
        query: ScrmContactWxidQuery = ScrmContactWxidQuery()
    ): ScrmContactWxidList
    fun saveCustomerProfile(
        contactId: Int,
        request: ScrmSaveCustomerProfileRequest
    ): ScrmCustomerProfile

    /**
     * 人工测试专用写接口：替换单个好友的完整标签集合，不做增量合并。
     * 测试前先用 GET /contact-labels 与 GET /contacts 记录目标原标签；空标签集合会清空标签。
     * 人工验收：在 Web 调试面板发起一次请求，再用 GET /contacts/{contactId}/detail 确认标签结果和任务状态。
     */
    fun setContactLabels(request: ScrmSetContactLabelsRequest): ScrmTaskSubmissionResult
    fun setFriendPermission(request: ScrmSetFriendPermissionRequest): ScrmTaskSubmissionResult

    /**
     * 人工测试专用写接口：批量追加或替换好友标签，每个好友会生成独立 Android 任务。
     * 首次测试只选一个测试好友，使用 mergeExisting=true、maxCount=1；严禁自动调用。
     * 验收时先记录原标签，再按单项 taskId 查询最终回包，最后用联系人详情接口回读标签。
     * 不得用 mergeExisting=false 或空标签集合测试生产联系人，以免覆盖或清空现有标签。
     */
    fun setContactLabelsBatch(
        request: ScrmBatchSetContactLabelsRequest
    ): ScrmBatchSetContactLabelsResponse

    fun syncContacts(request: ScrmSyncContactsRequest): ScrmTaskSubmissionResult
    fun addFriend(request: ScrmAddFriendRequest): ScrmTaskSubmissionResult
    fun findFriend(request: ScrmFindContactRequest): ScrmTaskSubmissionResult
    fun addFriendsByPhone(request: ScrmAddFriendsByPhoneRequest): ScrmTaskSubmissionResult
    fun sendFriendVerify(request: ScrmSendFriendVerifyRequest): ScrmTaskSubmissionResult
    fun deleteFriend(
        friendId: String,
        deviceUuid: String,
        weChatId: String
    ): ScrmTaskSubmissionResult
    fun getFriendRequests(
        weChatId: String? = null,
        count: Int = 50,
        pendingOnly: Boolean = false
    ): List<ScrmFriendRequest>
    fun pullFriendRequests(request: ScrmPullFriendRequestsRequest): ScrmTaskSubmissionResult
    fun handleFriendRequest(request: ScrmHandleFriendRequestRequest): ScrmTaskSubmissionResult
}

internal interface ScrmChatRoomApi {
    fun getGroupInvitations(query: ScrmGroupInvitationQuery = ScrmGroupInvitationQuery()): List<ScrmGroupInvitation>
    fun getChatRooms(query: ScrmChatRoomQuery = ScrmChatRoomQuery()): ScrmChatRoomPage
    fun getChatRoomMembers(
        chatRoomId: String,
        query: ScrmChatRoomMemberQuery = ScrmChatRoomMemberQuery()
    ): ScrmChatRoomMemberPage
    fun createChatRoom(request: ScrmCreateChatRoomRequest): ScrmTaskSubmissionResult
    fun syncChatRooms(request: ScrmSyncChatRoomsRequest): ScrmTaskSubmissionResult
    fun refreshChatRoom(request: ScrmRefreshChatRoomRequest): ScrmTaskSubmissionResult
    fun inviteChatRoomMembers(request: ScrmChatRoomMemberMutationRequest): ScrmTaskSubmissionResult
    fun kickChatRoomMembers(request: ScrmChatRoomMemberMutationRequest): ScrmTaskSubmissionResult
    fun renameChatRoom(request: ScrmRenameChatRoomRequest): ScrmTaskSubmissionResult
    fun setChatRoomNotice(request: ScrmSetChatRoomNoticeRequest): ScrmTaskSubmissionResult
    fun pullChatRoomQrCode(request: ScrmChatRoomActionRequest): ScrmTaskSubmissionResult
    fun exitChatRoom(request: ScrmChatRoomActionRequest): ScrmTaskSubmissionResult
}

internal class ScrmApiClient(
    private val config: ScrmApiConfig,
    private val transport: ScrmHttpTransport = HttpUrlConnectionScrmTransport(
        connectTimeoutMillis = config.connectTimeoutMillis,
        readTimeoutMillis = config.readTimeoutMillis
    ),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
) : ScrmReadApi,
    ScrmTaskApi,
    ScrmPaymentApi,
    ScrmMessageApi,
    ScrmMessageOperationApi,
    ScrmMomentApi,
    ScrmContactApi,
    ScrmContactManagementApi,
    ScrmChatRoomApi,
    ScrmOpenApiRawApi,
    ScrmChatRoomManagementApi {
    override fun requestRaw(
        method: String,
        path: String,
        query: Map<String, String?>,
        body: String?
    ): JsonElement {
        require(method in setOf("GET", "POST", "PUT", "DELETE")) { "不支持的 OpenAPI 方法: $method" }
        require(path.startsWith("/openapi/v1/") || path.startsWith("/openapi/docs/")) {
            "OpenAPI 路径必须以 /openapi/v1/ 或 /openapi/docs/ 开头"
        }
        val relativePath = path.removePrefix("/openapi/v1/")
        return executeRequest(
            method = method,
            path = relativePath,
            query = query,
            headers = authenticatedJsonHeaders(hasBody = body != null),
            body = body,
            bodyBytes = null,
            safeRoute = path,
            urlOverride = config.openApiEndpoint(path, query)
        )
    }
    override fun getMe(): ScrmMe = get("me")

    override fun getDevices(): List<ScrmDevice> = get("devices")

    override fun getWechatAccounts(): List<ScrmWechatAccount> = get("wechat-accounts")

    override fun getGroupInvitations(query: ScrmGroupInvitationQuery): List<ScrmGroupInvitation> {
        return get(
            path = "group-invitations",
            query = linkedMapOf(
                "weChatId" to query.weChatId,
                "chatRoomId" to query.chatRoomId,
                "count" to query.count.toString(),
                "pendingOnly" to query.pendingOnly.toString()
            )
        )
    }

    override fun getQuickStart(
        deviceUuid: String?,
        weChatId: String?,
        scope: String,
        includeBlocked: Boolean
    ): ScrmQuickStart {
        return get(
            path = "quick-start",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid?.takeIf { it.isNotBlank() },
                "weChatId" to weChatId?.takeIf { it.isNotBlank() },
                "scope" to scope,
                "includeBlocked" to includeBlocked.toString()
            )
        )
    }

    override fun getCapabilities(deviceUuid: String, weChatId: String): ScrmCapabilities {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        return get(
            path = "capabilities",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid,
                "weChatId" to weChatId
            )
        )
    }

    override fun getChatBootstrap(
        deviceUuid: String,
        weChatId: String,
        conversationLimit: Int
    ): ScrmChatBootstrap {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationLimit in 1..1_000) { "conversationLimit must be between 1 and 1000" }
        return get(
            path = "chat/bootstrap",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid,
                "weChatId" to weChatId,
                "conversationLimit" to conversationLimit.toString()
            )
        )
    }

    override fun getChatHistory(
        deviceUuid: String,
        weChatId: String,
        conversationWxid: String,
        conversationId: Long,
        cursor: String?,
        pageSize: Int
    ): ScrmChatHistory {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationWxid.isNotBlank()) { "conversationWxid cannot be blank" }
        require(conversationId >= 0L) { "conversationId cannot be negative" }
        require(pageSize in 1..200) { "pageSize must be between 1 and 200" }

        fun request(identifier: String): ScrmChatHistory {
            val query = linkedMapOf(
                "deviceUuid" to deviceUuid,
                "weChatId" to weChatId,
                "conversationId" to identifier,
                "pageSize" to pageSize.toString()
            )
            cursor?.takeIf { it.isNotBlank() }?.let { query["cursor"] = it }
            return get(path = "chat/history", query = query)
        }

        return try {
            request(conversationWxid)
        } catch (error: ScrmHttpException) {
            if (error.statusCode !in setOf(400, 404) || conversationId <= 0L) throw error
            request(conversationId.toString())
        }
    }

    override fun getChatChanges(
        deviceUuid: String,
        weChatId: String,
        afterSequence: Long,
        limit: Int
    ): ScrmChatChanges {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(afterSequence >= 0L) { "afterSequence cannot be negative" }
        require(limit in 1..500) { "limit must be between 1 and 500" }
        return get(
            path = "messages/changes",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid,
                "weChatId" to weChatId,
                "afterSequence" to afterSequence.toString(),
                "limit" to limit.toString()
            )
        )
    }

    override fun getTask(taskId: Long): ScrmTaskResult {
        require(taskId > 0) { "taskId 必须大于 0" }
        return get(
            path = "tasks/$taskId",
            safeRoute = "/openapi/v1/tasks/{taskId}"
        )
    }

    override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
        require(count > 0) { "count 必须大于 0" }
        return get(
            path = "tasks/recent",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid?.takeIf { it.isNotBlank() },
                "count" to count.toString()
            ),
            safeRoute = "/openapi/v1/tasks/recent"
        )
    }

    override fun sendLuckyMoney(
        request: ScrmSendLuckyMoneyRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult = postIdempotent(
        path = "payments/lucky-money",
        body = json.encodeToString(request),
        idempotencyKey = idempotencyKey
    )

    override fun takeLuckyMoney(
        request: ScrmTakeLuckyMoneyByMessageRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult = postIdempotent(
        path = "payments/lucky-money/take-by-message",
        body = json.encodeToString(request),
        idempotencyKey = idempotencyKey
    )

    override fun getRedPacketDetail(
        request: ScrmRedPacketQueryByMessageRequest
    ): ScrmTaskSubmissionResult = post(
        path = "payments/red-packets/detail-by-message",
        body = json.encodeToString(request)
    )

    override fun getRedPacketStatus(
        request: ScrmRedPacketQueryByMessageRequest
    ): ScrmTaskSubmissionResult = post(
        path = "payments/red-packets/status-by-message",
        body = json.encodeToString(request)
    )

    override fun sendRemittance(
        request: ScrmSendRemittanceRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult = postIdempotent(
        path = "payments/remittance",
        body = json.encodeToString(request),
        idempotencyKey = idempotencyKey
    )

    override fun takeTransfer(
        request: ScrmTakeTransferByMessageRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult = postIdempotent(
        path = "payments/transfers/take-by-message",
        body = json.encodeToString(request),
        idempotencyKey = idempotencyKey
    )

    override fun getWalletBalance(
        request: ScrmWalletBalanceRequest
    ): ScrmTaskSubmissionResult = post(
        path = "payments/wallet-balance",
        body = json.encodeToString(request)
    )

    override fun sendText(request: ScrmSendTextMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/text",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/text"
        )
    }

    override fun sendImage(request: ScrmSendImageMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/image",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/image"
        )
    }

    override fun sendVideo(request: ScrmSendVideoMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/video",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/video"
        )
    }

    override fun sendVoice(request: ScrmSendVoiceMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/voice",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/voice"
        )
    }

    override fun sendFile(request: ScrmSendFileMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/file",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/file"
        )
    }

    override fun sendLinkCard(request: ScrmSendLinkCardMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/link-card",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/link-card"
        )
    }

    override fun sendNoteCard(request: ScrmSendNoteCardMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/note-card",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/note-card"
        )
    }

    override fun sendOfficialArticleCard(
        request: ScrmSendLinkCardMessageRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "messages/official-article-card",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/official-article-card"
        )
    }

    override fun sendQuote(request: ScrmSendQuoteMessageRequest): ScrmTaskSubmissionResult {
        return post(
            path = "messages/quote",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/quote"
        )
    }

    override fun getCardTemplates(query: ScrmCardTemplateQuery): ScrmCardTemplateResponse {
        return get(
            path = "messages/card-templates",
            query = linkedMapOf(
                "deviceUuid" to query.deviceUuid,
                "weChatId" to query.weChatId,
                "conversationId" to query.conversationId,
                "thumbUrl" to query.thumbUrl
            )
        )
    }

    override fun sendEmoji(request: ScrmSendEmojiRequest): ScrmTaskSubmissionResult {
        return post(path = "messages/emoji", body = json.encodeToString(request))
    }

    override fun sendWeAppCard(request: ScrmSendWeAppCardRequest): ScrmTaskSubmissionResult {
        return post(path = "messages/weapp-card", body = json.encodeToString(request))
    }

    override fun sendBatch(request: ScrmBatchSendMessageRequest): ScrmBatchSendMessageResponse {
        return post(path = "messages/batch", body = json.encodeToString(request))
    }

    override fun sendBatchByFilter(
        request: ScrmBatchSendMessageByFilterRequest
    ): ScrmBatchSendMessageResponse {
        return post(path = "messages/batch-by-filter", body = json.encodeToString(request))
    }

    override fun syncConversationUnread(
        request: ScrmConversationMessageStateRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "messages/sync/conversation-unread",
            body = json.encodeToString(request)
        )
    }

    override fun syncHistory(request: ScrmSyncHistoryMessagesRequest): ScrmTaskSubmissionResult {
        return post(path = "messages/sync/history", body = json.encodeToString(request))
    }

    override fun syncMessageIds(request: ScrmSyncMessageIdsRequest): ScrmTaskSubmissionResult {
        return post(path = "messages/sync/ids", body = json.encodeToString(request))
    }

    override fun syncReadState(
        request: ScrmConversationMessageStateRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "messages/sync/read-state", body = json.encodeToString(request))
    }

    override fun syncUnreadList(request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult {
        return post(path = "messages/sync/unread-list", body = json.encodeToString(request))
    }

    override fun clearAllChatMessages(
        request: ScrmClearAllChatMessagesRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "messages/clear-all", body = json.encodeToString(request))
    }

    override fun forwardMessages(
        request: ScrmForwardMessagesRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult {
        return postIdempotent(
            path = "messages/forward",
            body = json.encodeToString(request),
            idempotencyKey = idempotencyKey
        )
    }

    override fun pullEmojiDetail(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult {
        return postMessageOperation(messageId, "emoji-detail", request)
    }

    override fun forwardMessage(
        messageId: Long,
        request: ScrmForwardMessageRequest,
        idempotencyKey: String
    ): ScrmTaskSubmissionResult {
        require(messageId > 0L) { "messageId must be greater than 0" }
        return postIdempotent(
            path = "messages/$messageId/forward",
            body = json.encodeToString(request),
            idempotencyKey = idempotencyKey,
            safeRoute = "/openapi/v1/messages/{messageId}/forward"
        )
    }

    override fun downloadMessageMedia(
        messageId: Long,
        request: ScrmMessageMediaDownloadRequest
    ): ScrmTaskSubmissionResult {
        require(messageId > 0L) { "messageId must be greater than 0" }
        return post(
            path = "messages/$messageId/media/download",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/{messageId}/media/download"
        )
    }

    override fun pullMessageDetail(
        messageId: Long,
        request: ScrmMessageDetailPullRequest
    ): ScrmTaskSubmissionResult {
        require(messageId > 0L) { "messageId must be greater than 0" }
        return post(
            path = "messages/$messageId/pull-detail",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/{messageId}/pull-detail"
        )
    }

    override fun pullMessageOriginal(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult {
        return postMessageOperation(messageId, "pull-original", request)
    }

    override fun revokeMessage(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult {
        return postMessageOperation(messageId, "revoke", request)
    }

    override fun transcribeVoiceMessage(
        messageId: Long,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult {
        return postMessageOperation(messageId, "voice-trans-text", request)
    }

    private fun postMessageOperation(
        messageId: Long,
        action: String,
        request: ScrmMessageOperationRequest
    ): ScrmTaskSubmissionResult {
        require(messageId > 0L) { "messageId must be greater than 0" }
        return post(
            path = "messages/$messageId/$action",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/messages/{messageId}/$action"
        )
    }

    override fun uploadMedia(request: ScrmMediaUploadRequest): ScrmMediaUploadResponse {
        return postMultipart(
            path = "media",
            request = request,
            safeRoute = "/openapi/v1/media"
        )
    }

    override fun uploadVoice(request: ScrmMediaUploadRequest): ScrmVoiceUploadResponse {
        return postMultipart(
            path = "media/voice",
            request = request,
            safeRoute = "/openapi/v1/media/voice"
        )
    }

    override fun postMoment(request: ScrmPostMomentRequest): ScrmTaskSubmissionResult {
        return post(
            path = "moments",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments"
        )
    }

    override fun syncMoments(request: ScrmSyncMomentsRequest): ScrmTaskSubmissionResult {
        return post(
            path = "moments/sync",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/sync"
        )
    }

    override fun syncMomentMessages(
        request: ScrmSyncMomentMessagesRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "moments/messages/sync",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/messages/sync"
        )
    }

    override fun getMomentDetail(request: ScrmMomentDetailRequest): ScrmTaskSubmissionResult {
        return post(
            path = "moments/detail",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/detail"
        )
    }

    override fun likeMoment(request: ScrmMomentLikeRequest): ScrmTaskSubmissionResult {
        return post(
            path = "moments/like",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/like"
        )
    }

    override fun commentMoment(request: ScrmMomentCommentRequest): ScrmTaskSubmissionResult {
        return post(
            path = "moments/comments",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/comments"
        )
    }

    override fun deleteMomentComment(
        request: ScrmMomentCommentDeleteRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "moments/comments/delete",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/comments/delete"
        )
    }

    override fun getMomentMaterials(query: ScrmMomentMaterialQuery): List<ScrmMomentMaterial> {
        return get(
            path = "moments/materials",
            query = linkedMapOf(
                "tenantId" to query.tenantId?.takeIf { it.isNotBlank() },
                "skip" to query.skip.toString(),
                "take" to query.take.toString()
            )
        )
    }

    override fun createMomentMaterial(
        request: ScrmMomentMaterialCreateRequest
    ): ScrmMomentMaterial {
        return post(
            path = "moments/materials",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/materials"
        )
    }

    override fun getMomentMaterial(materialId: Long, tenantId: String?): ScrmMomentMaterial {
        require(materialId > 0L) { "materialId must be greater than 0" }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId cannot be blank" }
        return get(
            path = "moments/materials/$materialId",
            query = linkedMapOf("tenantId" to tenantId?.takeIf { it.isNotBlank() }),
            safeRoute = "/openapi/v1/moments/materials/{materialId}"
        )
    }

    override fun updateMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialUpdateRequest
    ): ScrmMomentMaterialDetail {
        require(materialId > 0L) { "materialId must be greater than 0" }
        return put(
            path = "moments/materials/$materialId",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/materials/{materialId}"
        )
    }

    override fun getMomentMaterialDetail(
        materialId: Long,
        tenantId: String?
    ): ScrmMomentMaterialDetail {
        require(materialId > 0L) { "materialId must be greater than 0" }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId cannot be blank" }
        return get(
            path = "moments/materials/$materialId/detail",
            query = linkedMapOf("tenantId" to tenantId?.takeIf { it.isNotBlank() }),
            safeRoute = "/openapi/v1/moments/materials/{materialId}/detail"
        )
    }

    override fun copyMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialCopyRequest
    ): ScrmMomentMaterial {
        require(materialId > 0L) { "materialId must be greater than 0" }
        return post(
            path = "moments/materials/$materialId/copy",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/materials/{materialId}/copy"
        )
    }

    /**
     * 从已同步朋友圈创建视频号动态或直播素材草稿。
     * 测试流程：传入真实 snsId 与 preferredType=live，验证 POST 路由和响应中的 publishReady。
     */
    override fun copyMomentToFinderMaterial(
        snsId: Long,
        request: ScrmMomentCopyFinderMaterialRequest
    ): ScrmMomentCopyFinderMaterialResult {
        require(snsId > 0L) { "snsId must be greater than 0" }
        return post(
            path = "moments/$snsId/copy-finder-material",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/{snsId}/copy-finder-material"
        )
    }

    override fun archiveMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialControlRequest
    ): ScrmMomentMaterial {
        require(materialId > 0L) { "materialId must be greater than 0" }
        return post(
            path = "moments/materials/$materialId/archive",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/moments/materials/{materialId}/archive"
        )
    }

    override fun getContacts(query: ScrmContactQuery): ScrmContactPage {
        return get(
            path = "contacts",
            query = linkedMapOf(
                "weChatId" to query.weChatId?.takeIf { it.isNotBlank() },
                "page" to query.page.toString(),
                "pageSize" to query.pageSize.toString(),
                "search" to query.search?.takeIf { it.isNotBlank() },
                "includeDeleted" to query.includeDeleted.toString(),
                "onlyFriends" to query.onlyFriends.toString(),
                "includeProfile" to query.includeProfile.toString()
            )
        )
    }

    override fun getCustomerProfile(contactId: Int, weChatId: String): ScrmCustomerProfile {
        require(contactId > 0) { "contactId must be greater than 0" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        val response: JsonElement = get(
            path = "contacts/$contactId/customer-profile",
            query = linkedMapOf("weChatId" to weChatId),
            safeRoute = "/openapi/v1/contacts/{contactId}/customer-profile"
        )
        return decodeCustomerProfile(response)
    }

    override fun getContactDetail(
        contactId: Int,
        commonChatRoomLimit: Int,
        relationLogLimit: Int
    ): ScrmContactDetail {
        require(contactId > 0) { "contactId must be greater than 0" }
        require(commonChatRoomLimit >= 0) { "commonChatRoomLimit cannot be negative" }
        require(relationLogLimit >= 0) { "relationLogLimit cannot be negative" }
        val response: JsonElement = get(
            path = "contacts/$contactId/detail",
            query = linkedMapOf(
                "commonChatRoomLimit" to commonChatRoomLimit.toString(),
                "relationLogLimit" to relationLogLimit.toString()
            ),
            safeRoute = "/openapi/v1/contacts/{contactId}/detail"
        )
        return decodeContactDetail(response)
    }

    override fun getCommonChatRooms(
        friendId: String,
        query: ScrmCommonChatRoomQuery
    ): ScrmCommonChatRoomPage {
        require(friendId.isNotBlank()) { "friendId cannot be blank" }
        return get(
            path = "contacts/${scrmPathSegment(friendId)}/common-chatrooms",
            query = linkedMapOf(
                "weChatId" to query.weChatId?.takeIf { it.isNotBlank() },
                "page" to query.page.toString(),
                "pageSize" to query.pageSize.toString(),
                "search" to query.search?.takeIf { it.isNotBlank() },
                "includeDeleted" to query.includeDeleted.toString()
            ),
            safeRoute = "/openapi/v1/contacts/{friendId}/common-chatrooms"
        )
    }

    override fun getContactLabels(
        weChatId: String?,
        includeDeleted: Boolean
    ): List<ScrmContactLabel> {
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        return get(
            path = "contact-labels",
            query = linkedMapOf(
                "weChatId" to weChatId?.takeIf { it.isNotBlank() },
                "includeDeleted" to includeDeleted.toString()
            )
        )
    }

    override fun getContactWxids(query: ScrmContactWxidQuery): ScrmContactWxidList {
        return get(
            path = "contacts/wxids",
            query = linkedMapOf(
                "weChatId" to query.weChatId?.takeIf { it.isNotBlank() },
                "search" to query.search?.takeIf { it.isNotBlank() },
                "includeDeleted" to query.includeDeleted.toString(),
                "onlyFriends" to query.onlyFriends.toString(),
                "labelIds" to query.labelIds?.takeIf { it.isNotBlank() },
                "labelNames" to query.labelNames?.takeIf { it.isNotBlank() },
                "customerLevel" to query.customerLevel?.takeIf { it.isNotBlank() },
                "sourceChannel" to query.sourceChannel?.takeIf { it.isNotBlank() },
                "profileKey" to query.profileKey?.takeIf { it.isNotBlank() },
                "profileOnly" to query.profileOnly?.toString()
            ),
            safeRoute = "/openapi/v1/contacts/wxids"
        )
    }

    override fun setContactLabels(
        request: ScrmSetContactLabelsRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "contacts/labels",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/contacts/labels"
        )
    }

    override fun setContactLabelsBatch(
        request: ScrmBatchSetContactLabelsRequest
    ): ScrmBatchSetContactLabelsResponse {
        return post(
            path = "contacts/labels/batch",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/contacts/labels/batch"
        )
    }

    override fun saveContactLabel(
        request: ScrmSaveContactLabelRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "contact-labels",
            body = json.encodeToString(request)
        )
    }

    override fun syncContactLabels(
        request: ScrmAccountMutationRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "contact-labels/sync",
            body = json.encodeToString(request)
        )
    }

    override fun deleteContactLabel(
        request: ScrmDeleteContactLabelRequest
    ): ScrmTaskSubmissionResult {
        return delete(
            path = "contact-labels/${request.labelId}",
            query = linkedMapOf(
                "deviceUuid" to request.deviceUuid,
                "weChatId" to request.weChatId
            ),
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/contact-labels/{labelId}"
        )
    }

    override fun setContactLabelsByFilter(
        request: ScrmBatchSetContactLabelsByFilterRequest
    ): ScrmBatchSetContactLabelsResponse {
        return post(
            path = "contacts/labels/batch-by-filter",
            body = json.encodeToString(request)
        )
    }

    override fun getContactManagementPage(
        query: ScrmContactManagementQuery
    ): ScrmContactManagementPage {
        return get(
            path = "contacts/page",
            query = linkedMapOf(
                "offset" to query.offset.toString(),
                "limit" to query.limit.toString(),
                "search" to query.search,
                "weChatId" to query.weChatId,
                "blockedOnly" to query.blockedOnly.toString()
            )
        )
    }

    override fun saveCustomerProfileDraft(
        request: ScrmSaveCustomerProfileDraftRequest
    ): ScrmCustomerProfileDraft {
        return post(
            path = "contacts/customer-profile-drafts",
            body = json.encodeToString(request)
        )
    }

    override fun saveCustomerProfile(
        contactId: Int,
        request: ScrmSaveCustomerProfileRequest
    ): ScrmCustomerProfile {
        require(contactId > 0) { "contactId must be greater than 0" }
        val response: JsonElement = put(
            path = "contacts/$contactId/customer-profile",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/contacts/{contactId}/customer-profile"
        )
        return decodeCustomerProfile(response)
    }

    override fun deleteContactFriend(
        request: ScrmDeleteContactFriendRequest
    ): ScrmTaskSubmissionResult {
        return delete(
            path = "contacts/${request.contactId}/friend",
            query = linkedMapOf(
                "deviceUuid" to request.deviceUuid,
                "weChatId" to request.weChatId,
                "friendId" to request.friendId
            ),
            body = "{}",
            safeRoute = "/openapi/v1/contacts/{contactId}/friend"
        )
    }

    override fun setFriendPermission(
        request: ScrmSetFriendPermissionRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friends/permissions",
            body = json.encodeToString(request)
        )
    }

    override fun setFriendPermissionsBatch(
        request: ScrmBatchSetFriendPermissionRequest
    ): ScrmBatchSetFriendPermissionResponse {
        return post(
            path = "friends/permissions/batch",
            body = json.encodeToString(request)
        )
    }

    override fun setFriendPermissionsByFilter(
        request: ScrmBatchSetFriendPermissionByFilterRequest
    ): ScrmBatchSetFriendPermissionResponse {
        return post(
            path = "friends/permissions/batch-by-filter",
            body = json.encodeToString(request)
        )
    }

    override fun modifyFriendProfile(
        request: ScrmModifyFriendProfileRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friends/profile",
            body = json.encodeToString(request)
        )
    }

    override fun refreshFriendInfo(
        request: ScrmRefreshFriendInfoRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friends/refresh-info",
            body = json.encodeToString(request)
        )
    }

    override fun getChatRooms(query: ScrmChatRoomQuery): ScrmChatRoomPage {
        return get(
            path = "chatrooms",
            query = linkedMapOf(
                "weChatId" to query.weChatId?.takeIf { it.isNotBlank() },
                "page" to query.page.toString(),
                "pageSize" to query.pageSize.toString(),
                "search" to query.search?.takeIf { it.isNotBlank() },
                "includeDeleted" to query.includeDeleted.toString()
            )
        )
    }

    override fun getChatRoomMembers(
        chatRoomId: String,
        query: ScrmChatRoomMemberQuery
    ): ScrmChatRoomMemberPage {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        return get(
            path = "chatrooms/${scrmPathSegment(chatRoomId)}/members",
            query = linkedMapOf(
                "weChatId" to query.weChatId?.takeIf { it.isNotBlank() },
                "page" to query.page.toString(),
                "pageSize" to query.pageSize.toString(),
                "search" to query.search?.takeIf { it.isNotBlank() },
                "includeDeleted" to query.includeDeleted.toString()
            ),
            safeRoute = "/openapi/v1/chatrooms/{chatRoomId}/members"
        )
    }

    override fun createChatRoom(request: ScrmCreateChatRoomRequest): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms"
        )
    }

    override fun syncChatRooms(request: ScrmSyncChatRoomsRequest): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/sync",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/sync"
        )
    }

    override fun refreshChatRoom(request: ScrmRefreshChatRoomRequest): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/refresh",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/refresh"
        )
    }

    override fun inviteChatRoomMembers(
        request: ScrmChatRoomMemberMutationRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/members/invite",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/members/invite"
        )
    }

    override fun kickChatRoomMembers(
        request: ScrmChatRoomMemberMutationRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/members/kick",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/members/kick"
        )
    }

    override fun renameChatRoom(request: ScrmRenameChatRoomRequest): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/rename",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/rename"
        )
    }

    override fun setChatRoomNotice(
        request: ScrmSetChatRoomNoticeRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/notice",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/notice"
        )
    }

    override fun pullChatRoomQrCode(
        request: ScrmChatRoomActionRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/qrcode",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/qrcode"
        )
    }

    override fun exitChatRoom(request: ScrmChatRoomActionRequest): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/exit",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/chatrooms/exit"
        )
    }

    override fun createChatRoomByFilter(
        request: ScrmCreateChatRoomByFilterRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/by-filter", body = json.encodeToString(request))
    }

    override fun agreeChatRoomInvite(
        request: ScrmAgreeChatRoomInviteRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/invites/agree", body = json.encodeToString(request))
    }

    override fun approveChatRoomInvite(
        request: ScrmApproveChatRoomInviteRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/invites/approve", body = json.encodeToString(request))
    }

    override fun pullChatRoomInvites(
        request: ScrmAccountMutationRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/invites/pull", body = json.encodeToString(request))
    }

    override fun sendJielong(request: ScrmSendJielongRequest): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/jielong", body = json.encodeToString(request))
    }

    override fun joinChatRoomByQr(
        request: ScrmJoinChatRoomByQrRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/join-by-qr", body = json.encodeToString(request))
    }

    override fun addChatRoomManagers(
        request: ScrmChatRoomManagersRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/managers/add", body = json.encodeToString(request))
    }

    override fun removeChatRoomManagers(
        request: ScrmChatRoomManagersRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/managers/remove", body = json.encodeToString(request))
    }

    override fun inviteChatRoomMembersByFilter(
        request: ScrmChatRoomMembersByFilterRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/members/invite-by-filter",
            body = json.encodeToString(request)
        )
    }

    override fun kickChatRoomMembersByFilter(
        request: ScrmChatRoomMembersByFilterRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "chatrooms/members/kick-by-filter",
            body = json.encodeToString(request)
        )
    }

    override fun setChatRoomNewMessageNotify(
        request: ScrmChatRoomSwitchRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/new-message-notify", body = json.encodeToString(request))
    }

    override fun setChatRoomRemark(
        request: ScrmChatRoomTextRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/remark", body = json.encodeToString(request))
    }

    override fun setChatRoomSavedToPhonebook(
        request: ScrmChatRoomSwitchRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/save-to-phonebook", body = json.encodeToString(request))
    }

    override fun setChatRoomSelfDisplayName(
        request: ScrmChatRoomTextRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/self-display-name", body = json.encodeToString(request))
    }

    override fun setChatRoomTop(
        request: ScrmChatRoomSwitchRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/top", body = json.encodeToString(request))
    }

    override fun transferChatRoomOwner(
        request: ScrmTransferChatRoomOwnerRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/transfer-owner", body = json.encodeToString(request))
    }

    override fun setChatRoomVerify(
        request: ScrmChatRoomSwitchRequest
    ): ScrmTaskSubmissionResult {
        return post(path = "chatrooms/verify", body = json.encodeToString(request))
    }

    override fun syncContacts(request: ScrmSyncContactsRequest): ScrmTaskSubmissionResult {
        return post(
            path = "contacts/sync",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/contacts/sync"
        )
    }

    override fun addFriend(request: ScrmAddFriendRequest): ScrmTaskSubmissionResult {
        return post(
            path = "friends",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friends"
        )
    }

    override fun findFriend(request: ScrmFindContactRequest): ScrmTaskSubmissionResult {
        return post(
            path = "friends/find",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friends/find"
        )
    }

    override fun addFriendsByPhone(
        request: ScrmAddFriendsByPhoneRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friends/by-phone",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friends/by-phone"
        )
    }

    override fun sendFriendVerify(
        request: ScrmSendFriendVerifyRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friends/verify",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friends/verify"
        )
    }

    override fun deleteFriend(
        friendId: String,
        deviceUuid: String,
        weChatId: String
    ): ScrmTaskSubmissionResult {
        val request = ScrmDeleteFriendRequest(
            deviceUuid = deviceUuid,
            weChatId = weChatId,
            friendId = friendId
        )
        return delete(
            path = "friends/$friendId",
            query = linkedMapOf(
                "deviceUuid" to deviceUuid,
                "weChatId" to weChatId
            ),
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friends/{friendId}"
        )
    }

    override fun getFriendRequests(
        weChatId: String?,
        count: Int,
        pendingOnly: Boolean
    ): List<ScrmFriendRequest> {
        require(count > 0) { "count 必须大于 0" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId 不能为空" }
        return get(
            path = "friend-requests",
            query = linkedMapOf(
                "weChatId" to weChatId?.takeIf { it.isNotBlank() },
                "count" to count.toString(),
                "pendingOnly" to pendingOnly.toString()
            )
        )
    }

    override fun handleFriendRequest(
        request: ScrmHandleFriendRequestRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friend-requests/handle",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friend-requests/handle"
        )
    }

    /** 对应 iOS 拉取好友申请流程，任务回包后由页面重新读取服务端列表。 */
    override fun pullFriendRequests(
        request: ScrmPullFriendRequestsRequest
    ): ScrmTaskSubmissionResult {
        return post(
            path = "friend-requests/pull",
            body = json.encodeToString(request),
            safeRoute = "/openapi/v1/friend-requests/pull"
        )
    }

    private inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        safeRoute: String = "/openapi/v1/$path"
    ): T {
        return executeJson(
            method = "GET",
            path = path,
            query = query,
            body = null,
            safeRoute = safeRoute
        )
    }

    /** Keeps Android compatible with the field aliases accepted by the iOS profile reader. */
    private fun decodeCustomerProfile(element: JsonElement): ScrmCustomerProfile {
        val envelope = unwrapPayload(element)
        val root = (envelope["customerProfile"] as? JsonObject) ?: envelope
        return ScrmCustomerProfile(
            id = root.intValue("id"),
            contactId = root.intValue("contactId"),
            ownerWxid = root.stringValue("ownerWxid"),
            friendWxid = root.stringValue("friendWxid"),
            displayName = root.stringValue("displayName"),
            customerLevel = root.firstStringValue("customerLevel", "level"),
            sourceChannel = root.firstStringValue("sourceChannel", "source"),
            sourceDetail = root.firstStringValue("sourceDetail", "sourceExt"),
            profileKey = root.firstStringValue("profileKey", "profileId"),
            purchaseHistory = root.firstStringValue("purchaseHistory", "purchases"),
            socialAccounts = root.firstStringValue("socialAccounts", "socialAccount"),
            faceImageUrl = root.stringValue("faceImageUrl"),
            notes = root.firstStringValue("notes", "remark"),
            phone = root.firstStringValue("phone", "mobile", "mobilePhone"),
            mappedLabelIds = root["mappedLabelIds"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                .orEmpty(),
            mappedLabelNames = root.firstLabelNames("mappedLabelNames", "labelNames", "labels"),
            createdAt = root.stringValue("createdAt"),
            updatedAt = root.stringValue("updatedAt")
        )
    }

    private fun decodeContactDetail(element: JsonElement): ScrmContactDetail {
        val root = unwrapPayload(element)
        return ScrmContactDetail(
            contact = root["contact"]?.let { json.decodeFromJsonElement<ScrmContact>(it) },
            customerProfile = root["customerProfile"]?.let(::decodeCustomerProfile),
            labels = root["labels"]?.jsonArray?.map {
                json.decodeFromJsonElement<ScrmContactLabel>(it)
            }.orEmpty(),
            commonChatRooms = root["commonChatRooms"]?.jsonArray?.map {
                json.decodeFromJsonElement<ScrmCommonChatRoom>(it)
            }.orEmpty(),
            relationLogs = root["relationLogs"]?.jsonArray?.map {
                json.decodeFromJsonElement<ScrmContactRelationLog>(it)
            }.orEmpty()
        )
    }

    private fun unwrapPayload(element: JsonElement): JsonObject {
        val root = element.jsonObject
        val nested = listOf("data", "result", "payload", "item", "record")
            .firstNotNullOfOrNull { key -> root[key] as? JsonObject }
        return nested?.let(::unwrapPayload) ?: root
    }

    private fun JsonObject.stringValue(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.firstStringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> this.stringValue(key) }
    }

    private fun JsonObject.intValue(key: String): Int {
        return this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun JsonObject.firstLabelNames(vararg keys: String): List<String> {
        val values = keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray } ?: return emptyList()
        return values.mapNotNull { value ->
            (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: (value as? JsonObject)?.firstStringValue("name", "labelName", "value")
        }
    }

    private inline fun <reified T> post(
        path: String,
        body: String,
        safeRoute: String = "/openapi/v1/$path"
    ): T {
        return executeJson(
            method = "POST",
            path = path,
            body = body,
            safeRoute = safeRoute
        )
    }

    private inline fun <reified T> postIdempotent(
        path: String,
        body: String,
        idempotencyKey: String,
        safeRoute: String = "/openapi/v1/$path"
    ): T {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey cannot be blank" }
        require(idempotencyKey.length <= 128) { "idempotencyKey cannot exceed 128 characters" }
        return executeJson(
            method = "POST",
            path = path,
            body = body,
            safeRoute = safeRoute,
            extraHeaders = mapOf("Idempotency-Key" to idempotencyKey)
        )
    }

    private inline fun <reified T> put(
        path: String,
        body: String,
        safeRoute: String = "/openapi/v1/$path"
    ): T {
        return executeJson(
            method = "PUT",
            path = path,
            body = body,
            safeRoute = safeRoute
        )
    }

    private inline fun <reified T> delete(
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String,
        safeRoute: String = "/openapi/v1/$path"
    ): T {
        return executeJson(
            method = "DELETE",
            path = path,
            query = query,
            body = body,
            safeRoute = safeRoute
        )
    }

    private inline fun <reified T> executeJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String?,
        safeRoute: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): T {
        return executeRequest(
            method = method,
            path = path,
            query = query,
            headers = authenticatedJsonHeaders(hasBody = body != null) + extraHeaders,
            body = body,
            bodyBytes = null,
            safeRoute = safeRoute
        )
    }

    private inline fun <reified T> postMultipart(
        path: String,
        request: ScrmMediaUploadRequest,
        safeRoute: String
    ): T {
        val boundary = "scrm-${UUID.randomUUID()}"
        return executeRequest(
            method = "POST",
            path = path,
            headers = authenticatedMultipartHeaders(boundary),
            body = null,
            bodyBytes = multipartFileBody(boundary, request),
            safeRoute = safeRoute
        )
    }

    private inline fun <reified T> executeRequest(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String>,
        body: String?,
        bodyBytes: ByteArray?,
        safeRoute: String,
        urlOverride: String? = null
    ): T {
        val apiKey = config.apiKey
            ?: throw ScrmConfigurationException("尚未配置 SCRM API Key")
        val request = ScrmHttpRequest(
            method = method,
            url = urlOverride ?: config.endpoint(path, query),
            headers = headers,
            body = body,
            bodyBytes = bodyBytes,
            safeRoute = safeRoute
        )
        val response = try {
            transport.execute(request)
        } catch (_: SocketTimeoutException) {
            throw ScrmTimeoutException()
        } catch (_: IOException) {
            throw ScrmNetworkException()
        }
        ensureSuccessful(response, apiKey)
        return try {
            if (T::class == ScrmTaskSubmissionResult::class) {
                decodeTaskSubmissionResult(response.body) as T
            } else {
                json.decodeFromString(response.body)
            }
        } catch (error: ScrmException) {
            throw error
        } catch (_: SerializationException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        } catch (_: IllegalArgumentException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        }
    }

    private fun decodeTaskSubmissionResult(body: String): ScrmTaskSubmissionResult {
        val element = json.parseToJsonElement(body)
        val jsonObject = element.jsonObject
        val taskId = jsonObject["taskId"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toLongOrNull()
        val success = jsonObject["success"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
        val message = jsonObject["message"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        if (success == false) {
            throw ScrmRequestException(
                statusCode = 400,
                message = message ?: "SCRM 未受理任务"
            )
        }
        if (taskId == null || taskId <= 0L) {
            throw ScrmRequestException(
                statusCode = 400,
                message = message ?: "SCRM 未返回有效 taskId"
            )
        }
        return json.decodeFromString(body)
    }

    private fun authenticatedJsonHeaders(hasBody: Boolean): Map<String, String> {
        val apiKey = config.apiKey
            ?: throw ScrmConfigurationException("灏氭湭閰嶇疆 SCRM API Key")
        return linkedMapOf(
            "Accept" to "application/json",
            "X-API-Key" to apiKey.headerValue()
        ).apply {
            if (hasBody) {
                put("Content-Type", "application/json")
            }
        }
    }

    private fun authenticatedMultipartHeaders(boundary: String): Map<String, String> {
        val apiKey = config.apiKey
            ?: throw ScrmConfigurationException("灏氭湭閰嶇疆 SCRM API Key")
        return linkedMapOf(
            "Accept" to "application/json",
            "X-API-Key" to apiKey.headerValue(),
            "Content-Type" to "multipart/form-data; boundary=$boundary"
        )
    }

    private fun multipartFileBody(
        boundary: String,
        request: ScrmMediaUploadRequest
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeAscii("--$boundary\r\n")
        output.writeAscii(
            "Content-Disposition: form-data; name=\"file\"; " +
                "filename=\"${request.fileName.multipartQuoted()}\"\r\n"
        )
        output.writeAscii("Content-Type: ${request.contentType}\r\n\r\n")
        output.write(request.bytes)
        output.writeAscii("\r\n--$boundary--\r\n")
        return output.toByteArray()
    }

    private fun ensureSuccessful(response: ScrmHttpResponse, apiKey: ScrmApiKey) {
        if (response.statusCode in 200..299) return

        val serviceMessage = extractServiceMessage(response.body)
            ?.replace(apiKey.headerValue(), "****")
            ?.takeIf { it.isNotBlank() }
        val message = serviceMessage ?: "SCRM 请求失败，HTTP ${response.statusCode}"
        when (response.statusCode) {
            401 -> throw ScrmAuthenticationException(message)
            403 -> throw ScrmPermissionException(message)
            429 -> throw ScrmRateLimitException(
                message = message,
                retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull()
            )
            in 500..599 -> throw ScrmServerException(response.statusCode, message)
            else -> throw ScrmRequestException(response.statusCode, message)
        }
    }

    private fun extractServiceMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val objectValue = json.parseToJsonElement(body).jsonObject
            listOf("message", "detail", "title")
                .firstNotNullOfOrNull { key -> objectValue[key]?.jsonPrimitive?.contentOrNull }
        }.getOrNull()
    }
}

private fun scrmPathSegment(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.toByteArray(StandardCharsets.US_ASCII))
}

private fun String.multipartQuoted(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}
