package com.paifa.ubikitouch.accessibility

object FloatingChatBlinkVoiceBridge {
    private var headlessCaptureCloser: (() -> Unit)? = null

    fun requestCapture() {
        UbikiAccessibilityService.instance?.requestFloatingChatBlinkVoiceCapture()
    }

    fun requestHeadlessCapture() {
        if (headlessCaptureCloser != null) return
        UbikiAccessibilityService.instance?.requestFloatingChatBlinkVoiceHeadlessCapture()
    }

    fun stopHeadlessCapture() {
        headlessCaptureCloser?.invoke()
    }

    fun registerHeadlessCaptureCloser(closer: () -> Unit) {
        headlessCaptureCloser = closer
    }

    fun clearHeadlessCaptureCloser(closer: () -> Unit) {
        if (headlessCaptureCloser === closer) {
            headlessCaptureCloser = null
        }
    }

    fun deliverResult(
        eventType: String,
        durationMs: Long,
        confidence: Float,
        headless: Boolean = false
    ) {
        UbikiAccessibilityService.instance?.onFloatingChatBlinkVoiceResult(
            eventType = eventType,
            durationMs = durationMs,
            confidence = confidence,
            headless = headless
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

fun floatingChatInputFocusStartsHeadlessBlinkVoice(): Boolean = true

fun blinkVoiceHeadlessCaptureKeepsFloatingChatVisible(): Boolean = true

fun blinkVoiceHeadlessCaptureStopsWhenInputBlurred(): Boolean = true

fun blinkVoiceHeadlessCaptureStopsAfterFirstRecognizedEvent(): Boolean = true

fun blinkVoiceResultEventMarksHeadlessSource(): Boolean = true

fun blinkVoiceAiGeneratedInputTracksClearableState(): Boolean = true

fun bottomInputBarShowsAiGeneratedClearAction(): Boolean = true

fun aiGeneratedInputClearActionClearsInputAndHint(): Boolean = true

fun blinkVoiceInputStatusUsesFloatingHintBar(): Boolean = true

fun blinkVoiceInputStatusUsesMarquee(): Boolean = true

fun blinkVoiceInputStatusAppearsAboveInputBar(): Boolean = true

fun blinkVoiceInputStatusAutoDismisses(): Boolean = true

fun blinkVoiceInputStatusAutoDismissMs(): Int = BlinkVoiceInputStatusAutoDismissMs

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

const val BlinkVoiceHeadlessExtraName: String =
    "com.paifa.ubikitouch.extra.FLOATING_CHAT_BLINK_HEADLESS"

internal const val BlinkVoiceInputStatusAutoDismissMs: Int = 2600
