package com.paifa.ubikitouch.accessibility.scrm

import java.io.IOException
import java.net.SocketTimeoutException
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
) : ScrmReadApi, ScrmTaskApi, ScrmMessageApi {
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

    private inline fun <reified T> executeJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String?,
        safeRoute: String
    ): T {
        val apiKey = config.apiKey
            ?: throw ScrmConfigurationException("尚未配置 SCRM API Key")
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "X-API-Key" to apiKey.headerValue()
        )
        if (body != null) {
            headers["Content-Type"] = "application/json"
        }
        val request = ScrmHttpRequest(
            method = method,
            url = config.endpoint(path, query),
            headers = headers,
            body = body,
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
            json.decodeFromString(response.body)
        } catch (_: SerializationException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        } catch (_: IllegalArgumentException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        }
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
