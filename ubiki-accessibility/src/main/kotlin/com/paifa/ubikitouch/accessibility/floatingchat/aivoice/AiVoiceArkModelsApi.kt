package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface AiVoiceArkModelsTransport {
    suspend fun getModels(url: String, apiKey: String): String
}

class AiVoiceArkModelsApi(
    private val endpointUrl: String = DefaultArkModelsEndpoint,
    private val transport: AiVoiceArkModelsTransport = UrlConnectionAiVoiceArkModelsTransport()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchModels(apiKey: String): List<AiVoiceModelOption> {
        val normalizedKey = apiKey.trim()
        require(normalizedKey.isNotEmpty()) { "Volcengine Ark API Key is required" }

        val response = transport.getModels(endpointUrl, normalizedKey)
        return json.decodeFromString<ArkModelsResponse>(response)
            .data
            .map { model ->
                AiVoiceModelOption(
                    id = model.id,
                    name = model.name.ifBlank { model.id },
                    domain = model.domain,
                    status = model.status?.takeIf { it.isNotBlank() },
                    taskTypes = model.taskTypes
                )
            }
            .filter { it.id.isNotBlank() }
    }
}

class UrlConnectionAiVoiceArkModelsTransport : AiVoiceArkModelsTransport {
    override suspend fun getModels(url: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = ArkConnectTimeoutMs
                connection.readTimeout = ArkReadTimeoutMs
                connection.setRequestProperty("Authorization", "Bearer $apiKey")

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

@Serializable
private data class ArkModelsResponse(
    val data: List<ArkModelDto> = emptyList()
)

@Serializable
private data class ArkModelDto(
    val id: String = "",
    val name: String = "",
    val domain: String = "",
    val status: String? = null,
    @SerialName("task_type")
    val taskTypes: List<String> = emptyList()
)

private const val DefaultArkModelsEndpoint = "https://ark.cn-beijing.volces.com/api/v3/models"
private const val ArkConnectTimeoutMs = 15_000
private const val ArkReadTimeoutMs = 30_000
