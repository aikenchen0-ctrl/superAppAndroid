package com.paifa.ubikitouch.accessibility.floatingchat.finder

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.paifa.ubikitouch.accessibility.scrm.HttpUrlConnectionScrmTransport
import com.paifa.ubikitouch.accessibility.scrm.ScrmApiConfig
import com.paifa.ubikitouch.accessibility.scrm.ScrmAuthenticationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmConfigurationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpResponse
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpTransport
import com.paifa.ubikitouch.accessibility.scrm.ScrmInvalidResponseException
import com.paifa.ubikitouch.accessibility.scrm.ScrmNetworkException
import com.paifa.ubikitouch.accessibility.scrm.ScrmPermissionException
import com.paifa.ubikitouch.accessibility.scrm.ScrmRateLimitException
import com.paifa.ubikitouch.accessibility.scrm.ScrmRequestException
import com.paifa.ubikitouch.accessibility.scrm.ScrmServerException
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTimeoutException
import com.paifa.ubikitouch.accessibility.scrm.ScrmRecentTaskResults

/** Injectable OpenAPI boundary. Screens never construct URLs or HTTP requests directly. */
internal interface FinderApi : ScrmTaskApi {
    fun buildPostTemplate(request: FinderPostTemplateRequest): FinderPostTemplateResponse
    fun publishPost(request: FinderPostRequest): ScrmTaskSubmissionResult
    fun loadUserPage(request: FinderUserPageRequest): ScrmTaskSubmissionResult
    fun setLike(request: FinderLikeRequest): ScrmTaskSubmissionResult
    fun createComment(request: FinderCommentRequest): ScrmTaskSubmissionResult
    fun navigate(request: FinderNavigationRequest): ScrmTaskSubmissionResult
}

/**
 * Finder-only HTTP adapter. It accepts the common SCRM transport so contract tests can use
 * fixtures and production configuration remains in the existing SCRM settings flow.
 */
internal class ScrmFinderApi(
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
) : FinderApi {
    override fun buildPostTemplate(request: FinderPostTemplateRequest): FinderPostTemplateResponse {
        val response = post(FinderEndpoints.POST_TEMPLATE, json.encodeToString(request))
        return decode(response.body)
    }

    override fun publishPost(request: FinderPostRequest): ScrmTaskSubmissionResult =
        decodeTask(post(FinderEndpoints.POSTS, json.encodeToString(request)).body)

    override fun loadUserPage(request: FinderUserPageRequest): ScrmTaskSubmissionResult =
        decodeTask(post(FinderEndpoints.USER_PAGE, json.encodeToString(request)).body)

    override fun setLike(request: FinderLikeRequest): ScrmTaskSubmissionResult =
        decodeTask(post(FinderEndpoints.LIKES, json.encodeToString(request)).body)

    override fun createComment(request: FinderCommentRequest): ScrmTaskSubmissionResult =
        decodeTask(post(FinderEndpoints.COMMENTS, json.encodeToString(request)).body)

    override fun navigate(request: FinderNavigationRequest): ScrmTaskSubmissionResult =
        decodeTask(
            post(
                FinderEndpoints.NAVIGATION,
                json.encodeToString(request.copy(keyword = request.normalizedKeyword))
            ).body
        )

    override fun getTask(taskId: Long): ScrmTaskResult {
        require(taskId > 0L) { "taskId must be greater than 0" }
        return decode(get("/openapi/v1/tasks/$taskId").body)
    }

    override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
        require(count > 0) { "count must be greater than 0" }
        val query = buildList {
            deviceUuid?.trim()?.takeIf(String::isNotEmpty)?.let { add("deviceUuid=$it") }
            add("count=$count")
        }.joinToString("&")
        return decode(get("/openapi/v1/tasks/recent?$query").body)
    }

    private fun post(route: String, body: String): ScrmHttpResponse = execute("POST", route, body)

    private fun get(route: String): ScrmHttpResponse = execute("GET", route, body = null)

    private fun execute(method: String, route: String, body: String?): ScrmHttpResponse {
        val apiKey = config.apiKey ?: throw ScrmConfigurationException("SCRM API Key is not configured")
        val normalizedRoute = route.substringBefore('?')
        val query = route.substringAfter('?', missingDelimiterValue = "")
        val relativePath = FinderEndpoints.relativePath(normalizedRoute)
        val url = config.endpoint(
            relativePath,
            query.split('&').filter(String::isNotEmpty).associate { pair ->
                val key = pair.substringBefore('=')
                key to pair.substringAfter('=', missingDelimiterValue = "")
            }
        )
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "X-API-Key" to apiKey.headerValue()
        ).apply {
            if (body != null) put("Content-Type", "application/json")
        }
        val response = try {
            transport.execute(
                ScrmHttpRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    body = body,
                    safeRoute = normalizedRoute
                )
            )
        } catch (_: SocketTimeoutException) {
            throw ScrmTimeoutException()
        } catch (_: IOException) {
            throw ScrmNetworkException()
        }
        ensureSuccess(response)
        return response
    }

    private inline fun <reified T> decode(body: String): T {
        return try {
            json.decodeFromString(body)
        } catch (_: SerializationException) {
            throw ScrmInvalidResponseException("SCRM returned invalid Finder JSON")
        } catch (_: IllegalArgumentException) {
            throw ScrmInvalidResponseException("SCRM returned invalid Finder JSON")
        }
    }

    private fun decodeTask(body: String): ScrmTaskSubmissionResult {
        val result: ScrmTaskSubmissionResult = decode(body)
        if (!result.success) {
            throw ScrmRequestException(400, result.message ?: "SCRM did not accept the Finder task")
        }
        return result
    }

    private fun ensureSuccess(response: ScrmHttpResponse) {
        if (response.statusCode in 200..299) return
        val message = response.messageOrNull() ?: "Finder request failed with HTTP ${response.statusCode}"
        when (response.statusCode) {
            401 -> throw ScrmAuthenticationException(message)
            403 -> throw ScrmPermissionException(message)
            429 -> throw ScrmRateLimitException(message, response.header("Retry-After")?.toLongOrNull())
            in 500..599 -> throw ScrmServerException(response.statusCode, message)
            else -> throw ScrmRequestException(response.statusCode, message)
        }
    }

    private fun ScrmHttpResponse.messageOrNull(): String? {
        val objectValue = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return listOf("message", "detail", "title")
            .firstNotNullOfOrNull { key -> objectValue[key]?.jsonPrimitive?.contentOrNull }
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
