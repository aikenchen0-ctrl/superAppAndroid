package com.paifa.ubikitouch.accessibility.floatingchat.input

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BottomBar 语音模式契约：切换、按住录音、取消区域和页面内发送确认必须同时存在。 */
class BottomInputVoiceModeContractTest {
    @Test
    fun voiceModeUsesInlineBoxAndInRootConfirmation() {
        val inputSource = source("src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/input/BottomInputBar.kt")
        val voiceSource = source("src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/input/HoldToRecordVoiceBox.kt")

        assertTrue(inputSource.contains("voiceInputMode: Boolean"))
        assertTrue(inputSource.contains("onVoiceInputModeChange(!voiceInputMode)"))
        assertTrue(inputSource.contains("HoldToRecordVoiceBox("))
        assertTrue(voiceSource.contains("awaitEachGesture"))
        assertTrue(voiceSource.contains("cancelTarget"))
        assertTrue(voiceSource.contains("pending = PendingVoiceRecording"))
        assertTrue(voiceSource.contains("Text(\"发送语音？\""))
        assertTrue(voiceSource.contains("onSendVoice(Uri.fromFile"))
        assertFalse(voiceSource.contains("AlertDialog("))
    }

    private fun source(relativePath: String): String = File(
        System.getProperty("user.dir"),
        relativePath
    ).readText()
}
