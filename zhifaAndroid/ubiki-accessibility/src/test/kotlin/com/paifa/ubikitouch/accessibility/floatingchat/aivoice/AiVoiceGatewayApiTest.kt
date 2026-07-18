package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiVoiceGatewayApiTest {
    @Test
    fun createVoiceProfileUploadsAudioToGateway() {
        runBlocking {
            val sample = File.createTempFile("voice-sample", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val transport = RecordingTransport(
                audioResponse = """{"id":"voice-1","displayName":"我的音色 01"}"""
            )
            val api = AiVoiceGatewayApi(configuredApi(), transport)

            val result = api.createVoiceProfile(sample.absolutePath)

            assertEquals("https://voice.example.com/voice-profiles", transport.lastUrl)
            assertEquals("voice-1", result.id)
            assertEquals(byteArrayOf(1, 2, 3).toList(), transport.lastAudio?.toList())
            sample.delete()
        }
    }

    @Test
    fun generateVoicePostsTextAndOptionalVoiceProfile() {
        runBlocking {
            val transport = RecordingTransport(
                jsonResponse = """{"audioUrl":"https://cdn.example.com/a.m4a","durationMs":1200}"""
            )
            val api = AiVoiceGatewayApi(configuredApi(), transport)

            val result = api.generateVoice(VoiceGenerationRequest("你好", "voice-1"))

            assertEquals("https://voice.example.com/voice-messages", transport.lastUrl)
            assertEquals("https://cdn.example.com/a.m4a", result.audioUrl)
            assertEquals(1200, result.durationMs)
        }
    }

    private fun configuredApi() = AiVoiceApiConfig(
        gatewayBaseUrl = "https://voice.example.com",
        sessionToken = "token"
    )

    private class RecordingTransport(
        private val jsonResponse: String = "{}",
        private val audioResponse: String = "{}"
    ) : AiVoiceHttpTransport {
        var lastUrl: String? = null
        var lastAudio: ByteArray? = null

        override suspend fun postJson(url: String, token: String, body: String): String {
            lastUrl = url
            return jsonResponse
        }

        override suspend fun postAudio(url: String, token: String, audio: ByteArray): String {
            lastUrl = url
            lastAudio = audio
            return audioResponse
        }
    }
}
