package com.paifa.univerge.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：点击右侧“公众号文章”，确认进入全屏 M3 工作区；填写文章地址、标题和摘要后发送，
 * 切换“当前会话”查看文章记录，最后点击左上角返回确认向下退出动画。
 */
class OfficialArticleFullScreenContractTest {
    @Test
    fun officialArticleWorkspaceUsesFullscreenMaterial3Components() {
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
    }

    @Test
    fun officialArticleUsesCatalogEntryAndOfficialArticleCardApi() {
        val catalogSource = source("floatingchat/tools/RightRailToolCatalog.kt").readText()
        val overlaySource = source("FloatingChatOverlayUi.kt").readText()

        assertTrue(catalogSource.contains("35 -> item.copy(opensOfficialArticle = true)"))
        assertTrue(overlaySource.contains("OfficialArticleFullScreen("))
        assertTrue(overlaySource.contains("sendOfficialArticleCard("))
        assertTrue(overlaySource.contains("source = \"official_article\""))
    }

    private fun sourceFile(): File = source("floatingchat/tools/OfficialArticleFullScreen.kt")

    private fun source(relativePath: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    )
}
