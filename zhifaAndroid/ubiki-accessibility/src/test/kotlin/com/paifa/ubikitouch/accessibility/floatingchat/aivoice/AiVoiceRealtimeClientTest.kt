package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AiVoiceRealtimeClientTest {
    @Test
    fun realtimeRequestUsesOfficialVolcengineHeaders() {
        val request = buildDoubaoRealtimeRequest(
            DoubaoRealtimeCredentials(appId = "1234567890", accessToken = "token")
        )

        assertEquals("https://openspeech.bytedance.com/api/v3/realtime/dialogue", request.url.toString())
        assertEquals("1234567890", request.header("X-Api-App-ID"))
        assertEquals("token", request.header("X-Api-Access-Key"))
        assertEquals("volc.speech.dialog", request.header("X-Api-Resource-Id"))
        assertEquals("PlgvMymc7f3tQnJ6", request.header("X-Api-App-Key"))
    }

    @Test
    fun startConnectionFrameMatchesOfficialBinaryProtocol() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x14, 0x10, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0x7b, 0x7d),
            DoubaoRealtimeProtocol.startConnection()
        )
    }

    @Test
    fun serverConnectionStartedEventIsDecoded() {
        val connectId = "d1dcd999-9a9e-4ed6-b227-8649e946f6c4".encodeToByteArray()
        val frame = ByteBuffer.allocate(4 + 4 + 4 + connectId.size + 4 + 2)
            .order(ByteOrder.BIG_ENDIAN)
            .put(byteArrayOf(0x11, 0x94.toByte(), 0x10, 0))
            .putInt(50)
            .putInt(connectId.size)
            .put(connectId)
            .putInt(2)
            .put(byteArrayOf(0x7b, 0x7d))
            .array()

        assertEquals(50, DoubaoRealtimeProtocol.decode(frame).event)
    }

    @Test
    fun officialTranscriptPayloadsAreParsed() {
        assertEquals(
            "你好",
            parseUserTranscript("""{"results":[{"text":"你好","is_interim":true}]}""".encodeToByteArray())
        )
        assertEquals(
            "你好，我能帮你什么？",
            parseAssistantTranscript("""{"content":"你好，我能帮你什么？"}""".encodeToByteArray())
        )
    }
}
