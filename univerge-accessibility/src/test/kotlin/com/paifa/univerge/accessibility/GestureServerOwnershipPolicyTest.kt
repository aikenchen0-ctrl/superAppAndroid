package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerOwnershipPolicyTest {
    @Test
    fun freshServerLeaseYieldsInputUnlessFloatingChatOwnsTheSurface() {
        assertTrue(
            shouldYieldToGestureServer(
                gestureServerLeaseActive = true,
                floatingChatOwnsSurface = false
            )
        )
        assertFalse(
            shouldYieldToGestureServer(
                gestureServerLeaseActive = true,
                floatingChatOwnsSurface = true
            )
        )
        assertFalse(
            shouldYieldToGestureServer(
                gestureServerLeaseActive = false,
                floatingChatOwnsSurface = false
            )
        )
    }
}
