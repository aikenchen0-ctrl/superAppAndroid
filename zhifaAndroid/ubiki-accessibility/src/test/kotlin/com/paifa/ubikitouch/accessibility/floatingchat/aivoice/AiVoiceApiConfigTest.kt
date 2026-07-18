package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceApiConfigTest {
    @Test
    fun configurationRequiresGatewayBaseUrlAndSessionToken() {
        assertTrue(AiVoiceApiConfig().validationError() != null)
        assertEquals(
            "AI voice gateway base URL is required",
            AiVoiceApiConfig(sessionToken = "token").validationError()
        )
        assertEquals(
            "AI voice session token is required",
            AiVoiceApiConfig(gatewayBaseUrl = "https://voice.example.com").validationError()
        )
    }

    @Test
    fun configurationNormalizesGatewayUrlAndBuildsRealtimeEndpoint() {
        val config = AiVoiceApiConfig(
            gatewayBaseUrl = " https://voice.example.com/ ",
            sessionToken = " token "
        )

        assertEquals("https://voice.example.com", config.normalizedGatewayBaseUrl())
        assertEquals("wss://voice.example.com/realtime", config.realtimeWebSocketUrl())
        assertEquals("token", config.normalizedSessionToken())
    }
}
