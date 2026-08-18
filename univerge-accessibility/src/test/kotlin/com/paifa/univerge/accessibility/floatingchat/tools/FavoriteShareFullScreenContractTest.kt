package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.favoriteShareEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.favoriteShareExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.favoriteShareUsesFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteShareFullScreenContractTest {
    /** 测试流程：点击右侧收藏分享，确认仍在当前悬浮根中打开全屏收藏工作区。 */
    @Test
    fun favoriteShareOpensTheFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenFavoriteLibrary,
            toolActionDispatchFor(FloatingChatToolAction.Favorite)
        )
        assertTrue(favoriteShareUsesFullscreenWorkspace())
        val panelSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()
        assertTrue(panelSource.contains("BottomPanelMode.Favorite -> FavoriteShareFullScreen"))
    }

    /** 测试流程：打开与返回收藏分享，确认页面实体自下进入、向下退出。 */
    @Test
    fun favoriteShareUsesRequestedSlideDirections() {
        assertEquals(1, favoriteShareEnterOffsetDirection())
        assertEquals(-1, favoriteShareExitOffsetDirection())
    }

    /** 测试流程：检查六个 iOS 分类由 M3 Tab/Pager 承载，每页使用高性能 LazyColumn。 */
    @Test
    fun favoriteShareWorkspaceUsesM3PagerAndLazyLists() {
        assertEquals(6, FavoriteShareTab.entries.size)
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/FavoriteShareFullScreen.kt"
        ).readText()
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("FavoriteShareStatusBarHeightDp = 30"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("graphicsLayer"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("animateTo(pageHeightPx"))
        assertTrue(source.contains("fontWeight = FontWeight.Normal"))
    }
}
