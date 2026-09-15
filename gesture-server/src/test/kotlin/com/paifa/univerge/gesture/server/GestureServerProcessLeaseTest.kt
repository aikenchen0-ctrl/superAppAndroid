package com.paifa.univerge.gesture.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerProcessLeaseTest {
    @Test
    fun leaseIsFreshOnlyInsideItsMonotonicHeartbeatWindow() {
        val lease = GestureServerLease(pid = 42, heartbeatElapsedRealtime = 1_000L)

        assertTrue(isGestureServerLeaseFresh(lease, nowElapsedRealtime = 1_500L, timeoutMs = 1_000L))
        assertFalse(isGestureServerLeaseFresh(lease, nowElapsedRealtime = 2_000L, timeoutMs = 1_000L))
        assertFalse(isGestureServerLeaseFresh(lease, nowElapsedRealtime = 900L, timeoutMs = 1_000L))
        assertFalse(isGestureServerLeaseFresh(lease, nowElapsedRealtime = 1_500L, timeoutMs = 0L))
    }

    @Test
    fun releaseNotificationIsNeededOnlyWhenThisProcessActuallyOwnsTheLease() {
        val own = GestureServerLease(pid = 42, heartbeatElapsedRealtime = 1_000L)
        val other = GestureServerLease(pid = 43, heartbeatElapsedRealtime = 1_000L)

        assertTrue(shouldNotifyGestureServerRelease(own, currentPid = 42))
        assertFalse(shouldNotifyGestureServerRelease(other, currentPid = 42))
        assertFalse(shouldNotifyGestureServerRelease(null, currentPid = 42))
    }
}
