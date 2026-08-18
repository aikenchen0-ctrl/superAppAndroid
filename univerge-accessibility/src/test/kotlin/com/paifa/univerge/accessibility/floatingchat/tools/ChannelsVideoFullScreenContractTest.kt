package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelsVideoFullScreenContractTest {
    @Test
    fun channelsVideoActionOpensItsDedicatedFullScreenMode() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.ChannelsVideo),
            toolActionDispatchFor(FloatingChatToolAction.ChannelsVideo)
        )
    }

    @Test
    fun rightRailChannelsVideoEntryUsesTheDedicatedAction() {
        assertEquals(
            FloatingChatToolAction.ChannelsVideo,
            rightRailToolCatalog[28].action
        )
    }
}
