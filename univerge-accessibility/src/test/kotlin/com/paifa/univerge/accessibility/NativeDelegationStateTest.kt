package com.paifa.univerge.accessibility

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
}
