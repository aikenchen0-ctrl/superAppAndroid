package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class RightRailWorkflowLabelTest {
    @Test
    fun standardRailContainsSeventeenWorkflowTiles() {
        val actions = rightRailScrollableToolActions(emptyList())

        assertEquals(17, actions.size)
        assertEquals("AIFF流程", rightRailWorkflowLabel(actions.first()))
        assertEquals("筛选KOL", rightRailWorkflowLabel(FloatingChatToolAction.Search))
        assertEquals("沟通策略", rightRailWorkflowLabel(FloatingChatToolAction.Contacts))
        assertEquals("钱包", rightRailWorkflowLabel(FloatingChatToolAction.Voice))
    }
}
