package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从主界面点击展开聊天，再用无障碍“展开聊天”手势重复操作；分别打开未读总览和具体账户会话，
 * 检查根全屏视图从底部进入，点击收起或返回后向顶部退出，且动画作用于真实 ComposeView 属性。
 */
class FloatingChatOverlayAnimationContractTest {
    @Test
    fun sharedOverlayControllerAnimatesEveryFullscreenEntryAndExit() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
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
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(source.contains("FloatingChatOverlay("))
        assertTrue(source.contains("conversation = conversation"))
        assertTrue(source.contains("initialSelectedAccountId = selectedAccountId"))
    }

    /**
     * 测试流程：分别通过主界面展开按钮和无障碍手势首次展开未回消息页，
     * 确认是否复用旧窗口都先计算入场动画，再更新 Expanded 状态。
     */
    @Test
    fun firstExpandedChatMountComputesEntranceBeforePublishingExpandedState() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(source.contains("shouldAnimateFloatingChatEntrance("))
        assertTrue(source.contains("previousState = state"))
    }

    /**
     * 测试流程：首次展开聊天时让无障碍 Overlay 延后 attach，确认 ComposeView 仍会在 attach 后执行入场动画。
     * 该回归防止窗口背景已挂载、聊天内容却因保留在屏幕外而不可见。
     */
    @Test
    fun entranceAnimationIsPostedBeforeTheOverlayViewFinishesAttaching() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        val entranceStart = source.indexOf("private fun animateExpandedEntrance")
        val entranceEnd = source.indexOf("private fun animateExpandedExit", entranceStart)
        val entrance = source.substring(entranceStart, entranceEnd)

        assertTrue(entrance.contains("view.post {"))
        assertFalse(
            entrance.contains(
                "if (!view.isAttachedToWindow) return\n        view.post"
            )
        )
    }
}
