package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarWorkspaceContractTest {
    @Test
    fun toolbarWorkspaceProvidesSearchScanAndAddFriendFlows() {
        assertEquals(
            listOf(
                ToolbarWorkspaceMode.Search,
                ToolbarWorkspaceMode.Scan,
                ToolbarWorkspaceMode.AddFriend
            ),
            ToolbarWorkspaceMode.entries
        )
    }

    @Test
    /**
     * 测试流程：从未回消息总览和具体账号会话分别打开搜索、扫码，确认页面复用 UI组件
     * 的 surface M3 toolbar，30dp 顶部空间由 toolbar 内嵌，而不是独立状态栏占位或页面级动画。
     */
    fun toolbarWorkspaceUsesSharedUiComponentsPresentationAndExistingFriendApi() {
        val workspaceSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()
        val overlaySource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val controllerSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(workspaceSource.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(workspaceSource.contains("background(MaterialTheme.colorScheme.surface)"))
        assertFalse(workspaceSource.contains("Spacer(Modifier.height(30.dp))"))
        assertFalse(workspaceSource.contains("Animatable"))
        assertFalse(workspaceSource.contains("graphicsLayer"))
        assertTrue(workspaceSource.contains("LazyColumn("))
        assertTrue(overlaySource.contains("BottomPanelMode.ToolbarSearch"))
        assertTrue(overlaySource.contains("BottomPanelMode.ToolbarScan"))
        assertTrue(overlaySource.contains("ToolbarWorkspaceFullScreen("))
        assertTrue(overlaySource.contains("contactApi.addFriendsByPhone("))
        assertTrue(overlaySource.contains("contactApi.addFriend("))
        assertTrue(controllerSource.contains("FloatingWorkspaceMotion.enterTranslationY"))
        assertTrue(controllerSource.contains("FloatingWorkspaceMotion.exitTranslationY"))
    }
}
