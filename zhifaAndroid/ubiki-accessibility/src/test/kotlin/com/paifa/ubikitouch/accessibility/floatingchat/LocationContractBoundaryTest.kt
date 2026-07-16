package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class LocationContractBoundaryTest {
    @Test
    fun locationContractHasNoPlatformImports() {
        val root = locateModuleRoot()
        val source = File(
            root,
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/contract/ToolContract.kt"
        ).readText()
        assertFalse(source.contains("import android."))
        assertFalse(source.contains("import androidx.compose."))
        assertFalse(source.contains("LocationManager"))
        assertFalse(source.contains("Context"))
    }

    private fun locateModuleRoot(): File {
        var current = File(System.getProperty("user.dir"))
        while (!File(current, "src/main/kotlin").isDirectory) {
            val parent = current.parentFile ?: error("module root not found")
            current = parent
        }
        return current
    }
}
