package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从右侧“图片”进入全屏工作区，确认 Material 3 分页、30dp 状态区和实体属性动画；
 * 点击选择后走现有真实媒体桥接，系统选择器返回 URI 后再回到聊天层。
 */
class GalleryFullScreenContractTest {
    @Test
    fun galleryActionOpensTheFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Gallery),
            toolActionDispatchFor(com.paifa.univerge.core.model.FloatingChatToolAction.Gallery)
        )
    }

    @Test
    fun galleryWorkspaceUsesMaterialTabsLazyListAndRealPickerBridge() {
        val source = sourceFile().readText()
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("FloatingChatMediaPickerBridge.requestPick"))
        assertTrue(source.contains("PickedMediaKind.Image"))
        assertTrue(source.contains("TopAppBar"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(source.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("FontWeight.Normal"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
    }

    @Test
    fun galleryWorkspaceKeepsFullscreenGeometryAndRequestedMotion() {
        assertEquals(30, GalleryStatusBarHeightDp)
        assertEquals(1, galleryEnterOffsetDirection())
        assertEquals(1, galleryExitOffsetDirection())
    }

    private fun sourceFile(): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/GalleryFullScreen.kt"
    ).also { file ->
        assertTrue("图片全屏工作区必须存在", file.isFile)
    }
}
