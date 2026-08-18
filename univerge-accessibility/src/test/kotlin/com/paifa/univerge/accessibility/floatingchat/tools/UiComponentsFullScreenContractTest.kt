package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.shell.uiComponentsEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.uiComponentsExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.uiComponentsUsesFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiComponentsFullScreenContractTest {
    /** 测试流程：点击右侧“UI组件”，验证它进入全屏悬浮工作区。 */
    @Test
    fun uiComponentsActionOpensTheFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.UiComponents),
            toolActionDispatchFor(FloatingChatToolAction.UiComponents)
        )
        assertTrue(uiComponentsUsesFullscreenWorkspace())
    }

    /** 测试流程：打开后返回，验证页面从下进入并向上退出。 */
    @Test
    fun uiComponentsWorkspaceUsesRequestedSlideDirections() {
        assertEquals(1, uiComponentsEnterOffsetDirection())
        assertEquals(-1, uiComponentsExitOffsetDirection())
    }

    /** 测试流程：检查 iOS OperationLab 与 AppKitRegistry 的内容已映射为 M3 分页清单。 */
    @Test
    fun uiComponentsTabsAndModuleManifestMatchIosFeatureBoundaries() {
        assertEquals(
            listOf(UiComponentsFullScreenTab.Operation, UiComponentsFullScreenTab.Modules),
            UiComponentsFullScreenTab.entries
        )
        assertEquals(30, UiComponentsStatusBarHeightDp)
        assertEquals(6, iosAppKitModuleManifest.size)
    }

    /** 测试流程：打开 UI组件，确认 30dp 位于 TopAppBar padding，而不是独立空白 Spacer。 */
    @Test
    fun uiComponentsStatusAreaIsToolbarPadding() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/UiComponentsFullScreen.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar(title = \"UI组件\", onBack = onBack)"))
        assertFalse(source.contains("Spacer(Modifier.height(UiComponentsStatusBarHeightDp.dp))"))
    }
}
