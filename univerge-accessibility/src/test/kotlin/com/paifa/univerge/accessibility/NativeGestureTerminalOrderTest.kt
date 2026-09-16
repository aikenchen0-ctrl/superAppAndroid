package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGestureTerminalOrderTest {
    @Test
    fun nativeSideUpCommitsActionBeforeVisualCleanup() {
        val source = sourceFile().readText()
        val handleUp = source.substringAfter("private fun handleUp(")
            .substringBefore("private fun backProgressFor(")
        val sideUp = handleUp.substringAfter("val side = signal.side")
        val actionIndex = sideUp.indexOf("onBackGestureCommit(")
        val fallbackActionIndex = sideUp.indexOf("onGesture(side, commit.gesture, commit.action, data)")
        val cleanupIndex = sideUp.indexOf("postVisualTerminal(")
        val terminalImplementation = source.substringAfter("private fun postVisualTerminal(")

        assertTrue("native terminal action callback must exist", actionIndex >= 0)
        assertTrue("native fallback action callback must exist", fallbackActionIndex >= 0)
        assertTrue("native preview cleanup must be enqueued", cleanupIndex >= 0)
        assertTrue("native terminal dispatch must perform preview cleanup", terminalImplementation.contains("onGestureEnd()"))
        assertTrue(
            "native ACTION_UP must dispatch action before cleanup",
            minOf(actionIndex, fallbackActionIndex) < cleanupIndex
        )
    }

    private fun sourceFile(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/univerge/accessibility/NativeEdgeGestureController.kt"
        )
        if (moduleRelative.isFile) return moduleRelative
        return File(
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/NativeEdgeGestureController.kt"
        )
    }
}
