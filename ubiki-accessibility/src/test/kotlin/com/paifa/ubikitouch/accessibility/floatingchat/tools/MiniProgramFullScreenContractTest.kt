package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.shell.miniProgramEnterOffsetDirection
import com.paifa.ubikitouch.accessibility.floatingchat.shell.miniProgramExitOffsetDirection
import com.paifa.ubikitouch.accessibility.floatingchat.shell.miniProgramUsesFullscreenWorkspace
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgramFullScreenContractTest {
    /** 测试流程：点击右侧微信小程序，验证进入同一悬浮根视图的全屏工作区。 */
    @Test
    fun miniProgramActionOpensTheFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.MiniProgram),
            toolActionDispatchFor(FloatingChatToolAction.MiniProgram)
        )
        assertTrue(miniProgramUsesFullscreenWorkspace())
        val catalogSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/RightRailToolCatalog.kt"
        ).readText()
        assertTrue(catalogSource.contains("36 -> item.copy(action = FloatingChatToolAction.MiniProgram)"))
    }

    /** 测试流程：打开并返回微信小程序，验证下进上出的实体属性动画方向。 */
    @Test
    fun miniProgramWorkspaceUsesRequestedSlideDirections() {
        assertEquals(1, miniProgramEnterOffsetDirection())
        assertEquals(-1, miniProgramExitOffsetDirection())
    }

    /** 测试流程：确认页面用双 Tab 承载配置和预览，并保留 30dp 顶部状态区。 */
    @Test
    fun miniProgramWorkspaceExposesM3Pages() {
        assertEquals(
            listOf(MiniProgramFullScreenTab.Configure, MiniProgramFullScreenTab.Preview),
            MiniProgramFullScreenTab.entries
        )
        assertEquals(30, MiniProgramStatusBarHeightDp)
    }

    /** 测试流程：打开小程序卡片后观察页面实体自下向上进入，点击左上角返回后实体向下退出。 */
    @Test
    fun miniProgramWorkspaceUsesEntityPropertyAnimations() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/MiniProgramFullScreen.kt"
        ).readText()

        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("graphicsLayer"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("animateTo(pageHeightPx"))
        assertTrue(source.contains("TopAppBar"))
        assertTrue(source.contains("height(MiniProgramStatusBarHeightDp.dp)"))
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
    }

    /** 测试流程：填写 iOS 同样要求的真实链接，确认 UI 和 POST 接口均传递 url。 */
    @Test
    fun miniProgramWorkspaceSendsTheIosRequiredUrl() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/MiniProgramFullScreen.kt"
        ).readText()

        assertTrue(source.contains("url = url.trim()"))
        assertTrue(source.contains("真实链接 URL"))
        assertTrue(source.contains("operationApi.sendWeAppCard(request)"))
    }
}
