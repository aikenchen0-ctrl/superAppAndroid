package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoicePanelContractTest {
    @Test
    fun panelScrollsWhenConfigurationExceedsAvailableHeight() {
        val text = sourceFile("AiVoicePanel.kt").readText()

        assertTrue(text.contains("rememberScrollState()"))
        assertTrue(text.contains(".verticalScroll(scrollState)"))
    }

    @Test
    fun panelRendersEveryStateAndUsesCentralizedTokens() {
        val text = sourceFile("AiVoicePanel.kt").readText()

        listOf(
            "fun AiVoicePanel(",
            "capabilityConfigState: AiVoiceCapabilityConfigState",
            "onCapabilityConfigEvent: (AiVoiceCapabilityConfigEvent) -> Unit",
            "豆包语音 API Key",
            "一键测试全部能力",
            "CapabilityStatusRow",
            "AiVoiceEvent.PreviewGeneratedVoice",
            "AiVoiceEvent.SendGeneratedVoice",
            "试听",
            "发送",
            "AiVoiceState.Menu",
            "is AiVoiceState.CreatingVoiceProfile",
            "is AiVoiceState.GeneratingMessage",
            "is AiVoiceState.RealtimeCall",
            "AiVoiceState.VoiceAgents",
            "is AiVoiceState.Failed",
            "AiVoiceTokens"
        ).forEach { expected -> assertTrue("Missing $expected", text.contains(expected)) }
        assertFalse(Regex("""\d+\.sp""").containsMatchIn(text))
        assertFalse(text.contains("Color("))
    }

    @Test
    fun typingUsesLocalDraftAndConfigurationIsProgressivelyDisclosed() {
        val text = sourceFile("AiVoicePanel.kt").readText()

        assertTrue(text.contains("var draft by remember(state.feature)"))
        assertTrue(text.contains("onValueChange = { draft = it }"))
        assertTrue(text.contains("onEvent(AiVoiceEvent.MessageTextChanged(draft))"))
        assertTrue(text.contains("Text(\"语音配置\")"))
        assertFalse(text.contains("Text(\"编辑\")"))
        assertTrue(text.contains("Text(\"保存\")"))
    }

    @Test
    fun assistantSubtitleClockDoesNotRestartForEveryIncomingTextChunk() {
        val text = sourceFile("AiVoicePanel.kt").readText()

        assertTrue(text.contains("rememberUpdatedState(target)"))
        assertTrue(text.contains("LaunchedEffect(active)"))
        assertFalse(text.contains("LaunchedEffect(target, active)"))
    }

    @Test
    fun voiceInputPanelContainsReadableChineseLabels() {
        val text = sourceFile("../tools/VoiceInputPanel.kt").readText()

        listOf("语音输入", "点击开始录音", "停止发送", "需要麦克风权限").forEach {
            assertTrue("Missing readable label: $it", text.contains(it))
        }
    }

    private fun sourceFile(name: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice",
            name
        )
        if (moduleRelative.exists()) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice",
            name
        )
    }
}
