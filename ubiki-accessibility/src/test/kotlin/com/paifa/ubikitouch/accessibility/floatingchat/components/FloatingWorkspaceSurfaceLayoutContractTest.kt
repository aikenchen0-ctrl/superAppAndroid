package com.paifa.ubikitouch.accessibility.floatingchat.components

import java.io.File
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.shell.isFullscreenWorkspace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从联系人进入资料页、新朋友页和客户画像页，再进入朋友圈素材库详情/编辑页与视频号。
 * 确认每个二级工作区继续占满悬浮根，并以 Material 3 surface 作为根背景。
 */
class FloatingWorkspaceSurfaceLayoutContractTest {
    @Test
    fun contactWorkspaceChildPagesUseSurfaceAndTheFullAvailableViewport() {
        val contacts = source("contacts/ContactsScreen.kt")
        val profile = source("contacts/ContactProfileScreen.kt")
        val requests = source("contacts/FriendRequestScreen.kt")
        val scrmProfile = source("contacts/ScrmContactProfilePanel.kt")

        listOf(contacts, profile, requests, scrmProfile).forEach { page ->
            assertTrue(page.contains("fillMaxSize()"))
            assertTrue(page.contains("background(MaterialTheme.colorScheme.surface)"))
        }
        assertTrue(
            Regex("weight\\(1f\\)[\\s\\S]{0,80}fillMaxWidth\\(\\)").containsMatchIn(requests)
        )
        assertTrue(scrmProfile.contains("Modifier.weight(1f).fillMaxWidth()"))
    }

    @Test
    fun scrmContactRoutesDoNotRestoreTheLegacyPageBackground() {
        val scrmContacts = source("contacts/ScrmContactsPanel.kt")

        assertTrue(scrmContacts.contains("background(MaterialTheme.colorScheme.surface)"))
        assertTrue(!scrmContacts.contains("background(WechatContactsPageBackground)"))
        assertTrue(
            Regex("fillMaxSize\\(\\)\\s*\\.background\\(MaterialTheme\\.colorScheme\\.surface\\)")
                .findAll(scrmContacts)
                .count() >= 3
        )
    }

    @Test
    fun materialLibrarySubpagesAndFinderAllocateTheRemainingFullscreenSpace() {
        val library = source("moments/MaterialLibraryActivityContent.kt")
        val finder = source("finder/FinderWorkspaceView.kt")

        val detailPage = library.substringAfter("private fun MaterialDetailPage")
            .substringBefore("private fun MaterialDetailSection")
        val editorPage = library.substringAfter("private fun MaterialEditorPage")
            .substringBefore("private fun MaterialDraftSection")

        listOf(detailPage, editorPage).forEach { page ->
            assertTrue(page.contains("background(MaterialTheme.colorScheme.surface)"))
            assertTrue(page.contains("Modifier.weight(1f).fillMaxWidth()"))
        }
        assertTrue(finder.contains("background(MaterialTheme.colorScheme.surface)"))
        assertTrue(finder.contains("Modifier.weight(1f).fillMaxWidth()"))
    }

