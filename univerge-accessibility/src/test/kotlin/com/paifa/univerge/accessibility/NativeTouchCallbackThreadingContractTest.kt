package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeTouchCallbackThreadingContractTest {
    @Test
    fun nativeTouchCallbacksDoNotAddAMainLooperHop() {
        val source = sourceFile().readText()

        assertTrue(
            "Touch callbacks must run on the callback executor so MOVE cannot backlog the main looper",
            source.contains("Executor { command -> command.run() }")
        )
        assertFalse(
            "per-event main-handler hops delay ACTION_UP behind window work",
            source.contains("Executor { command -> mainHandler.post(command) }")
        )
    }

    @Test
    fun visualProgressUsesACoalescedMainDispatch() {
        val source = sourceFile().readText()

        assertTrue(
            "visual MOVE updates must be coalesced before touching WindowManager/Compose",
            source.contains("postCoalescedVisualProgress")
        )
        assertTrue(
            "terminal visual cleanup must have an explicit main-thread boundary",
            source.contains("postVisualTerminal")
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
