package com.paifa.univerge.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：点击右侧“网页链接”，确认进入全屏工作区；填写 http/https 链接并发送，
 * 再切换到“当前会话”查看网页链接记录，最后使用左上角返回验证向下退出动画。
 */
class WebLinkFullScreenContractTest {
    @Test
    fun webLinkWorkspaceUsesTheRequestedFullscreenMaterial3Contract() {
        val source = sourceFile().readText()

        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("TopAppBar"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(source.contains("height(30.dp)"))
        assertTrue(source.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("FontWeight.Normal"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertFalse(source.contains("Dialog("))
        assertFalse(source.contains("WindowManager"))
    }

    @Test
    fun webLinkUsesIndependentActionModeAndRealLinkCardApi() {
        val actionSource = source("floatingchat/tools/ToolActionDispatch.kt").readText()
        val catalogSource = source("floatingchat/tools/RightRailToolCatalog.kt").readText()
        val orderSource = source("floatingchat/tools/ToolActionOrderStore.kt").readText()
        val overlaySource = source("FloatingChatOverlayUi.kt").readText()

        assertTrue(actionSource.contains("FloatingChatToolAction.WebLink"))
        assertTrue(actionSource.contains("BottomPanelMode.WebLink"))
        assertTrue(catalogSource.contains("34 -> item.copy(action = FloatingChatToolAction.WebLink)"))
        assertTrue(orderSource.contains("FloatingChatToolAction.WebLink"))
        assertTrue(overlaySource.contains("WebLinkFullScreen("))
        assertTrue(overlaySource.contains("sendLinkCard("))
        assertTrue(overlaySource.contains("ScrmSendLinkCardMessageRequest("))
    }

    private fun sourceFile(): File = source("floatingchat/tools/WebLinkFullScreen.kt").also { file ->
        assertTrue("网页链接全屏工作区必须存在", file.isFile)
    }

    private fun source(relativePath: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    )
}
