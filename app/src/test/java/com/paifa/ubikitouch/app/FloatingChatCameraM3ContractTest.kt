package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从悬浮聊天顶部进入扫一扫，确认相机页使用 Material 3 工具栏和高对比图标按钮，
 * 不回退为旧的自绘顶栏或低对比文字操作。
 */
class FloatingChatCameraM3ContractTest {
    @Test
    fun scannerUsesMaterial3ToolbarAndHighContrastActions() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/FloatingChatCameraActivity.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("FilledTonalIconButton"))
        assertTrue(source.contains("MaterialTheme.colorScheme"))
        assertTrue(source.contains("floatingWorkspaceSurfaceBackdrop()"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surface"))
    }
}
