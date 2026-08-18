package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.shell.isFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageFullScreenContractTest {
    /** 测试流程：点击右侧“语音消息”，确认进入全屏录音工作区而不是普通工具消息。 */
    @Test
    fun voiceMessageEntryOpensVoiceWorkspace() {
        assertEquals(
            "语音消息",
            rightRailToolCatalog.first { item -> item.action == FloatingChatToolAction.Voice }.label
        )
        assertTrue(BottomPanelMode.Voice.isFullscreenWorkspace())
    }

    /** 测试流程：检查全屏实体由下向上进入、由上向下退出。 */
    @Test
    fun voiceMessageUsesRequestedSlideDirections() {
        assertEquals(1, voiceMessageEnterOffsetDirection())
        assertEquals(1, voiceMessageExitOffsetDirection())
    }

    /** 测试流程：核对 M3 Tab/Pager、LazyColumn、30dp 状态栏、属性动画和本地录音链路。 */
    @Test
    fun voiceMessageWorkspaceUsesM3ComponentsAndLocalRecording() {
        val workspace = source("floatingchat/tools/VoiceMessageFullScreen.kt").readText()
        val recorder = source("floatingchat/tools/VoiceInputPanel.kt").readText()
        val overlay = source("FloatingChatOverlayUi.kt").readText()
        val input = source("floatingchat/input/InputMessageActions.kt").readText()
        val api = source("scrm/ScrmApiClient.kt").readText()
        val dispatcher = source("scrm/ScrmOutboxDispatcher.kt").readText()

        assertTrue(workspace.contains("PrimaryTabRow"))
        assertTrue(workspace.contains("HorizontalPager"))
        assertTrue(workspace.contains("LazyColumn"))
        assertTrue(workspace.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(workspace.contains("background(MaterialTheme.colorScheme.surface)"))
        assertTrue(workspace.contains("Animatable").not())
        assertTrue(workspace.contains("graphicsLayer").not())
        assertTrue(workspace.contains("Spacer(Modifier.height(30.dp))").not())
        assertTrue(workspace.contains("RealVoiceInputPanel"))
        assertTrue(workspace.contains("Dialog(").not())
        assertTrue(workspace.contains("WindowManager").not())
        assertTrue(recorder.contains("MediaRecorder"))
        assertTrue(recorder.contains("showCancelButton = true").not())
        assertTrue(overlay.contains("VoiceMessageFullScreen("))
        assertTrue(overlay.contains("closePanel = false"))
        assertTrue(input.contains("closePanel: Boolean = true"))
        assertTrue(api.contains("/openapi/v1/media/voice"))
        assertTrue(api.contains("/openapi/v1/messages/voice"))
        assertTrue(dispatcher.contains("api.uploadVoice"))
        assertTrue(dispatcher.contains("api.sendVoice"))
    }

    private fun source(relativePath: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    )
}
