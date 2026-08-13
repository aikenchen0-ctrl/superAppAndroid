package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class RightRailWorkflowLabelTest {
    @Test
    fun deviceToolOpensTheAccountDevicePanel() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(
                com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode.AccountDevice
            ),
            toolActionDispatchFor(FloatingChatToolAction.Device)
        )
    }

    @Test
    fun standardRailContainsFinderEntry() {
        val actions = rightRailScrollableToolActions(emptyList())

        assertEquals(18, actions.size)
        assertEquals("视频号", rightRailWorkflowLabel(FloatingChatToolAction.Finder))
        assertEquals("视频号", toolActionLabel(FloatingChatToolAction.Finder))
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(
                com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode.Finder
            ),
            toolActionDispatchFor(FloatingChatToolAction.Finder)
        )
        assertEquals("AIFF流程", rightRailWorkflowLabel(actions.first()))
        assertEquals("筛选KOL", rightRailWorkflowLabel(FloatingChatToolAction.Search))
        assertEquals("沟通策略", rightRailWorkflowLabel(FloatingChatToolAction.Contacts))
        assertEquals("钱包", rightRailWorkflowLabel(FloatingChatToolAction.Voice))
    }
}
