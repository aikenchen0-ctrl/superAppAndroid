package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RightRailToolPolicyTest {
    @Test
    fun assistantIsPinnedAboveTheScrollableToolList() {
        val actions = listOf(
            FloatingChatToolAction.Assistant,
            FloatingChatToolAction.QuickPhrase,
            FloatingChatToolAction.Moments
        )

        assertTrue(rightRailUsesDedicatedAiEntry())
        assertEquals(FloatingChatToolAction.Assistant, rightRailDedicatedAiAction())
        assertFalse(rightRailScrollableToolActions(actions).contains(FloatingChatToolAction.Assistant))
        assertEquals(
            listOf(FloatingChatToolAction.QuickPhrase, FloatingChatToolAction.Moments),
            rightRailScrollableToolActions(actions)
        )
    }
}
