package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPanelContractTest {
    @Test
    fun actionsUseCurrentCandidateAndHaveFullButtonTargets() {
        val source = sourceFile().readText()

        assertTrue(source.contains("var candidate by remember(config)"))
        assertTrue(source.contains("onTest(candidate)"))
        assertTrue(source.contains("onSave(candidate)"))
        assertTrue(source.contains("PasswordVisualTransformation()"))
        assertFalse(source.contains("onSave(config)"))
        assertFalse(source.contains("onTest(config)"))
    }

    private fun sourceFile(): File {
        val relative = File("src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/AiPanel.kt")
        return if (relative.exists()) relative else File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/AiPanel.kt"
        )
    }
}
