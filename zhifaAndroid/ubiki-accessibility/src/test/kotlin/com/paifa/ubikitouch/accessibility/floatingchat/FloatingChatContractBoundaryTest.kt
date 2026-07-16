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

    @Test
    fun mediaContractUsesStableIdsWithoutPlatformTypes() {
        val source = sourceFile("contract/MediaContract.kt")

        assertTrue("Media contract source must exist", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("sealed interface MediaUiEvent"))
        assertTrue(text.contains("interface MediaPlatformPort"))
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))
        assertFalse(text.contains("Context"))
        assertFalse(text.contains("Uri"))
        assertFalse(text.contains("Intent"))
    }

    @Test
    fun mediaEventsAndAndroidRuntimeAdapterCoverPlatformActions() {
        val mediaContract = sourceFile("contract/MediaContract.kt")
        val shellContract = sourceFile("contract/FloatingChatShellContract.kt")
        val runtime = sourceFile("shell/AndroidFloatingChatRuntimePorts.kt")

        assertTrue(mediaContract.readText().contains("data class PickerClicked"))
        assertTrue(mediaContract.readText().contains("data object CameraClicked"))
        assertTrue(mediaContract.readText().contains("data object PreviewDismissed"))
        assertTrue(shellContract.readText().contains("data object OpenCamera"))
        assertTrue(shellContract.readText().contains("data object CloseMediaPreview"))
        assertTrue("Android runtime adapter source must exist", runtime.isFile)
        assertTrue(runtime.readText().contains("class AndroidFloatingChatRuntimePorts"))
        assertTrue(runtime.readText().contains("fun handle(effect: FloatingChatEffect)"))
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
