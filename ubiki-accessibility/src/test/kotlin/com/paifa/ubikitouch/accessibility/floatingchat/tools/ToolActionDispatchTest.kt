package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolActionDispatchTest {
    @Test
    fun commandActionOpensTheOpenApiWorkbench() {
        assertEquals(
            ToolActionDispatch.OpenApiWorkbench,
            toolActionDispatchFor(FloatingChatToolAction.Command)
        )
    }

    @Test
    fun finderActionOpensTheFinderPublishActivity() {
        assertEquals(
            ToolActionDispatch.OpenFinderPublish,
            toolActionDispatchFor(FloatingChatToolAction.Finder)
        )
    }
}
