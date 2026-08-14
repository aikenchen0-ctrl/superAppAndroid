package com.paifa.ubikitouch.app

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test flow: create the window contract, then verify fullscreen geometry, accessibility type,
 * focusability, status-bar spacing, and the two translation endpoints used by the property animation.
 */
class BlinkVoiceFullscreenOverlayPresentationTest {
    @Test
    fun blinkVoiceUsesAnInteractiveFullscreenAccessibilityOverlay() {
        val presentation = blinkVoiceFullscreenOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, blinkVoiceFullscreenStatusBarHeightDp())
    }

    @Test
    fun overlayMotionSlidesUpOnEntryAndDownOnExit() {
        assertEquals(1_000f, blinkVoiceFullscreenEntryTranslationY(1_000))
        assertEquals(-1_000f, blinkVoiceFullscreenExitTranslationY(1_000))
    }

    /**
     * 测试流程：从右侧眨眼测试图标打开页面，确认状态区由工具栏内边距承载；
     * 再点击左上返回，确认进出场均复用 UI组件 工作区的实体位移动画规格。
     */
    @Test
    fun blinkVoiceReusesTheFloatingWorkspaceToolbarAndMotionContract() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/BlinkVoiceFullscreenOverlayController.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("FloatingWorkspaceMotion.enterTranslationY"))
        assertTrue(source.contains("FloatingWorkspaceMotion.exitTranslationY"))
        assertFalse(source.contains("Spacer(Modifier.height(blinkVoiceFullscreenStatusBarHeightDp().dp))"))
    }

    /** 测试流程：从右侧打开智能抠图与 OpenAPI，确认两者均由统一悬浮工作区承载，而非普通 Activity 窗口。 */
    @Test
    fun rightRailActivityEntriesDelegateToTheExistingFloatingChatWorkspace() {
        val backgroundRemoval = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/BackgroundRemovalActivity.kt"
        ).readText()
        val openApi = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/OpenApiWorkbenchActivity.kt"
        ).readText()

        assertTrue(backgroundRemoval.contains("FloatingChatBackgroundRemovalBridge.open()"))
        assertTrue(openApi.contains("FloatingChatOpenApiBridge.open()"))
        assertFalse(backgroundRemoval.contains("FloatingWorkspaceOverlayHost.show"))
        assertFalse(openApi.contains("FloatingWorkspaceOverlayHost.show"))
    }

    /**
     * 测试流程：分别打开智能抠图与眨眼测试，确认全屏悬浮 View 的根容器透出下层聊天界面，
     * 而输入区域和 M3 卡片仍由各自组件负责绘制背景。
     */
    @Test
    fun activityWorkspaceRootsKeepTheOverlayBackgroundTransparent() {
        val backgroundRemoval = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/BackgroundRemovalActivity.kt"
        ).readText()
        val blinkVoice = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/BlinkVoiceFullscreenOverlayController.kt"
        ).readText()

        assertTrue(backgroundRemoval.contains("Color.Transparent"))
        assertTrue(blinkVoice.contains("Color.Transparent"))
    }
}
