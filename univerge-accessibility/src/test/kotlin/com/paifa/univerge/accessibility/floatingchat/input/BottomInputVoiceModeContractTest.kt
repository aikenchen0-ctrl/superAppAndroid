package com.paifa.univerge.accessibility.floatingchat.input

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BottomBar 语音模式契约：切换、按住录音、取消区域和页面内发送确认必须同时存在。 */
class BottomInputVoiceModeContractTest {
    @Test
    fun voiceModeUsesInlineBoxAndInRootConfirmation() {
        val inputSource = source("src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/input/BottomInputBar.kt")
        val voiceSource = source("src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/input/HoldToRecordVoiceBox.kt")
        val moreButtonSource = source("src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/input/MoreInputButton.kt")
        val overlaySource = source("src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt")

        assertTrue(inputSource.contains("voiceInputMode: Boolean"))
        assertTrue(inputSource.contains("onVoiceInputModeChange(!voiceInputMode)"))
        assertTrue(inputSource.contains("action = if (voiceInputMode) BottomInputAction.Text else BottomInputAction.Voice"))
        assertTrue(inputSource.contains("BottomInputAction.Text -> Icons.Filled.Edit"))
        assertTrue(inputSource.contains("HoldToRecordVoiceBox("))
        assertTrue(voiceSource.contains("awaitEachGesture"))
        assertTrue(voiceSource.contains("cancelTarget"))
        assertTrue(voiceSource.contains("onRecordingReady(PendingVoiceRecording"))
        assertFalse(voiceSource.contains("\"取消发送\""))
        assertFalse(voiceSource.contains("onTranscribe: () -> Unit"))
        assertFalse(voiceSource.contains("statusMessage: String?"))
        val cancelButtonIndex = voiceSource.indexOf("Text(\"取消\")")
        val sendButtonIndex = voiceSource.indexOf("Text(\"发送\")")
        assertTrue(cancelButtonIndex >= 0)
        assertTrue(sendButtonIndex > cancelButtonIndex)
        assertFalse(voiceSource.contains("Text(\"转文字\")"))
        assertTrue(overlaySource.contains("VoiceSendConfirmationOverlay("))
        assertFalse(overlaySource.contains("pendingVoiceTranscriptionError"))
        assertFalse(overlaySource.contains("onTranscribe ="))
        assertFalse(overlaySource.contains("现有转文字接口仅支持已同步的语音消息"))
        assertFalse(overlaySource.contains("发送并同步后，可在语音消息中使用转文字"))
        assertFalse(voiceSource.contains("AlertDialog("))
        assertTrue(inputSource.contains("val iconTint = MaterialTheme.colorScheme.onSurface"))
        assertTrue(moreButtonSource.contains("val iconTint = MaterialTheme.colorScheme.onSurface"))
    }

    private fun source(relativePath: String): String = File(
        System.getProperty("user.dir"),
        relativePath
    ).readText()
}
