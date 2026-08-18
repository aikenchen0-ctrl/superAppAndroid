package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatOverlayMountStateTest {
    @Test
    fun expandedStateIsCommittedOnlyAfterWindowMountSucceeds() {
        assertFalse(shouldCommitFloatingChatExpansion(requestedExpanded = true, mounted = false))
        assertTrue(shouldCommitFloatingChatExpansion(requestedExpanded = true, mounted = true))
        assertFalse(shouldCommitFloatingChatExpansion(requestedExpanded = false, mounted = true))
    }
}
