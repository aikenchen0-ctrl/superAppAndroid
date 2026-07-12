package com.paifa.ubikitouch.accessibility.scrm

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val OpenApiBasePath = "/openapi/v1"
private const val RedactedSecret = "****"

internal class ScrmApiKey private constructor(
    private val value: String
) {
    val masked: String = if (value.length > 4) {
        RedactedSecret + value.takeLast(4)
    } else {
        RedactedSecret
    }

    internal fun headerValue(): String = value

    override fun toString(): String = masked

    companion object {
        fun from(value: String): ScrmApiKey {
            val normalized = value.trim()
            require(normalized.isNotEmpty()) { "SCRM API Key 不能为空" }
            return ScrmApiKey(normalized)
        }
    }
}

internal class ScrmApiConfig(
    baseUrl: String,
    val apiKey: ScrmApiKey?,
    val connectTimeoutMillis: Int = 10_000,
    val readTimeoutMillis: Int = 20_000
) {
    val baseUrl: String = normalizeApiBaseUrl(baseUrl)

    init {
        require(connectTimeoutMillis > 0) { "连接超时必须大于 0" }
        require(readTimeoutMillis > 0) { "读取超时必须大于 0" }
    }

    fun endpoint(
        path: String,
        query: Map<String, String?> = emptyMap()
    ): String {
        val relativePath = path.trim().removePrefix("/").removePrefix("openapi/v1/")
        require(relativePath.isNotEmpty()) { "SCRM API 路径不能为空" }
        val encodedQuery = query.entries
            .asSequence()
            .filter { it.value != null }
            .joinToString("&") { (name, value) ->
                "${percentEncode(name)}=${percentEncode(requireNotNull(value))}"
            }
        return buildString {
            append(baseUrl)
            append('/')
            append(relativePath)
            if (encodedQuery.isNotEmpty()) {
                append('?')
                append(encodedQuery)
            }
        }
    }

    override fun toString(): String {
        return "ScrmApiConfig(baseUrl=$baseUrl, apiKey=${apiKey ?: RedactedSecret}, " +
            "connectTimeoutMillis=$connectTimeoutMillis, readTimeoutMillis=$readTimeoutMillis)"
    }
}

private fun normalizeApiBaseUrl(value: String): String {
    val input = value.trim()
    require(input.isNotEmpty()) { "SCRM 服务地址不能为空" }
    val uri = runCatching { URI(input) }
        .getOrElse { throw IllegalArgumentException("SCRM 服务地址格式无效", it) }
    require(uri.scheme == "http" || uri.scheme == "https") {
        "SCRM 服务地址只支持 http 或 https"
    }
    require(!uri.host.isNullOrBlank()) { "SCRM 服务地址缺少主机名" }
    require(uri.userInfo == null) { "SCRM 服务地址不能包含账号信息" }
    require(uri.query == null) { "SCRM 服务地址不能包含查询参数" }
    require(uri.fragment == null) { "SCRM 服务地址不能包含片段" }

    val inputPath = uri.path.orEmpty().trimEnd('/')
    val apiPath = if (inputPath.endsWith(OpenApiBasePath)) {
        inputPath
    } else {
        inputPath + OpenApiBasePath
    }
    return URI(
        uri.scheme,
        null,
        uri.host,
        uri.port,
        apiPath,
        null,
        null
    ).toASCIIString()
}

private fun percentEncode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
