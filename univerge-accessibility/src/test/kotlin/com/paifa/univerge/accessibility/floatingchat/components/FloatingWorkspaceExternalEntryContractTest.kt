package com.paifa.univerge.accessibility.floatingchat.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从右侧依次打开 AI 自动回复、通讯录关系和左侧全部，确认服务不会再创建第二个悬浮 Window，
 * 而是把请求交给已挂载的聊天根工作区。
 */
class FloatingWorkspaceExternalEntryContractTest {
    @Test
    fun commonRightRailEntriesUseTheExistingFloatingChatWorkspace() {
        val service = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/UbikiAccessibilityService.kt"
        ).readText()
        val panelMode = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/BottomPanelMode.kt"
        ).readText()
        val bottomPanel = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()

        assertTrue(service.contains("openWorkspace(BottomPanelMode.AiAutoReply)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.ContactRelations)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.LeftSidebar)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.FriendManagement)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.FavoriteLibrary)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.FinderPublish)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.MaterialLibrary)"))
        assertFalse(service.contains("aiAutoReplyOverlayController.show()"))
        assertFalse(service.contains("contactRelationsOverlayController.show()"))
        assertFalse(service.contains("leftSidebarOverlayController.show()"))
        assertFalse(service.contains("friendManagementOverlayController.show()"))
        assertFalse(service.contains("favoriteLibraryOverlayController.show()"))
        assertFalse(service.contains("finderPublishOverlayController.show()"))
        assertFalse(service.contains("com.paifa.univerge.app.MaterialLibraryActivity"))
        assertTrue(panelMode.contains("AiAutoReply"))
        assertTrue(panelMode.contains("ContactRelations"))
        assertTrue(panelMode.contains("LeftSidebar"))
        assertTrue(panelMode.contains("FriendManagement"))
        assertTrue(panelMode.contains("FavoriteLibrary"))
        assertTrue(panelMode.contains("FinderPublish"))
        assertTrue(panelMode.contains("MaterialLibrary"))
        assertTrue(bottomPanel.contains("BottomPanelMode.AiAutoReply"))
        assertTrue(bottomPanel.contains("BottomPanelMode.ContactRelations"))
        assertTrue(bottomPanel.contains("BottomPanelMode.LeftSidebar"))
        assertTrue(bottomPanel.contains("BottomPanelMode.FriendManagement"))
        assertTrue(bottomPanel.contains("BottomPanelMode.FavoriteLibrary"))
        assertTrue(bottomPanel.contains("BottomPanelMode.FinderPublish"))
        assertTrue(bottomPanel.contains("BottomPanelMode.MaterialLibrary"))
    }
}
