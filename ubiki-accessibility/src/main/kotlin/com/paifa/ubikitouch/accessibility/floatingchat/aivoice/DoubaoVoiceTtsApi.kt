package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class DoubaoVoiceHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String
)

interface DoubaoVoiceHttpTransport {
    suspend fun postStreamingJson(request: DoubaoVoiceHttpRequest): String
}

class DoubaoVoiceException(
    val providerCode: Int,
    providerMessage: String
) : IllegalStateException("Doubao Voice error $providerCode: $providerMessage")

class DoubaoVoiceTtsApi(
    private val transport: DoubaoVoiceHttpTransport = UrlConnectionDoubaoVoiceTransport()
) {
    suspend fun synthesize(
        apiKey: String,
        text: String,
        speaker: String = DefaultDoubaoTtsSpeaker,
        resourceId: String = DoubaoTtsResourceId
    ): ByteArray {
        val normalizedKey = apiKey.trim()
        require(normalizedKey.isNotEmpty()) { "Doubao Voice API Key is required" }
        require(text.isNotBlank()) { "Voice message text is required" }
        val request = buildDoubaoTtsRequest(
            apiKey = normalizedKey,
            text = text,
            speaker = speaker,
            resourceId = resourceId,
            requestId = UUID.randomUUID().toString()
        )
        return parseDoubaoTtsAudio(transport.postStreamingJson(request))
    }
}

fun buildDoubaoTtsRequest(
    apiKey: String,
    text: String,
    requestId: String,
    speaker: String = DefaultDoubaoTtsSpeaker,
    resourceId: String = DoubaoTtsResourceId
): DoubaoVoiceHttpRequest {
    require(apiKey.isNotBlank()) { "Doubao Voice API Key is required" }
    require(text.isNotBlank()) { "Voice message text is required" }
    require(requestId.isNotBlank()) { "Request ID is required" }
    val body = buildJsonObject {
        put("req_params", buildJsonObject {
            put("text", text)
            put("speaker", speaker)
            put("audio_params", buildJsonObject {
                put("format", "mp3")
                put("sample_rate", DoubaoTtsSampleRate)
            })
        })
    }.toString()
    return DoubaoVoiceHttpRequest(
        url = DoubaoTtsEndpoint,
        headers = mapOf(
            "X-Api-Key" to apiKey,
            "X-Api-Resource-Id" to resourceId,
            "X-Api-Request-Id" to requestId,
            "Content-Type" to "application/json"
        ),
        body = body
    )
}

fun parseDoubaoTtsAudio(payload: String): ByteArray {
    val audio = ByteArrayOutputStream()
    payload.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { line ->
            val response = DoubaoJson.parseToJsonElement(line).jsonObject
            val code = response["code"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Doubao Voice response is missing code")
            val message = response["message"]?.jsonPrimitive?.content.orEmpty()
            if (code != DoubaoTtsChunkCode && code != DoubaoTtsCompletedCode) {
                throw DoubaoVoiceException(code, message)
            }
            response["data"]?.jsonPrimitive?.content
                ?.takeIf(String::isNotBlank)
                ?.let { audio.write(Base64.getDecoder().decode(it)) }
        }
    return audio.toByteArray().also {
        require(it.isNotEmpty()) { "Doubao Voice returned no audio data" }
    }
}

class UrlConnectionDoubaoVoiceTransport : DoubaoVoiceHttpTransport {
    override suspend fun postStreamingJson(request: DoubaoVoiceHttpRequest): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = DoubaoConnectTimeoutMs
                connection.readTimeout = DoubaoReadTimeoutMs
                connection.doOutput = true
                request.headers.forEach(connection::setRequestProperty)
                connection.outputStream.use { output ->
                    output.write(request.body.toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.useLines { lines -> lines.joinToString("\n") }
                    .orEmpty()
                if (status !in 200..299) {
                    throw AiVoiceGatewayException(status, response)
                }
                response
            } finally {
                connection.disconnect()
            }
        }
    }
}

private val DoubaoJson = Json { ignoreUnknownKeys = true }
private const val DoubaoTtsEndpoint = "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
private const val DoubaoTtsResourceId = "seed-tts-2.0"
private const val DefaultDoubaoTtsSpeaker = "zh_female_vv_uranus_bigtts"
private const val DoubaoTtsSampleRate = 24_000
private const val DoubaoTtsChunkCode = 0
private const val DoubaoTtsCompletedCode = 20_000_000
private const val DoubaoConnectTimeoutMs = 15_000
private const val DoubaoReadTimeoutMs = 60_000
