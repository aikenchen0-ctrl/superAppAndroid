package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

data class AiVoiceApiConfig(
    val gatewayBaseUrl: String = "",
    val sessionToken: String = ""
) {
    fun validationError(): String? {
        if (normalizedGatewayBaseUrl().isBlank()) {
            return "AI voice gateway base URL is required"
        }
        if (normalizedSessionToken().isBlank()) {
            return "AI voice session token is required"
        }
        return null
    }

    fun normalizedGatewayBaseUrl(): String = gatewayBaseUrl.trim().trimEnd('/')

    fun normalizedSessionToken(): String = sessionToken.trim()

    fun realtimeWebSocketUrl(): String {
        val baseUrl = normalizedGatewayBaseUrl()
        return when {
            baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}/realtime"
            baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}/realtime"
            else -> "$baseUrl/realtime"
        }
    }
}
