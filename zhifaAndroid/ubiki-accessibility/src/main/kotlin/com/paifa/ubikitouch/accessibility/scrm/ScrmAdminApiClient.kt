package com.paifa.ubikitouch.accessibility.scrm

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface ScrmAdminApi {
    fun login(username: String, password: String): ScrmAdminSession
    fun createOpenApiKey(
        token: String,
        request: ScrmCreateOpenApiKeyRequest
    ): ScrmCreatedOpenApiKey
}

internal data class ScrmAdminSession(
    val token: String
) {
    init {
        require(token.isNotBlank()) { "后台登录令牌不能为空" }
    }

    override fun toString(): String = "ScrmAdminSession(token=****)"
}

@Serializable
internal data class ScrmCreateOpenApiKeyRequest(
    val name: String,
    val remark: String? = null,
    val expiresAt: String? = null
) {
    init {
        require(name.isNotBlank()) { "OpenAPI Key 名称不能为空" }
        require(remark == null || remark.isNotBlank()) { "OpenAPI Key 备注不能为空" }
        require(expiresAt == null || expiresAt.isNotBlank()) { "OpenAPI Key 过期时间不能为空" }
    }
}

internal data class ScrmCreatedOpenApiKey(
    val id: Long?,
    val keyPrefix: String?,
    val plainKey: String
) {
    init {
        require(plainKey.isNotBlank()) { "OpenAPI Key 明文不能为空" }
    }

    override fun toString(): String {
        return "ScrmCreatedOpenApiKey(id=$id, keyPrefix=$keyPrefix, plainKey=****)"
    }
}

internal class ScrmAdminApiClient(
    serverBaseUrl: String,
    private val transport: ScrmHttpTransport = HttpUrlConnectionScrmTransport(
        connectTimeoutMillis = 10_000,
        readTimeoutMillis = 20_000
    ),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
) : ScrmAdminApi {
    private val rootUrl = normalizeScrmServerRoot(serverBaseUrl)

    override fun login(username: String, password: String): ScrmAdminSession {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotBlank()) { "请输入后台账号" }
        require(password.isNotBlank()) { "请输入后台密码" }
        val response = post<ScrmAdminLoginResponse>(
            path = "api/auth/login",
            headers = emptyMap(),
            body = json.encodeToString(
                ScrmAdminLoginRequest(
                    username = normalizedUsername,
                    password = password
                )
            ),
            safeRoute = "/api/auth/login",
            sensitiveValue = password
        )
        val token = response.data?.token?.takeIf { it.isNotBlank() }
            ?: response.data?.accessToken?.takeIf { it.isNotBlank() }
            ?: response.token?.takeIf { it.isNotBlank() }
            ?: response.accessToken?.takeIf { it.isNotBlank() }
            ?: throw ScrmInvalidResponseException("后台登录响应缺少 token")
        return ScrmAdminSession(token)
    }

    override fun createOpenApiKey(
        token: String,
        request: ScrmCreateOpenApiKeyRequest
    ): ScrmCreatedOpenApiKey {
        require(token.isNotBlank()) { "后台登录令牌不能为空" }
        val response = post<ScrmCreateOpenApiKeyResponse>(
            path = "api/openapi-keys",
            headers = mapOf("Authorization" to "Bearer $token"),
            body = json.encodeToString(request),
            safeRoute = "/api/openapi-keys",
            sensitiveValue = token
        )
        val item = response.item ?: response.data?.item
        val plainKey = response.plainKey?.takeIf { it.isNotBlank() }
            ?: response.data?.plainKey?.takeIf { it.isNotBlank() }
            ?: throw ScrmInvalidResponseException("创建 OpenAPI Key 响应缺少明文 Key")
        return ScrmCreatedOpenApiKey(
            id = item?.id,
            keyPrefix = item?.keyPrefix,
            plainKey = plainKey
        )
    }

    private inline fun <reified T> post(
        path: String,
        headers: Map<String, String>,
        body: String,
        safeRoute: String,
        sensitiveValue: String
    ): T {
        val requestHeaders = linkedMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
        requestHeaders.putAll(headers)
        val request = ScrmHttpRequest(
            method = "POST",
            url = adminEndpoint(path),
            headers = requestHeaders,
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
        ensureSuccessful(response, sensitiveValue)
        return try {
            json.decodeFromString(response.body)
        } catch (_: SerializationException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        } catch (_: IllegalArgumentException) {
            throw ScrmInvalidResponseException("SCRM 返回了无效的 JSON 响应")
        }
    }

    private fun adminEndpoint(path: String): String {
        val relativePath = path.trim().trimStart('/')
        require(relativePath.isNotEmpty()) { "后台 API 路径不能为空" }
        return "$rootUrl/$relativePath"
    }

    private fun ensureSuccessful(response: ScrmHttpResponse, sensitiveValue: String) {
        if (response.statusCode in 200..299) return

        val serviceMessage = extractServiceMessage(response.body)
            ?.replace(sensitiveValue, "****")
            ?.takeIf { it.isNotBlank() }
        val message = serviceMessage ?: when (response.statusCode) {
            401 -> "后台账号或密码错误"
            403 -> "后台账号没有 OpenAPI Key 管理权限"
            else -> "SCRM 后台请求失败，HTTP ${response.statusCode}"
        }
        when (response.statusCode) {
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

@Serializable
private data class ScrmAdminLoginRequest(
    val username: String,
    val password: String
)

@Serializable
private data class ScrmAdminLoginResponse(
    val data: ScrmAdminLoginData? = null,
    val token: String? = null,
    val accessToken: String? = null
)

@Serializable
private data class ScrmAdminLoginData(
    val token: String? = null,
    val accessToken: String? = null
)

@Serializable
private data class ScrmCreateOpenApiKeyResponse(
    val item: ScrmOpenApiKeyItem? = null,
    val plainKey: String? = null,
    val data: ScrmCreateOpenApiKeyData? = null
)

@Serializable
private data class ScrmCreateOpenApiKeyData(
    val item: ScrmOpenApiKeyItem? = null,
    val plainKey: String? = null
)

@Serializable
private data class ScrmOpenApiKeyItem(
    val id: Long? = null,
    val keyPrefix: String? = null
)
