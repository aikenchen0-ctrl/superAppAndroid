package com.paifa.univerge.accessibility

import android.view.WindowManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：打开悬浮聊天右侧“左侧全部”，确认全屏页包含 30dp 状态区和左上返回；
 * 在“预览”和“显示设置”分页间切换，分别选择全部、好友、群聊后关闭页面，确认动画方向正确。
 */
class LeftSidebarOverlayPresentationTest {
    @Test
    fun leftSidebarUsesInteractiveFullscreenAccessibilityOverlay() {
        val presentation = leftSidebarOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, leftSidebarStatusBarHeightDp())
    }

    @Test
    fun leftSidebarUsesVerticalPropertyAnimationEndpoints() {
        assertEquals(1_000f, leftSidebarEntryTranslationY(1_000))
        assertEquals(0f, leftSidebarEntryTranslationY(0))
        assertEquals(-1_000f, leftSidebarExitTranslationY(1_000))
        assertEquals(0f, leftSidebarExitTranslationY(0))
    }

    @Test
    fun leftSidebarDisplayModeMatchesIosRawValues() {
        assertEquals("friendsAndGroups", LeftSidebarDisplayMode.All.rawValue)
        assertEquals("friends", LeftSidebarDisplayMode.Friends.rawValue)
        assertEquals("groups", LeftSidebarDisplayMode.Groups.rawValue)
        assertEquals(LeftSidebarDisplayMode.All, LeftSidebarDisplayMode.fromRawValue("unknown"))
    }

    @Test
    fun leftSidebarFullscreenProvidesAnInternalReusableComposableEntry() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/LeftSidebarOverlayController.kt"
        ).readText()

        assertTrue(source.contains("internal fun LeftSidebarFullScreen(onBack: () -> Unit)"))
        assertFalse(source.contains("private fun LeftSidebarFullScreen(onBack: () -> Unit)"))
        assertTrue(source.contains("setContent { LeftSidebarFullScreen(onBack = ::dismiss) }"))
    }
}
