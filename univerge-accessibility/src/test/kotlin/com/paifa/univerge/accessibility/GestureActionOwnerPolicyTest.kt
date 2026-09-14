package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionOwnerPolicyTest {
    @Test
    fun expandedFloatingChatAcceptsOnlyItsComposeGestureOwners() {
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.Compose, true, false))
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.ComposeBottom, true, false))
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.Native, true, false))
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.NativeBottom, true, false))
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.Overlay, true, false))
    }

    @Test
    fun collapsedSurfaceRejectsStaleComposeCallbacks() {
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.Compose, false, false))
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.ComposeBottom, false, false))
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.Native, false, false))
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.Overlay, false, false))
    }

    @Test
    fun externalActivityRestoresNativeAndOverlayOwnership() {
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.Native, true, true))
        assertTrue(isGestureActionSourceAllowed(GestureActionSource.Overlay, true, true))
        assertFalse(isGestureActionSourceAllowed(GestureActionSource.Compose, true, true))
    }
}
