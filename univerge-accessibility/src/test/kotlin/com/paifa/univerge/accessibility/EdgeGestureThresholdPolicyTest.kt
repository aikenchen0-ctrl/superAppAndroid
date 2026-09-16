package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.floatingchat.shell.floatingChatEdgeGestureLongThresholdPx
import com.paifa.univerge.accessibility.floatingchat.shell.floatingChatEdgeGestureShortThresholdPx
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeGestureThresholdPolicyTest {
    @Test
    fun fallbackOverlayUsesTheConfiguredShortThresholdForVerticalSwipes() {
        assertEquals(
            nativeGestureThresholdPx(thresholdDp = 72, density = 3f),
            edgeOverlayVerticalSwipeThresholdPx(shortThresholdDp = 72, density = 3f),
            0.001f
        )
    }

    @Test
    fun floatingChatUsesTheSameShortAndLongThresholds() {
        assertEquals(
            nativeGestureThresholdPx(thresholdDp = 45, density = 3f),
            floatingChatEdgeGestureShortThresholdPx(shortThresholdDp = 45, density = 3f),
            0.001f
        )
        assertEquals(
            nativeGestureLongThresholdPx(45, 175, 3f),
            floatingChatEdgeGestureLongThresholdPx(45, 175, 3f),
            0.001f
        )
    }
}
