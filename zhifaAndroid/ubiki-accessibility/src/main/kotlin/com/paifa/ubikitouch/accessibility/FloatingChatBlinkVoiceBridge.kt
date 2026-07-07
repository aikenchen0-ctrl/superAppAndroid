package com.paifa.ubikitouch.accessibility

object FloatingChatBlinkVoiceBridge {
    fun requestCapture() {
        UbikiAccessibilityService.instance?.requestFloatingChatBlinkVoiceCapture()
    }

    fun deliverResult(
        eventType: String,
        durationMs: Long,
        confidence: Float
    ) {
        UbikiAccessibilityService.instance?.onFloatingChatBlinkVoiceResult(
            eventType = eventType,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    fun notifyCaptureClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatBlinkVoiceClosed()
    }
}

internal fun blinkVoiceBridgeActivityClassName(): String = BlinkVoiceBridgeActivityClassName

fun blinkVoiceCaptureAutoFinishOnEvent(): Boolean = false

fun blinkVoiceCaptureUsesFloatingWindow(): Boolean = true

fun blinkVoiceRealtimeStatusUsesChineseText(): Boolean = true

fun blinkVoiceCaptureClosesOnlyOnManualExit(): Boolean = true

fun blinkVoiceRecognizedEventTypes(): List<String> {
    return listOf("SINGLE_BLINK", "DOUBLE_BLINK", "LONG_CLOSE")
}

fun blinkVoiceRealtimeStatusLabel(eventType: String?): String {
    return when (eventType) {
        "SINGLE_BLINK" -> "识别到单眨"
        "DOUBLE_BLINK" -> "识别到双眨"
        "LONG_CLOSE" -> "识别到长闭眼"
        else -> "等待眨眼识别"
    }
}

fun blinkVoiceStatusLogEntry(eventType: String): String = blinkVoiceRealtimeStatusLabel(eventType)

internal const val BlinkVoiceBridgeActivityClassName: String =
    "com.paifa.ubikitouch.app.FloatingChatBlinkVoiceActivity"
