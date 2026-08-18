package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFullScreenContractTest {
    @Test
    fun locationActionUsesTheDedicatedFullScreenMode() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Location),
            toolActionDispatchFor(FloatingChatToolAction.Location)
        )
    }

    @Test
    fun locationFullScreenUsesCurrentAndSearchPages() {
        assertEquals(
            listOf(LocationFullScreenTab.CurrentLocation, LocationFullScreenTab.Search),
            LocationFullScreenTab.entries
        )
    }

}
