package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureInputOwnershipSurfaceTest {
    @Test
    fun expandedFloatingChatKeepsNativeBackendOffWhenItOwnsTheEdgeSurface() {
        assertEquals(false, shouldStartNativeEdgeInput(true, false))
        assertEquals(true, shouldStartNativeEdgeInput(true, true))
        assertEquals(true, shouldStartNativeEdgeInput(false, false))
    }
}
