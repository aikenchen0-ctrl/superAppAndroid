package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.shell.isFullscreenWorkspace
import com.paifa.univerge.accessibility.floatingchat.shell.transferEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.transferExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.transferUsesFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFullScreenContractTest {
    /** 测试流程：点击右侧“转账”，确认路由进入专用全屏工作区。 */
    @Test
    fun transferEntryUsesFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Transfer),
            toolActionDispatchFor(FloatingChatToolAction.Transfer)
        )
        assertTrue(transferUsesFullscreenWorkspace())
        assertTrue(BottomPanelMode.Transfer.isFullscreenWorkspace())
    }

    /** 测试流程：检查 M3 分页、列表、30dp 状态区及实体 translationY 动画契约。 */
    @Test
    fun transferWorkspaceUsesMaterialComponentsAndSlideAnimation() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/TransferFullScreen.kt"
        ).readText()

        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("background(MaterialTheme.colorScheme.surface)"))
        assertFalse(source.contains("Animatable"))
        assertFalse(source.contains("graphicsLayer"))
        assertFalse(source.contains("Spacer(Modifier.height(30.dp))"))
        assertFalse(source.contains("Dialog("))
        assertFalse(source.contains("WindowManager"))

        val shell = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()
        assertTrue(shell.contains("BottomPanelMode.Transfer -> TransferFullScreen"))
        assertTrue(shell.contains("onSubmit = onSendTransfer"))

        val messageActions = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolMessageActions.kt"
        ).readText()
        assertTrue(messageActions.contains("onBottomPanelModeChanged(BottomPanelMode.Transfer)"))
        assertFalse(messageActions.contains("onTransferRequested()"))
    }

    /** 测试流程：确认转账进入和退出都使用实体位移动画方向。 */
    @Test
    fun transferSlideDirectionsMatchWorkspaceContract() {
        assertEquals(1, transferEnterOffsetDirection())
        assertEquals(-1, transferExitOffsetDirection())
    }
}
