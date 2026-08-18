package com.paifa.univerge.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：点击右侧“视频号直播”，确认进入全屏工作区；切换“直播素材”和“当前会话”，
 * 填写已同步朋友圈编号后创建直播素材草稿，再使用左上角返回退出。
 */
class ChannelsLiveFullScreenContractTest {
    @Test
    fun channelsLiveWorkspaceUsesTheRequestedFullscreenMaterial3Contract() {
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
    }

    @Test
    fun channelsLiveUsesItsOwnActionModeAndDocumentedMaterialDraftApi() {
        val actionSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolActionDispatch.kt"
        ).readText()
        val apiSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/scrm/ScrmApiClient.kt"
        ).readText()

        assertTrue(actionSource.contains("FloatingChatToolAction.ChannelsLive"))
        assertTrue(actionSource.contains("BottomPanelMode.ChannelsLive"))
        assertTrue(apiSource.contains("moments/\$snsId/copy-finder-material"))
        assertTrue(apiSource.contains("copyMomentToFinderMaterial"))
    }

    private fun sourceFile(): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ChannelsLiveFullScreen.kt"
    ).also { file ->
        assertTrue("视频号直播全屏工作区必须存在", file.isFile)
    }
}
