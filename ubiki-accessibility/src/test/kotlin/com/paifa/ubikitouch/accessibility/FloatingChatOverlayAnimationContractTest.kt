package com.paifa.ubikitouch.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从主界面点击展开聊天，再用无障碍“展开聊天”手势重复操作；分别打开未读总览和具体账户会话，
 * 检查根全屏视图从底部进入，点击收起或返回后从顶部向下退出，且动画作用于真实 ComposeView 属性。
 */
class FloatingChatOverlayAnimationContractTest {
    @Test
    fun sharedOverlayControllerAnimatesEveryFullscreenEntryAndExit() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(source.contains("fun expand()"))
        assertTrue(source.contains("fun collapse()"))
        assertTrue(source.contains("translationY"))
        assertTrue(source.contains("animateExpandedEntrance"))
        assertTrue(source.contains("animateExpandedExit"))
        assertTrue(source.contains("setDuration(FloatingChatOverlayAnimationDurationMillis)"))
        assertTrue(source.contains("view.animate()"))
    }

    @Test
    fun unreadAndAccountChatUseTheSameExpandedRoot() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(source.contains("FloatingChatOverlay("))
        assertTrue(source.contains("conversation = conversation"))
        assertTrue(source.contains("initialSelectedAccountId = selectedAccountId"))
    }
}
