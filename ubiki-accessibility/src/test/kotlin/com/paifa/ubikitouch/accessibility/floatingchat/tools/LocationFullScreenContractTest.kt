package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
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
