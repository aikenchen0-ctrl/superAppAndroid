package com.paifa.univerge.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：点击右侧“音乐分享”，切换播放器和当前会话，播放/暂停并点击分享；确认本地音乐消息出现，
 * 再点击左上角返回验证自上向下退出动画。
 */
class MusicFullScreenContractTest {
    @Test
    fun musicToolIsMappedToTheMusicWorkspace() {
        val music = rightRailToolCatalog.first { item -> item.label == "音乐分享" }

        assertTrue(music.opensMusic)
        assertNull(music.action)
    }

    @Test
    fun musicWorkspaceUsesFullscreenMaterial3AndEfficientLists() {
        val source = sourceFile().readText()

        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("TopAppBar"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(source.contains("height(30.dp)"))
        assertTrue(source.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("MediaPlayer"))
        assertFalse(source.contains("Dialog("))
        assertFalse(source.contains("WindowManager"))
    }

    @Test
    fun musicEntryCreatesLocalMusicMessageWithoutInventingRemoteEndpoint() {
        val catalogSource = source("floatingchat/tools/RightRailToolCatalog.kt").readText()
        val overlaySource = source("FloatingChatOverlayUi.kt").readText()
        val messageSource = source("floatingchat/message/OutgoingMessageActions.kt").readText()

        assertTrue(catalogSource.contains("37 -> item.copy(opensMusic = true)"))
        assertTrue(overlaySource.contains("MusicFullScreen("))
        assertTrue(overlaySource.contains("sendMusicMessage("))
        assertTrue(messageSource.contains("FloatingChatMessageType.Music"))
        assertFalse(overlaySource.contains("music-card"))
    }

    private fun sourceFile(): File = source("floatingchat/tools/MusicFullScreen.kt")

    private fun source(relativePath: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    )
}
