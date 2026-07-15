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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
}

internal interface ScrmTaskApi {
    fun getTask(taskId: Long): ScrmTaskResult
    fun getRecentTasks(deviceUuid: String? = null, count: Int = 20): ScrmRecentTaskResults
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
    fun archiveMomentMaterial(
        materialId: Long,
        request: ScrmMomentMaterialControlRequest
    ): ScrmMomentMaterial
}

internal interface ScrmContactApi {
    fun getContacts(query: ScrmContactQuery = ScrmContactQuery()): ScrmContactPage
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
    fun handleFriendRequest(request: ScrmHandleFriendRequestRequest): ScrmTaskSubmissionResult
}

internal interface ScrmChatRoomApi {
    fun getChatRooms(query: ScrmChatRoomQuery = ScrmChatRoomQuery()): ScrmChatRoomPage
    fun getChatRoomMembers(
        chatRoomId: String,
        query: ScrmChatRoomMemberQuery = ScrmChatRoomMemberQuery()
    ): ScrmChatRoomMemberPage
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
) : ScrmReadApi, ScrmTaskApi, ScrmMessageApi, ScrmMomentApi, ScrmContactApi, ScrmChatRoomApi {
    override fun getMe(): ScrmMe = get("me")

    override fun getDevices(): List<ScrmDevice> = get("devices")

    override fun getWechatAccounts(): List<ScrmWechatAccount> = get("wechat-accounts")

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
        safeRoute: String
    ): T {
        return executeRequest(
            method = method,
            path = path,
            query = query,
            headers = authenticatedJsonHeaders(hasBody = body != null),
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
        safeRoute: String
    ): T {
        val apiKey = config.apiKey
            ?: throw ScrmConfigurationException("尚未配置 SCRM API Key")
        val request = ScrmHttpRequest(
            method = method,
            url = config.endpoint(path, query),
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
