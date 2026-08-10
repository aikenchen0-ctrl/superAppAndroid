package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDebugChatContractTest {
    @Test
    fun debugHomeProvidesARealFloatingChatExpandEntryPoint() {
        val mainActivitySource = sourceFile("app/src/main/java/com/paifa/ubikitouch/app/MainActivity.kt").readText()
        val serviceSource = sourceFile(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiAccessibilityService.kt"
        ).readText()

        assertTrue(mainActivitySource.contains("BuildConfig.DEBUG"))
        assertTrue(mainActivitySource.contains("\"展开聊天\""))
        assertTrue(mainActivitySource.contains("requestFloatingChatExpandForDebug()"))
        assertTrue(serviceSource.contains("fun requestFloatingChatExpandForDebug(): Boolean"))
        assertTrue(serviceSource.contains("floatingChatOverlayController.expand()"))
    }

    @Test
    fun debugExpandEntryIsPlacedBeforeTheFloatingChatSectionTitle() {
        val mainActivitySource = sourceFile("app/src/main/java/com/paifa/ubikitouch/app/MainActivity.kt").readText()
        val sectionStart = mainActivitySource.indexOf("text = \"悬浮聊天\"")
        val sectionHeader = mainActivitySource.substring(sectionStart - 700, sectionStart)

        assertTrue(sectionHeader.contains("DebugFloatingChatExpandButton()"))
    }

    private fun sourceFile(path: String): File {
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
