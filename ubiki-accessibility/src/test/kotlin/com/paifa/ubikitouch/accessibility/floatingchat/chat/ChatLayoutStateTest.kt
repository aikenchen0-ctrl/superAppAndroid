package com.paifa.ubikitouch.accessibility.floatingchat.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLayoutStateTest {
    @Test
    fun selectedRailActionsAppearOnlyAfterFiveSecondsWithoutScroll() {
        assertFalse(leftRailSelectionActionsVisible(4_999L, isSelected = true, isScrolling = false))
        assertTrue(leftRailSelectionActionsVisible(5_000L, isSelected = true, isScrolling = false))
        assertFalse(leftRailSelectionActionsVisible(5_000L, isSelected = true, isScrolling = true))
        assertFalse(leftRailSelectionActionsVisible(6_000L, isSelected = false, isScrolling = false))
    }
}
