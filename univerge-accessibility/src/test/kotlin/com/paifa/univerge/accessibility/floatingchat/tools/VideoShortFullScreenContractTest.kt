package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoShortFullScreenContractTest {
    @Test
    fun videoShortActionOpensItsDedicatedFullScreenMode() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.VideoShort),
            toolActionDispatchFor(FloatingChatToolAction.Video)
        )
    }

    @Test
    fun videoShortTabsCoverVideoAndShortVideo() {
        assertEquals(
            listOf(VideoShortFullScreenTab.Video, VideoShortFullScreenTab.ShortVideo),
            VideoShortFullScreenTab.entries
        )
    }
}
