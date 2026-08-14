package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：点击右侧“通讯录”，确认显示可交互的全屏窗口与 30dp 顶部状态区；
 * 切换任意关系分页后点击左上角返回，确认窗口从底部进入并向顶部退出。
 */
class ContactRelationsOverlayPresentationTest {
    @Test
    fun contactRelationsUsesInteractiveFullscreenAccessibilityOverlay() {
        val presentation = contactRelationsOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, contactRelationsStatusBarHeightDp())
    }

    @Test
    fun contactRelationsUsesVerticalPropertyAnimationEndpoints() {
        assertEquals(1_000f, contactRelationsEntryTranslationY(1_000))
        assertEquals(0f, contactRelationsEntryTranslationY(0))
        assertEquals(-1_000f, contactRelationsExitTranslationY(1_000))
        assertEquals(0f, contactRelationsExitTranslationY(0))
    }
}
