package com.paifa.univerge.accessibility

import android.accessibilityservice.TouchInteractionController
import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDelegationStateTest {
    @Test
    fun failedDelegationRequestDoesNotSilentlyDropTheNextEvent() {
        assertFalse(shouldMarkNativeTouchDelegated(requestSucceeded = false, platformIsDelegating = false))
        assertTrue(shouldMarkNativeTouchDelegated(requestSucceeded = true, platformIsDelegating = false))
        assertTrue(shouldMarkNativeTouchDelegated(requestSucceeded = false, platformIsDelegating = true))
    }

    @Test
    fun lateNonDownEventFromAnOlderSessionIsIgnored() {
        assertTrue(
            shouldIgnoreOutOfOrderNativeEvent(
                action = MotionEvent.ACTION_UP,
                eventTime = 90L,
                sessionStartTime = 100L,
                lastAcceptedEventTime = 120L
            )
        )
        assertFalse(
            shouldIgnoreOutOfOrderNativeEvent(
                action = MotionEvent.ACTION_MOVE,
                eventTime = 120L,
                sessionStartTime = 100L,
                lastAcceptedEventTime = 120L
            )
        )
        assertFalse(
            shouldIgnoreOutOfOrderNativeEvent(
                action = MotionEvent.ACTION_DOWN,
                eventTime = 90L,
                sessionStartTime = 100L,
                lastAcceptedEventTime = 120L
            )
        )
    }

    @Test
    fun terminalPlatformStatesAbortAnActiveNativeSession() {
        assertTrue(
            shouldAbortNativeSessionForState(
                state = TouchInteractionController.STATE_DELEGATING,
                sessionActive = true
            )
        )
        assertTrue(
            shouldAbortNativeSessionForState(
                state = TouchInteractionController.STATE_CLEAR,
                sessionActive = true
            )
        )
        assertFalse(
            shouldAbortNativeSessionForState(
                state = TouchInteractionController.STATE_TOUCH_INTERACTING,
                sessionActive = true
            )
        )
        assertFalse(
            shouldAbortNativeSessionForState(
                state = TouchInteractionController.STATE_CLEAR,
                sessionActive = false
            )
        )
    }

}
