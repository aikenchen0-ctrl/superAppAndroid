package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceArkModelsApiTest {
    @Test
    fun fetchModelsUsesArkModelListEndpointAndBearerKey() {
        runBlocking {
            val transport = RecordingArkModelsTransport(
                response = """
                    {
                      "data": [
                        {
                          "id": "doubao-seed-2-0-mini-260428",
                          "name": "doubao-seed-2-0-mini",
                          "domain": "VLM",
                          "status": "",
                          "task_type": ["VisualQuestionAnswering", "TextGeneration", "SpeechToText"]
                        }
                      ]
                    }
                """.trimIndent()
            )
            val api = AiVoiceArkModelsApi(transport = transport)

            val models = api.fetchModels(" ark-test-key ")

            assertEquals("https://ark.cn-beijing.volces.com/api/v3/models", transport.lastUrl)
            assertEquals("ark-test-key", transport.lastApiKey)
            assertEquals(1, models.size)
            assertEquals("doubao-seed-2-0-mini-260428", models.single().id)
            assertEquals(listOf("VisualQuestionAnswering", "TextGeneration", "SpeechToText"), models.single().taskTypes)
        }
    }

    @Test
    fun fetchModelsRejectsBlankArkKey() {
        runBlocking {
            val api = AiVoiceArkModelsApi(transport = RecordingArkModelsTransport("{}"))

            val result = runCatching { api.fetchModels(" ") }

            assertTrue(result.isFailure)
            assertEquals("Volcengine Ark API Key is required", result.exceptionOrNull()?.message)
        }
    }

    private class RecordingArkModelsTransport(private val response: String) : AiVoiceArkModelsTransport {
        var lastUrl: String? = null
        var lastApiKey: String? = null

        override suspend fun getModels(url: String, apiKey: String): String {
            lastUrl = url
            lastApiKey = apiKey
            return response
        }
    }
}
