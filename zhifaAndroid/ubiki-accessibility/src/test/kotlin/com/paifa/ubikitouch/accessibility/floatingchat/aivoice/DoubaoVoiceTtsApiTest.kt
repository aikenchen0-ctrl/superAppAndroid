package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoVoiceTtsApiTest {
    @Test
    fun requestUsesSpeechApiKeyWithoutArkOrLegacyAppId() {
        val request = buildDoubaoTtsRequest(
            apiKey = "speech-key",
            text = "hello",
            requestId = "request-1"
        )

        assertEquals("https://openspeech.bytedance.com/api/v3/tts/unidirectional", request.url)
        assertEquals("speech-key", request.headers["X-Api-Key"])
        assertEquals("seed-tts-2.0", request.headers["X-Api-Resource-Id"])
        assertEquals("request-1", request.headers["X-Api-Request-Id"])
        assertNull(request.headers["Authorization"])
        assertFalse(request.body.contains("appid", ignoreCase = true))
        assertFalse(request.url.contains("ark.cn-beijing.volces.com"))
    }

    @Test
    fun streamingResponseCombinesAudioChunksAndAcceptsFinalSuccessCode() {
        val first = Base64.getEncoder().encodeToString(byteArrayOf(1, 2))
        val second = Base64.getEncoder().encodeToString(byteArrayOf(3, 4))
        val payload = """
            {"code":0,"message":"OK","data":"$first"}
            {"code":0,"message":"OK","data":"$second"}
            {"code":20000000,"message":"OK"}
        """.trimIndent()

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parseDoubaoTtsAudio(payload))
    }

    @Test
    fun synthesizeReturnsRealAudioFromTransport() = runBlocking {
        val audio = byteArrayOf(7, 8, 9)
        val transport = RecordingDoubaoTransport(
            """{"code":0,"message":"OK","data":"${Base64.getEncoder().encodeToString(audio)}"}
               {"code":20000000,"message":"OK"}"""
        )

        val result = DoubaoVoiceTtsApi(transport).synthesize("speech-key", "hello")

        assertArrayEquals(audio, result)
        assertEquals("speech-key", transport.request?.headers?.get("X-Api-Key"))
    }

    @Test
    fun providerErrorIsExposed() {
        val error = runCatching {
            parseDoubaoTtsAudio("""{"code":45000000,"message":"resource not activated"}""")
        }.exceptionOrNull()

        assertTrue(error is DoubaoVoiceException)
        assertTrue(error?.message.orEmpty().contains("45000000"))
        assertTrue(error?.message.orEmpty().contains("resource not activated"))
    }

    private class RecordingDoubaoTransport(
        private val response: String
    ) : DoubaoVoiceHttpTransport {
        var request: DoubaoVoiceHttpRequest? = null

        override suspend fun postStreamingJson(request: DoubaoVoiceHttpRequest): String {
            this.request = request
            return response
        }
    }
}
