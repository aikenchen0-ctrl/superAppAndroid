package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.videoCallEnterOffsetDirection
import com.paifa.ubikitouch.accessibility.floatingchat.shell.videoCallExitOffsetDirection
import com.paifa.ubikitouch.accessibility.floatingchat.shell.videoCallUsesFullscreenWorkspace
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCallFullScreenContractTest {
    /** 测试流程：点击右侧视频通话，确认全屏页复用现有悬浮根而非创建新窗口。 */
    @Test
    fun videoCallOpensFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(
                com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode.VideoCall
            ),
            toolActionDispatchFor(FloatingChatToolAction.VideoCall)
        )
        assertTrue(videoCallUsesFullscreenWorkspace())
    }

    /** 测试流程：确认视频通话实体由下向上进入、由上向下退出。 */
    @Test
    fun videoCallUsesRequestedSlideDirections() {
        assertEquals(1, videoCallEnterOffsetDirection())
        assertEquals(-1, videoCallExitOffsetDirection())
    }

    /** 测试流程：确认 M3 Tab/Pager、LazyColumn、CameraX 预览与结束记录回写。 */
    @Test
    fun videoCallWorkspaceUsesM3CameraPreviewAndCallRecordFlow() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/VideoCallFullScreen.kt"
        ).readText()
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("VideoCallStatusBarHeightDp = 30"))
        assertTrue(source.contains("ProcessCameraProvider"))
        assertTrue(source.contains("PreviewView"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("onEndCall"))
        assertTrue(source.contains("Dialog").not())
        assertTrue(source.contains("WindowManager").not())
    }
}
