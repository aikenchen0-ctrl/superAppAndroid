package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.voiceCallEnterOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.voiceCallExitOffsetDirection
import com.paifa.univerge.accessibility.floatingchat.shell.voiceCallUsesFullscreenWorkspace
import com.paifa.univerge.core.model.FloatingChatToolAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallFullScreenContractTest {
    /** 测试流程：点击右侧语音通话，确认使用当前悬浮根内的全屏通话工作区。 */
    @Test
    fun voiceCallOpensFullscreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(
                com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode.VoiceCall
            ),
            toolActionDispatchFor(FloatingChatToolAction.VoiceCall)
        )
        assertTrue(voiceCallUsesFullscreenWorkspace())
    }

    /** 测试流程：确认全屏实体由下向上进入、由上向下退出。 */
    @Test
    fun voiceCallUsesRequestedSlideDirections() {
        assertEquals(1, voiceCallEnterOffsetDirection())
        assertEquals(-1, voiceCallExitOffsetDirection())
    }

    /** 测试流程：核对 M3 Tab/Pager、LazyColumn、状态栏、属性动画与本地通话记录回写。 */
    @Test
    fun voiceCallWorkspaceUsesM3ComponentsAndCallRecordFlow() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VoiceCallFullScreen.kt"
        ).readText()
        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("VoiceCallStatusBarHeightDp = 30"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY = pageTranslationY.value"))
        assertTrue(source.contains("onEndCall"))
        assertTrue(source.contains("Dialog").not())
        assertTrue(source.contains("WindowManager").not())
    }
}
