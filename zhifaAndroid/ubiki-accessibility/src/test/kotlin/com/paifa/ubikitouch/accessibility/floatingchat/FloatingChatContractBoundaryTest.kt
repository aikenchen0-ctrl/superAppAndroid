package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatContractBoundaryTest {
    @Test
    fun shellContractIsPlatformIndependent() {
        val source = sourceFile(
            "contract/FloatingChatShellContract.kt"
        )

        assertTrue("Shell contract source must exist", source.isFile)
        val text = source.readText()
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))
        assertFalse(text.contains("import java.awt."))
    }

    @Test
    fun shellContractDefinesStateEventsAndEffects() {
        val source = sourceFile(
            "contract/FloatingChatShellContract.kt"
        )

        assertTrue("Shell contract source must exist", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("data class FloatingChatShellState"))
        assertTrue(text.contains("sealed interface FloatingChatShellEvent"))
        assertTrue(text.contains("sealed interface FloatingChatEffect"))
        assertTrue(text.contains("enum class FloatingChatPanel"))
    }

    @Test
    fun runtimePortsUseCommandsAndStableIds() {
        val source = sourceFile(
            "shell/FloatingChatRuntimePorts.kt"
        )

        assertTrue("Runtime ports source must exist", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("fun interface FloatingChatWindowPort"))
        assertTrue(text.contains("fun apply(command: FloatingChatWindowCommand)"))
        assertFalse(text.contains("Context"))
        assertFalse(text.contains("Uri"))
        assertFalse(text.contains("Intent"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat",
            relativePath
        )
    }
}
