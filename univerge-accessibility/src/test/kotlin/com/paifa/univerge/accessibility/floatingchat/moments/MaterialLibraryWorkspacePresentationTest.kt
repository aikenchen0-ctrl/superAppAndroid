package com.paifa.univerge.accessibility.floatingchat.moments

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从右侧打开朋友圈素材库，依次进入详情和编辑，再触发归档确认。
 * 预期三个页面共用 UI组件 的 M3 toolbar，归档确认以内联状态呈现，不创建 Dialog 窗口。
 */
class MaterialLibraryWorkspacePresentationTest {
    @Test
    fun materialLibrarySubpagesUseTheSharedWorkspaceToolbar() {
        val source = source()

        assertTrue("列表页必须使用共享 toolbar", source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue("详情页必须使用共享 toolbar", detailPage(source).contains("FloatingWorkspaceTopAppBar("))
        assertTrue("编辑页必须使用共享 toolbar", editorPage(source).contains("FloatingWorkspaceTopAppBar("))
        assertFalse("全屏工作区不得使用悬浮 Dialog", source.contains("AlertDialog("))
        assertTrue("归档确认必须为页面内联状态", source.contains("MaterialArchiveConfirmation("))
    }

    @Test
    fun materialLibrarySubpagesKeepTheRemainingViewportForLists() {
        val source = source()

        listOf(detailPage(source), editorPage(source)).forEach { page ->
            assertTrue(page.contains("Modifier.weight(1f).fillMaxWidth()"))
            assertTrue(page.contains("background(MaterialTheme.colorScheme.surface)"))
        }
    }

    private fun detailPage(source: String): String = source
        .substringAfter("private fun MaterialDetailPage")
        .substringBefore("private fun MaterialDetailSection")

    private fun editorPage(source: String): String = source
        .substringAfter("private fun MaterialEditorPage")
        .substringBefore("private fun MaterialDraftSection")

    private fun source(): String = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MaterialLibraryActivityContent.kt"
    ).readText()
}
