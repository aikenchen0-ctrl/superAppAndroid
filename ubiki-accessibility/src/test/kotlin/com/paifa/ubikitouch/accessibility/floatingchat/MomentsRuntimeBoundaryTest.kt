package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class MomentsRuntimeBoundaryTest {
    @Test
    fun momentsContractsDoNotDependOnPlatformOrScrmTypes() {
        val root = locateProjectRoot()
        val contractDir = File(
            root,
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/contract"
        )
        contractDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { source ->
                val text = source.readText()
                assertFalse("platform import leaked into ${source.name}", text.contains("import android."))
                assertFalse("compose import leaked into ${source.name}", text.contains("import androidx.compose."))
                assertFalse("SCRM import leaked into ${source.name}", text.contains("import com.paifa.ubikitouch.accessibility.scrm."))
            }
    }

    private fun locateProjectRoot(): File {
        var current = File(System.getProperty("user.dir"))
        while (!File(current, "src/main/kotlin").isDirectory) {
            val parent = current.parentFile ?: error("module root not found")
            current = parent
        }
        return current
    }
}
