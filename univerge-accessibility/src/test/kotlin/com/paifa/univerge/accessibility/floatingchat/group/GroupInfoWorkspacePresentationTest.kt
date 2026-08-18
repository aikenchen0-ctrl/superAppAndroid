package com.paifa.univerge.accessibility.floatingchat.group

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupInfoWorkspacePresentationTest {
    /**
     * 测试流程：在群聊中点击“群信息”，确认入口进入聊天根的 surface 全屏工作区，
     * 而不是资料编辑遮罩或新的 Window。
     */
    @Test(timeout = 60_000)
    fun groupInfoUsesTheSharedSurfaceWorkspaceRoute() {
        val overlaySource = sourceFile("FloatingChatOverlayUi.kt")
        val modeSource = sourceFile("floatingchat/shell/BottomPanelMode.kt")
        val panelSource = sourceFile("floatingchat/shell/FloatingBottomPanel.kt")
        val contactOverlaySource = sourceFile("floatingchat/contacts/ContactEditOverlay.kt")

        assertTrue(overlaySource.contains("BottomPanelMode.GroupInfo"))
        assertTrue(overlaySource.contains("groupInfoWorkspaceTarget = group"))
        assertTrue(overlaySource.contains("useFullScreenWorkspace = true"))
        assertTrue(modeSource.contains("GroupInfo"))
        assertTrue(panelSource.contains("BottomPanelMode.GroupInfo"))
        assertTrue(contactOverlaySource.contains("GroupInfoHost("))
        assertTrue(contactOverlaySource.contains("isGroupInfoWorkspace"))
        assertTrue(contactOverlaySource.contains("background(MaterialTheme.colorScheme.surface)"))
        assertTrue(contactOverlaySource.contains("color = if (isGroupInfoWorkspace) MaterialTheme.colorScheme.surface"))
    }

    /**
     * 测试流程：从群信息分别进入成员详情、邀请/移除成员和群管理，确认所有子流程仍在聊天根的
     * surface 全屏工作区内，并使用同一套 M3 返回工具栏，不创建对话框窗口。
     */
    @Test(timeout = 60_000)
    fun groupInfoChildFlowsUseTheSharedFullScreenWorkspace() {
        val memberSource = sourceFile("floatingchat/group/GroupMemberScreen.kt")
        val memberPickerSource = sourceFile("floatingchat/group/GroupMemberSelectionPanel.kt")
        val scrmPreviewSource = sourceFile("floatingchat/group/ScrmGroupOperationPreviewPanel.kt")

        assertTrue(memberSource.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(memberSource.contains("fillMaxSize()"))
        assertTrue(memberSource.contains("background(MaterialTheme.colorScheme.surface)"))
        assertFalse(memberSource.contains("PageBackground"))

        assertTrue(memberPickerSource.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(memberPickerSource.contains("fillMaxSize()"))
        assertFalse(memberPickerSource.contains("heightIn(min = 360.dp, max = 520.dp)"))

        assertTrue(scrmPreviewSource.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(scrmPreviewSource.contains("LazyColumn("))
        assertFalse(scrmPreviewSource.contains("AlertDialog("))
        assertFalse(scrmPreviewSource.contains("FloatingDialogCloseButton"))
    }

    @Test(timeout = 60_000)
    fun groupInfoMatchesTheSinglePageM3Specification() {
        val screenSource = sourceFile("floatingchat/group/GroupInfoScreen.kt")
        val hostSource = sourceFile("floatingchat/group/GroupInfoHost.kt")

        assertTrue(screenSource.contains("title = \"聊天信息${'$'}{state.memberCount}\""))
        assertTrue(screenSource.contains("GridCells.Fixed(GroupInfoGridColumns)"))
        assertTrue(screenSource.contains("private const val GroupInfoGridColumns = 5"))
        assertTrue(screenSource.contains("contentDescription = \"搜索成员\""))
        assertTrue(screenSource.contains("\"查看更多\""))
        assertTrue(screenSource.contains("\"邀请成员\""))
        assertTrue(screenSource.contains("\"群聊名称\""))
        assertTrue(screenSource.contains("\"群聊二维码\""))
        assertTrue(screenSource.contains("\"群公告\""))
        assertTrue(screenSource.contains("\"消息通知\""))
        assertTrue(screenSource.contains("\"显示群成员昵称\""))
        assertTrue(screenSource.contains("\"退出群聊\""))
        assertTrue(screenSource.contains("GroupInfoInputDialog("))
        assertTrue(screenSource.contains("GroupQrCodeScreen("))
        assertTrue(screenSource.contains("GroupMemberSearchScreen("))
        assertFalse(screenSource.contains("TabRow("))
        assertFalse(screenSource.contains("HorizontalPager("))

        assertTrue(hostSource.contains("outcome.data"))
        assertTrue(hostSource.contains("parseGroupQrCodePayload"))
        assertTrue(hostSource.contains("setChatRoomNewMessageNotify"))
        assertTrue(hostSource.contains("setChatRoomTop"))
        assertTrue(hostSource.contains("setChatRoomSavedToPhonebook"))
    }

    /**
     * 测试流程：在群成员页点击“发消息”或点击返回，确认退出动画期间群信息内容和输入栏状态不闪动；
     * 触发眨眼测试时，已有全屏捕获宿主可用则不隐藏当前聊天根。
     */
    @Test(timeout = 60_000)
    fun groupInfoExitAndBlinkCaptureKeepTheFloatingRootStable() {
        val overlaySource = sourceFile("FloatingChatOverlayUi.kt")
        val serviceSource = sourceFile("UbikiAccessibilityService.kt")

        assertTrue(overlaySource.contains("displayedGroupInfoWorkspaceTarget"))
        assertTrue(overlaySource.contains("isGroupInfoWorkspaceVisible"))
        assertTrue(overlaySource.contains("!isGroupInfoWorkspaceVisible"))

        val captureRequest = serviceSource.indexOf("if (FloatingChatBlinkVoiceBridge.requestFullscreenCapture()) return")
        val hideFloatingRoot = serviceSource.indexOf("hideFloatingChatForExternalActivity(\"BlinkVoice\")")
        assertTrue(captureRequest >= 0)
        assertTrue(hideFloatingRoot > captureRequest)
    }

    private fun sourceFile(relativePath: String): String {
        val root = File(System.getProperty("user.dir"), "src/main/kotlin/com/paifa")
        return sequenceOf("univerge", "ubikitouch")
            .map { packageRoot -> File(root, "$packageRoot/accessibility/$relativePath") }
            .first(File::isFile)
            .readText()
    }
}
