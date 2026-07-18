package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface AiVoiceHttpTransport {
    suspend fun postJson(url: String, token: String, body: String): String
    suspend fun postAudio(url: String, token: String, audio: ByteArray): String
}

class AiVoiceGatewayApi(
    private val config: AiVoiceApiConfig,
    private val transport: AiVoiceHttpTransport = UrlConnectionAiVoiceTransport()
) : AiVoiceApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createVoiceProfile(sampleAudioPath: String): VoiceProfileRemote {
        requireAiVoiceApiConfig(config)
        val sample = File(sampleAudioPath)
        require(sample.isFile && sample.length() > 0L) { "Voice sample file is missing or empty" }
        val response = transport.postAudio(
            url = endpoint("voice-profiles"),
            token = config.normalizedSessionToken(),
            audio = sample.readBytes()
        )
        return json.decodeFromString(response)
    }

    override suspend fun generateVoice(request: VoiceGenerationRequest): GeneratedVoiceRemote {
        requireAiVoiceApiConfig(config)
        require(request.text.isNotBlank()) { "Voice message text is required" }
        val response = transport.postJson(
            url = endpoint("voice-messages"),
            token = config.normalizedSessionToken(),
            body = json.encodeToString(request)
        )
        return json.decodeFromString(response)
    }

    override suspend fun createVoiceAgent(request: VoiceAgentRequest): VoiceAgentRemote {
        requireAiVoiceApiConfig(config)
        require(request.name.isNotBlank()) { "Voice agent name is required" }
        require(request.roleDescription.isNotBlank()) { "Voice agent role description is required" }
        val response = transport.postJson(
            url = endpoint("voice-agents"),
            token = config.normalizedSessionToken(),
            body = json.encodeToString(request)
        )
        return json.decodeFromString(response)
    }

    private fun endpoint(path: String): String = "${config.normalizedGatewayBaseUrl()}/$path"
}

class UrlConnectionAiVoiceTransport : AiVoiceHttpTransport {
    override suspend fun postJson(url: String, token: String, body: String): String {
        return post(url, token, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    override suspend fun postAudio(url: String, token: String, audio: ByteArray): String {
        return post(url, token, "audio/mp4", audio)
    }

    private suspend fun post(url: String, token: String, contentType: String, body: ByteArray): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = ConnectTimeoutMs
                connection.readTimeout = ReadTimeoutMs
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { output -> output.write(body) }

                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
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

class AiVoiceGatewayException(val statusCode: Int, responseBody: String) :
    IllegalStateException("AI voice gateway returned HTTP $statusCode: $responseBody")

private const val ConnectTimeoutMs = 15_000
private const val ReadTimeoutMs = 60_000
