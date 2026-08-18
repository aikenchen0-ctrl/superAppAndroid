package com.paifa.univerge.app

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
            "src/main/java/com/paifa/univerge/app/FloatingChatCameraActivity.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("FilledTonalIconButton"))
        assertTrue(source.contains("MaterialTheme.colorScheme"))
        assertTrue(source.contains("floatingWorkspaceSurfaceBackdrop()"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surface"))
        assertTrue(source.contains("FloatingWorkspaceMotion.enterTranslationY"))
        assertTrue(source.contains("FloatingWorkspaceMotion.exitTranslationY"))
        assertTrue(source.contains("animateCameraEntrance"))
        assertTrue(source.contains("CAMERA_WORKSPACE_ANIMATION_MS"))
        assertTrue(source.contains("PreviewView.ImplementationMode.COMPATIBLE"))
        assertTrue(source.contains("private var cameraProvider: ProcessCameraProvider? = null"))
        assertTrue(source.contains("cameraProvider?.unbindAll()"))
        assertTrue(source.contains("CameraX failed to initialize"))
    }
}
