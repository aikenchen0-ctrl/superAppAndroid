package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.fileDocumentEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.fileDocumentExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.fileDocumentUsesFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDocumentFullScreenContractTest {
    /** 测试流程：点击右侧“文件/文档”，确认入口先打开当前悬浮根内的全屏工作区。 */
    @Test
    fun filesOpenTheFullscreenWorkspace() {
        assertEquals(ToolActionDispatch.PickDocument, toolActionDispatchFor(FloatingChatToolAction.Files))
        assertTrue(fileDocumentUsesFullscreenWorkspace())
        val panelSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()
        assertTrue(panelSource.contains("BottomPanelMode.FileDocument -> FileDocumentFullScreen"))
    }

    /** 测试流程：打开并返回文件工作区，确认实体从底部进入、从顶部方向退出。 */
    @Test
    fun fileDocumentUsesRequestedSlideDirections() {
        assertEquals(1, fileDocumentEnterOffsetDirection())
        assertEquals(-1, fileDocumentExitOffsetDirection())
    }

    /** 测试流程：检查 M3 Tab/Pager、LazyColumn、30dp 状态区和 translationY 属性动画。 */
    @Test
    fun fileDocumentWorkspaceUsesM3PagerAndLazyLists() {
        assertEquals(2, FileDocumentTab.entries.size)
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/FileDocumentFullScreen.kt"
        ).readText()
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("FileDocumentStatusBarHeightDp = 30"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("graphicsLayer"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("onPickDocument"))
        assertTrue(source.contains("Dialog").not())
        assertTrue(source.contains("WindowManager").not())
        val overlaySource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        assertTrue(overlaySource.contains("onPickDocument = { FloatingChatMediaPickerBridge.requestDocumentPick() }"))
    }
}
