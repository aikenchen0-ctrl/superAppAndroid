package com.paifa.univerge.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDebugChatContractTest {
    @Test
    fun debugHomeProvidesARealFloatingChatExpandEntryPoint() {
        val mainActivitySource = sourceFile("app/src/main/java/com/paifa/univerge/app/MainActivity.kt").readText()
        val serviceSource = sourceFile(
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
        ).readText()

        assertTrue(mainActivitySource.contains("BuildConfig.DEBUG"))
        assertTrue(mainActivitySource.contains("\"展开聊天\""))
        assertTrue(mainActivitySource.contains("requestFloatingChatExpandForDebug()"))
        assertTrue(serviceSource.contains("fun requestFloatingChatExpandForDebug(): Boolean"))
        assertTrue(serviceSource.contains("floatingChatOverlayController.expand()"))
    }

    @Test
    fun debugExpandEntryIsPlacedBeforeTheFloatingChatSectionTitle() {
        val mainActivitySource = sourceFile("app/src/main/java/com/paifa/univerge/app/MainActivity.kt").readText()
        val sectionStart = mainActivitySource.indexOf("text = \"悬浮聊天\"")
        val sectionHeader = mainActivitySource.substring(sectionStart - 700, sectionStart)

        assertTrue(sectionHeader.contains("DebugFloatingChatExpandButton()"))
    }

    @Test
    fun debugHomeOpensTheFullWidthTouchTestActivityBelowChatExpand() {
        val mainActivitySource = sourceFile("app/src/main/java/com/paifa/univerge/app/MainActivity.kt").readText()
        val manifestSource = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val activitySource = sourceFile("app/src/main/java/com/paifa/univerge/app/TouchTestActivity.kt").readText()

        val expandIndex = mainActivitySource.indexOf("DebugFloatingChatExpandButton()")
        val hapticIndex = mainActivitySource.indexOf("DebugHapticTestButton()")
        assertTrue(hapticIndex > expandIndex)
        assertTrue(mainActivitySource.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(mainActivitySource.contains("Text(\"触感测试\")"))
        assertTrue(mainActivitySource.contains("TouchTestActivity::class.java"))
        assertTrue(manifestSource.contains("com.paifa.univerge.app.TouchTestActivity"))
        assertTrue(activitySource.contains("pointerInteropFilter"))
        assertTrue(activitySource.contains("handleMotionEvent"))
        assertTrue(activitySource.contains("selectedModelInfo"))
        assertTrue(activitySource.contains("Icons.AutoMirrored.Outlined.ArrowBack"))
    }

    @Test
    fun touchTestShowsExplicitChineseGestureClassification() {
        val activitySource = sourceFile("app/src/main/java/com/paifa/univerge/app/TouchTestActivity.kt").readText()

        assertTrue(activitySource.contains("点击"))
        assertTrue(activitySource.contains("重触"))
        assertTrue(activitySource.contains("按压"))
        assertTrue(activitySource.contains("长按"))
        assertTrue(activitySource.contains("轻触"))
        assertTrue(!activitySource.contains("拖动"))
    }

    private fun sourceFile(path: String): File {
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
