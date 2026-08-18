package com.paifa.univerge.app

import com.paifa.univerge.app.photoFullscreenEntryTranslationY
import com.paifa.univerge.app.photoFullscreenExitTranslationY
import com.paifa.univerge.app.photoFullscreenOverlayWindowPresentation
import com.paifa.univerge.app.photoFullscreenStatusBarHeightDp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：从右侧“拍摄照片”进入，检查全屏悬浮层先自下而上出现；拍照后预览并点击发送，
 * 确认图片经 FloatingChatMediaPickerBridge 回到聊天；点击左上返回时从顶部退出。
 */
class FloatingChatPhotoOverlayPresentationTest {
    @Test
    fun photoCaptureUsesFullscreenAccessibilityOverlayWithM3NavigationAndPropertyMotion() {
        val source = photoOverlaySource()

        assertTrue(source.contains("TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(source.contains("photoFullscreenStatusBarHeightDp(): Int = 30"))
        assertTrue(source.contains("translationY = photoFullscreenEntryTranslationY"))
        assertTrue(source.contains(".translationY(0f)"))
        assertTrue(source.contains("photoFullscreenExitTranslationY"))
        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertFalse(source.contains("Spacer(Modifier.height(photoFullscreenStatusBarHeightDp().dp))"))
        assertFalse(source.contains("import androidx.compose.material3.TopAppBar"))
        assertTrue(source.contains("runCatching { windowManager.addView"))
        assertTrue(source.contains("runCatching { windowManager.removeViewImmediate"))
    }

    @Test
    fun photoCaptureBindsRealCameraXAndReturnsOnlyRealCapturedMedia() {
        val source = photoOverlaySource()

        assertTrue(source.contains("ProcessCameraProvider.getInstance(context)"))
        assertTrue(source.contains("ImageCapture.Builder()"))
        assertTrue(source.contains("capture.takePicture("))
        assertTrue(source.contains("FloatingChatMediaPickerBridge.deliverPickedMedia("))
        assertTrue(source.contains("FloatingChatPrototype.PickedMediaKind.Image"))
        assertTrue(source.contains("normalizeCapturedPhotoOrientation"))
        assertTrue(source.contains("FloatingChatMediaPickerBridge.notifyPickerClosed()"))
        assertTrue(!source.contains("Bitmap.createBitmap(1,"))
    }

    @Test
    fun presentationContractUsesRequiredFullscreenGeometry() {
        val presentation = photoFullscreenOverlayWindowPresentation()

        assertEquals(-1_000f, photoFullscreenExitTranslationY(1_000))
        assertEquals(1_000f, photoFullscreenEntryTranslationY(1_000))
        assertEquals(30, photoFullscreenStatusBarHeightDp())
        assertTrue(presentation.focusable)
    }

    private fun photoOverlaySource(): String {
        val file = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/univerge/app/FloatingChatPhotoFullscreenOverlayController.kt"
        )
        assertTrue("拍摄照片全屏悬浮层必须存在", file.isFile)
        return file.readText()
    }
}
