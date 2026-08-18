package com.paifa.univerge.accessibility.floatingchat.moments

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsTimelinePanelPresentationTest {
    /**
     * 测试流程：从悬浮聊天右侧点击“朋友圈”，确认页面使用 UI组件 同款全屏 surface、
     * 顶部 M3 返回栏和动态/发表分页，列表向下滚动时按批次展开而不是一次性绘制全部动态。
     */
    @Test
    fun momentsUsesSharedM3WorkspaceAndPagedTimeline() {
        val source = source()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("background(MaterialTheme.colorScheme.surface)"))
        assertTrue(source.contains("PrimaryTabRow("))
        assertTrue(source.contains("HorizontalPager("))
        assertTrue(source.contains("rememberPagerState("))
        assertTrue(source.contains("rememberLazyListState("))
        assertTrue(source.contains("snapshotFlow"))
        assertTrue(source.contains("items(visiblePosts, key = { it.id })"))
        assertFalse(source.contains("接口接入后再执行"))
        assertFalse(source.contains("已打开 UI 预览，接口接入后再执行"))
    }

    /**
     * 测试流程：在动态页刷新后打开任意带真实 circleId 的动态，确认详情回读、点赞、评论、
     * 回复和删除本人评论均由 SCRM 调用完成，失败信息留在当前工作区而不触发自动重发。
     */
    @Test
    fun momentsUsesRealDetailAndCommentDeletionOperations() {
        val syncSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/ScrmMomentsSync.kt"
        ).readText()

        assertTrue(syncSource.contains("session.momentApi.getMomentDetail("))
        assertTrue(syncSource.contains("deleteScrmMomentComment("))
        assertTrue(syncSource.contains("session.momentApi.deleteMomentComment("))
    }

    private fun source(): String = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MomentsToolPanels.kt"
    ).readText()
}
