package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

internal fun rightRailAssistantOpensAiConfigPanel(): Boolean = true

internal fun aiConfigPanelSupportsConnectionTest(): Boolean = true

internal fun aiConfigTemperatureLabel(): String = "生成内容温度"

internal fun blinkVoiceResultMessageText(eventType: String, durationMs: Long): String {
    val eventLabel = when (eventType) {
        "SINGLE_BLINK" -> "单眨"
        "DOUBLE_BLINK" -> "双眨"
        "LONG_CLOSE" -> "闭眼"
        else -> eventType.ifBlank { "未知" }
    }
    return "眨眼识别：$eventLabel，${durationMs.coerceAtLeast(0L)}ms"
}

internal fun blinkVoiceRecognitionAutoSendsChatMessage(): Boolean = false
