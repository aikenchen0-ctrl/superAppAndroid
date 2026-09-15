package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTouchCallbackThreadingContractTest {
    @Test
    fun nativeTouchCallbacksAreSerializedThroughTheMainHandler() {
        val source = sourceFile().readText()

        assertTrue(source.contains("import android.os.Handler"))
        assertTrue(source.contains("private val mainHandler = Handler(Looper.getMainLooper())"))
        assertTrue(source.contains("Executor { command -> mainHandler.post(command) }"))
        assertFalse(source.contains("Executor { command -> command.run() }"))
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
