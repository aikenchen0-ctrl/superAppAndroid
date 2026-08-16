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
}
