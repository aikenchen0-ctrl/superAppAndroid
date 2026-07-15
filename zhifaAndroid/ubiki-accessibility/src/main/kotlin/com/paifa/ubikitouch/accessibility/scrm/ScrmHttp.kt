package com.paifa.ubikitouch.accessibility.scrm

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private val SensitiveHeaderNames = setOf("authorization", "x-api-key", "proxy-authorization")

internal class ScrmHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
    val bodyBytes: ByteArray? = null,
    private val safeRoute: String = "<redacted>"
) {
    init {
        require(body == null || bodyBytes == null) { "request cannot have both body and bodyBytes" }
    }

    override fun toString(): String {
        val safeHeaders = headers.entries.joinToString(", ") { (name, value) ->
            val renderedValue = if (name.lowercase() in SensitiveHeaderNames) "****" else value
            "$name=$renderedValue"
        }
        return "ScrmHttpRequest(method=$method, route=$safeRoute, headers={$safeHeaders})"
    }
}

internal class ScrmHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
) {
    fun header(name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    override fun toString(): String {
        return "ScrmHttpResponse(statusCode=$statusCode, bodyLength=${body.length})"
    }
}

internal fun interface ScrmHttpTransport {
    fun execute(request: ScrmHttpRequest): ScrmHttpResponse
}

internal class HttpUrlConnectionScrmTransport(
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int
) : ScrmHttpTransport {
    override fun execute(request: ScrmHttpRequest): ScrmHttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            request.headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            val bytes = request.body?.toByteArray(StandardCharsets.UTF_8) ?: request.bodyBytes
            bytes?.let { bodyBytes ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Length", bodyBytes.size.toString())
                connection.outputStream.use { output -> output.write(bodyBytes) }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { (_, values) -> values.joinToString(",") }
            ScrmHttpResponse(statusCode = statusCode, body = body, headers = headers)
        } finally {
            connection.disconnect()
        }
    }
}