    /**
     * 测试流程：从右侧功能栏打开 SCRM运营，确认其不是受限的居中面板，
     * 并且工具栏后的操作列表会占满全屏工作区剩余高度。
     */
    @Test
    fun scrmOperationsUsesTheSurfaceFullscreenWorkspace() {
        val panelMode = source("shell/BottomPanelMode.kt")
        val operations = source("scrm/ScrmOperationsHubPanel.kt")

        // SCRM 与其他工作区由集中策略判定，宿主不再维护易遗漏的模式枚举。
        assertTrue(BottomPanelMode.ScrmOperations.isFullscreenWorkspace())
        assertTrue(panelMode.contains("fun BottomPanelMode.isFullscreenWorkspace()"))
        assertTrue(
            operations.contains(
                "Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)"
            )
        )
        assertTrue(operations.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(!operations.contains("height(390.dp)"))
    }

    /**
     * 测试流程：从右侧打开携带名字，确认直连全屏宿主在内容尚未准备完成时也保留 surface，
     * 页面仅由共享 AppBar 提供状态区，并让列表占用工具栏后的剩余高度。
     */
    @Test
    fun directWorkspaceHostAndSendNameKeepSurfaceAndRemainingHeight() {
        val overlay = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val sendName = source("tools/SendNameFullScreen.kt")

        assertTrue(
            Regex(
                "else Modifier\\.fillMaxSize\\(\\)\\.background\\(MaterialTheme\\.colorScheme\\.surface\\)"
            ).containsMatchIn(overlay)
        )
        assertTrue(sendName.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(sendName.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(!sendName.contains("Spacer(Modifier.height(30.dp))"))
    }

    /**
     * 测试流程：依次打开右侧快捷语、名片、群邀请、红包、转账、位置、视频号和网页链接。
     * 这些入口都是完整工作区，不能再被旧的居中面板宽高约束或遮罩分支截断。
     */
    @Test
    fun fullScreenFeatureRoutesAvoidLegacyCenteredPanelSizing() {
        val panelMode = source("shell/BottomPanelMode.kt")
        val bottomPanel = source("shell/FloatingBottomPanel.kt")
        val overlay = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()

        assertTrue(panelMode.contains("fun BottomPanelMode.isFullscreenWorkspace()"))
        assertTrue(bottomPanel.contains("val isFullscreenWorkspace = mode.isFullscreenWorkspace()"))
        assertFalse(bottomPanel.contains("mode.isCenteredToolFeaturePanel()"))
        assertFalse(overlay.contains("displayedBottomPanelMode.isCenteredToolFeaturePanel()"))
        assertTrue(
            overlay.contains(
                "bottomInputBarVisibleForFullscreenWorkspace(bottomPanelMode.isFullscreenWorkspace())"
            )
        )
        listOf(
            BottomPanelMode.Gift,
            BottomPanelMode.ScrmEmoji,
            BottomPanelMode.ScrmWeAppCard,
            BottomPanelMode.ScrmCardTemplates,
            BottomPanelMode.ScrmBatchSend
        ).forEach { mode ->
            assertTrue("$mode must use the full-screen workspace", mode.isFullscreenWorkspace())
        }
    }

    /**
     * 测试流程：从右侧打开 AI 自动回复，确认它和 UI 组件使用相同的 surface 根与共享工具栏。
     */
    @Test
    fun aiConfigWorkspaceUsesTheSharedSurfaceToolbar() {
        val aiPanel = source("tools/AiPanel.kt")

        assertTrue(aiPanel.contains("Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)"))
        assertTrue(aiPanel.contains("FloatingWorkspaceTopAppBar("))
        assertFalse(aiPanel.contains("Box(modifier = Modifier.fillMaxWidth().height(30.dp))"))
        assertFalse(aiPanel.contains("import androidx.compose.material3.TopAppBar"))
        assertFalse(Regex("(?m)^\\s*TopAppBar\\(").containsMatchIn(aiPanel))
    }

    /**
     * 测试流程：从 More 依次打开礼物、收藏表情、小程序卡片、卡片模板和批量发送。
     * 确认每项都占满同一 surface 工作区，并使用共享工具栏与高性能列表承载内容。
     */
    @Test
    fun moreToolWorkspacesUseSurfaceFullscreenRoots() {
        val giftPanel = source("tools/BottomToolPanels.kt")
        val scrmComposer = source("message/ScrmMessageComposerPanel.kt")

        listOf(giftPanel, scrmComposer).forEach { source ->
            assertTrue(
                Regex("fillMaxSize\\(\\)[\\s\\S]{0,120}background\\(MaterialTheme\\.colorScheme\\.surface\\)")
                    .containsMatchIn(source)
            )
            assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
            assertTrue(source.contains("LazyColumn("))
            assertTrue(source.contains("weight(1f)"))
        }
    }

    private fun source(relativePath: String): String {
        return File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/$relativePath"
        ).readText()
    }
}
