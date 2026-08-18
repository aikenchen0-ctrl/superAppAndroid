package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedPacketFullScreenContractTest {
    /** 测试流程：点击右侧“红包”，应在既有悬浮根内打开全屏工作区。 */
    @Test
    fun redPacketOpensDedicatedFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.RedPacket),
            toolActionDispatchFor(FloatingChatToolAction.RedPacket)
        )

        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RedPacketFullScreen.kt"
        )
        assertTrue("红包全屏工作区源码必须存在", source.isFile)
    }

    /** 测试流程：检查 M3 分页、列表、状态栏与实体属性动画，且不允许使用 Dialog/Window。 */
    @Test
    fun redPacketWorkspaceUsesRequiredMaterial3BuildingBlocks() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RedPacketFullScreen.kt"
        ).readText()

        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("height(30.dp)"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY"))
        assertFalse(source.contains("Dialog("))
        assertFalse(source.contains("WindowManager"))
    }

    /** 测试流程：右侧入口不得跳转旧 Activity，必须沿 BottomPanelMode.RedPacket 进入悬浮根页面。 */
    @Test
    fun redPacketToolActionDoesNotBypassTheOverlayWorkspace() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolMessageActions.kt"
        ).readText()

        assertFalse(source.contains("onRedPacketRequested"))
    }
}
