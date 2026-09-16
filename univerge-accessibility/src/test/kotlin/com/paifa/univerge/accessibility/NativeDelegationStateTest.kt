package com.paifa.univerge.accessibility

import android.accessibilityservice.TouchInteractionController
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDelegationStateTest {
    @Test
    fun nativeVerticalThresholdUsesTheSharedShortPullContract() {
        assertEquals(31.5f, nativeGestureVerticalThresholdPx(45, 1f), 0.001f)
        assertEquals(63f, nativeGestureVerticalThresholdPx(45, 2f), 0.001f)
    }

    @Test
    fun nativeLongThresholdUsesTheConfiguredLongPullDistance() {
        assertEquals(122.5f, nativeGestureLongThresholdPx(45, 175, 1f), 0.001f)
        assertEquals(245f, nativeGestureLongThresholdPx(45, 175, 2f), 0.001f)
    }

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
        assertTrue(
            shouldIgnoreOutOfOrderNativeEvent(
                action = MotionEvent.ACTION_UP,
                eventTime = 130L,
                sessionStartTime = 100L,
                lastAcceptedEventTime = 120L,
                sessionDownTime = 10L,
                eventDownTime = 9L
            )
        )
        assertFalse(
            shouldIgnoreOutOfOrderNativeEvent(
                action = MotionEvent.ACTION_MOVE,
                eventTime = 130L,
                sessionStartTime = 100L,
                lastAcceptedEventTime = 120L,
                sessionDownTime = 10L,
                eventDownTime = 10L
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
    fun bottomTerminalWatchdogOnlyRunsForTheCurrentActiveBottomSession() {
        assertTrue(
            shouldRunNativeBottomTerminalWatchdog(
                watchdogGeneration = 4L,
                currentGeneration = 4L,
                sessionActive = true,
                bottomGestureActive = true
            )
        )
        assertFalse(
            shouldRunNativeBottomTerminalWatchdog(
                watchdogGeneration = 3L,
                currentGeneration = 4L,
                sessionActive = true,
                bottomGestureActive = true
            )
        )
        assertFalse(
            shouldRunNativeBottomTerminalWatchdog(
                watchdogGeneration = 4L,
                currentGeneration = 4L,
                sessionActive = false,
                bottomGestureActive = true
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
