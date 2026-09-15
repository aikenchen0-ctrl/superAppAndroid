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

    @Test
    fun serverLeaseRejectsLateMainServiceTerminals() {
        assertFalse(
            isGestureActionSourceAllowed(
                source = GestureActionSource.Native,
                floatingChatExpanded = false,
                externalActivityVisible = false,
                gestureServerOwnsInput = true
            )
        )
        assertFalse(
            isGestureActionSourceAllowed(
                source = GestureActionSource.Overlay,
                floatingChatExpanded = false,
                externalActivityVisible = false,
                gestureServerOwnsInput = true
            )
        )
        assertTrue(
            isGestureActionSourceAllowed(
                source = GestureActionSource.Command,
                floatingChatExpanded = false,
                externalActivityVisible = false,
                gestureServerOwnsInput = true
            )
        )
    }

    @Test
    fun serverForwardedActionsRequireTheActiveServerLease() {
        assertTrue(
            isGestureActionSourceAllowed(
                source = GestureActionSource.Server,
                floatingChatExpanded = false,
                externalActivityVisible = false,
                gestureServerOwnsInput = true
            )
        )
        assertFalse(
            isGestureActionSourceAllowed(
                source = GestureActionSource.Server,
                floatingChatExpanded = false,
                externalActivityVisible = false,
                gestureServerOwnsInput = false
            )
        )
    }
}
