package com.paifa.univerge.accessibility

import android.view.WindowManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从悬浮聊天右侧点击“AI自动回复”，确认全屏窗口可交互、保留 30dp 顶部状态区，
 * 再点击左上角返回，确认 View 分别从底部进入、向顶部退出。
 */
class AiAutoReplyOverlayPresentationTest {
    @Test
    fun aiAutoReplyUsesInteractiveFullscreenAccessibilityOverlay() {
        val presentation = aiAutoReplyOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, aiAutoReplyStatusBarHeightDp())
    }

    @Test
    fun aiAutoReplyUsesVerticalPropertyAnimationEndpoints() {
        assertEquals(1_000f, aiAutoReplyEntryTranslationY(1_000))
        assertEquals(0f, aiAutoReplyEntryTranslationY(0))
        assertEquals(-1_000f, aiAutoReplyExitTranslationY(1_000))
        assertEquals(0f, aiAutoReplyExitTranslationY(0))
    }

    @Test
    fun aiAutoReplyScreenIsReusableWhileTheOverlayControllerKeepsUsingIt() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/AiAutoReplyOverlayController.kt"
        ).readText()

        assertTrue(source.contains("internal fun AiAutoReplyFullScreen("))
        assertTrue(source.contains("setContent { AiAutoReplyFullScreen(context = context, onBack = ::dismiss) }"))
    }
}
