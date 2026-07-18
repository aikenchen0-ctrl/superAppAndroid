package com.paifa.ubikitouch.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatAiVoiceEntryTest {
    @Test
    fun moreToolsExposesAiVoiceAndRoutesToDedicatedPanel() {
        val source = overlaySource().readText()
        val toolsSource = toolsSource().readText()
        val shellSource = shellSource().readText()

        assertTrue(toolsSource.contains("PanelTool(\"AI\""))
        assertTrue(toolsSource.contains("PanelToolAction.AiVoice"))
        assertTrue(source.contains("BottomPanelMode.AiVoice"))
        assertTrue(shellSource.contains("AiVoicePanel("))
        assertTrue(toolsSource.contains("onAiVoiceClick"))
    }

    private fun overlaySource(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        )
        if (moduleRelative.exists()) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        )
    }

    private fun toolsSource(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/BottomToolPanels.kt"
        )
        if (moduleRelative.exists()) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/BottomToolPanels.kt"
        )
    }

    private fun shellSource(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        )
        if (moduleRelative.exists()) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        )
    }
}
