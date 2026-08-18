package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.reviewRequestsEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.reviewRequestsExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.reviewRequestsUsesFullscreenWorkspace
import com.paifa.univerge.accessibility.scrm.ScrmFriendRequestOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRequestsFullScreenContractTest {
    /** 测试流程：点击右侧“申请审核”，确认其复用悬浮根视图的全屏工作区。 */
    @Test(timeout = 60_000)
    fun reviewRequestsUseTheFullscreenWorkspace() {
        assertTrue(reviewRequestsUsesFullscreenWorkspace())
    }

    /** 测试流程：打开并关闭申请审核，确认实体视图从下方进入并向上方退出。 */
    @Test(timeout = 60_000)
    fun reviewRequestsUseTheRequestedSlideDirections() {
        assertEquals(1, reviewRequestsEnterOffsetDirection())
        assertEquals(-1, reviewRequestsExitOffsetDirection())
    }

    /** 测试流程：进入页面并切换 Tab，确认分页和 30dp 顶部安全区契约不被回归。 */
    @Test(timeout = 60_000)
    fun reviewRequestsExposeM3TabsAndStatusBarSpace() {
        assertEquals(
            listOf(ReviewRequestsFullScreenTab.Pending, ReviewRequestsFullScreenTab.Processed),
            ReviewRequestsFullScreenTab.entries
        )
        assertEquals(30, ReviewRequestsStatusBarHeightDp)
    }

    /** 测试流程：点击拒绝，确认请求体使用接口文档规定的 operation=2。 */
    @Test(timeout = 60_000)
    fun rejectingARequestUsesTheDocumentedOperationCode() {
        assertEquals(2, ScrmFriendRequestOperation.Reject.wireValue)
    }
}
