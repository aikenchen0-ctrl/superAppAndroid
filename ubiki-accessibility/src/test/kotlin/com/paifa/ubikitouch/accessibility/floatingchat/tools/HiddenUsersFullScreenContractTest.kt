package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenUsersFullScreenContractTest {
    @Test
    fun fullScreenHiddenUsersTabsCoverHiddenListAndManagement() {
        assertEquals(
            listOf(HiddenUsersFullScreenTab.Hidden, HiddenUsersFullScreenTab.Manage),
            HiddenUsersFullScreenTab.entries
        )
    }

    @Test
    fun hiddenUsersActionOpensItsDedicatedFullScreenMode() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.HiddenUsers),
            toolActionDispatchFor(FloatingChatToolAction.HiddenUsers)
        )
    }
}
