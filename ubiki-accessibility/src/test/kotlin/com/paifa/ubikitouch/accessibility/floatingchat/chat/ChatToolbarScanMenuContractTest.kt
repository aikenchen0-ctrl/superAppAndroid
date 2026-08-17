package com.paifa.ubikitouch.accessibility.floatingchat.chat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** toolbar 扫描页的 M3 选项列表必须提供扫码加群入口，并复用扫码启动回调。 */
class ChatToolbarScanMenuContractTest {
    @Test
    fun scanMenuIncludesJoinGroupAction() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()

        assertTrue(source.contains("Icons.Filled.GroupAdd"))
        assertTrue(source.contains("onRequestScan"))
    }

    @Test
    fun scanMenuIncludesCreateGroupActionWithDedicatedCallback() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()

        assertTrue(source.contains("onOpenCreateGroup: () -> Unit"))
        assertTrue(source.contains("Text(\"创建群聊\""))
        assertTrue(source.contains("Modifier.clickable(onClick = onOpenCreateGroup)"))
    }

    @Test
    fun createGroupActionOpensContactsInStartGroupMode() {
        val overlaySource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val bottomPanelSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()

        assertTrue(overlaySource.contains("contactsOpenStartGroup = true"))
        assertTrue(overlaySource.contains("contactsOpenStartGroup = displayedContactsOpenStartGroup"))
        assertTrue(bottomPanelSource.contains("openStartGroup = contactsOpenStartGroup"))
    }

    @Test
    fun startGroupModeUsesSharedFullscreenToolbarAndExistingCreateApi() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/contacts/ScrmContactsPanel.kt"
        ).readText()

        assertTrue(source.contains("openStartGroup: Boolean = false"))
        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("internal fun wechatStartGroupTitle(): String = \"创建群聊\""))
        assertTrue(source.contains("return if (selectedCount > 0) \"确定(${'$'}selectedCount)\" else \"确定\""))
        assertTrue(source.contains("session.chatRoomApi.createChatRoom("))
        assertTrue(source.contains("ScrmCreateChatRoomRequest("))
    }

    @Test
    fun directCreateGroupEntryClosesInsteadOfFallingThroughToContacts() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/contacts/ScrmContactsPanel.kt"
        ).readText()

        assertTrue(source.contains("if (openStartGroup) onClose()"))
        assertTrue(source.contains("if (!openStartGroup) {\n                    panelScreen = WechatContactsPanelScreen.Contacts"))
    }
}
