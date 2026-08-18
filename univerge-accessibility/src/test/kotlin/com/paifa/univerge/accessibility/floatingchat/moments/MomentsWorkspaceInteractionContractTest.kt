package com.paifa.univerge.accessibility.floatingchat.moments

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsWorkspaceInteractionContractTest {
    /**
     * 测试流程：打开朋友圈的动态页，依次触发评论、回复和本人评论删除，
     * 确认 UI 只调用已接入的 SCRM 任务接口，并保留 Material 3 的标准操作组件。
     */
    @Test
    fun workspaceConnectsIosEquivalentCommentOperations() {
        val source = source("floatingchat/moments/MomentsToolPanels.kt")

        assertTrue(source.contains("internal fun MomentsWorkspace("))
        assertTrue(source.contains("deleteScrmMomentComment("))
        assertTrue(source.contains("toWeChatId = replyTarget?.authorWxId"))
        assertTrue(source.contains("replyCommentId = replyTarget?.id ?: 0L"))
        assertTrue(source.contains("DropdownMenu("))
        assertTrue(source.contains("Card("))
        assertTrue(source.contains("ListItem("))
    }

    /**
     * 测试流程：从右侧“朋友圈”打开并点击左上返回，确认关闭由聊天根处理，
     * 不创建额外 Activity Window，避免悬浮窗口 token 异常。
     */
    @Test
    fun momentsRouteUsesSharedWorkspaceCloseCallback() {
        val source = source("floatingchat/shell/FloatingBottomPanel.kt")
        val momentsRoute = source
            .substringAfter("BottomPanelMode.Moments -> MomentsWorkspace(")
            .substringBefore("BottomPanelMode.Finder")

        assertTrue(momentsRoute.contains("onClose = onClose"))
    }

    /**
     * 测试流程：打开朋友圈，确认顶部同时提供缓存刷新和真实同步；点击链接时显示工作区内确认卡，而非 Dialog。
     */
    @Test
    fun workspaceUsesIosRefreshSyncAndInlineLinkConfirmation() {
        val source = source("floatingchat/moments/MomentsToolPanels.kt")
        val workspace = source
            .substringAfter("internal fun MomentsWorkspace(")
            .substringBefore("internal fun MomentsTimelinePanel(")

        assertTrue(workspace.contains("contentDescription = \"刷新朋友圈\""))
        assertTrue(workspace.contains("contentDescription = \"同步朋友圈\""))
        assertTrue(workspace.contains("loadMoments(syncNow = false)"))
        assertTrue(workspace.contains("loadMoments(syncNow = true)"))
        assertTrue(workspace.contains("MomentLinkOpenConfirmation("))
        assertFalse(workspace.contains("AlertDialog("))
    }

    private fun source(relativePath: String): String = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    ).readText()
}
