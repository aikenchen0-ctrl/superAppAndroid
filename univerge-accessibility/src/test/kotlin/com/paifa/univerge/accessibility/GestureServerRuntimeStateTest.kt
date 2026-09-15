package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerRuntimeStateTest {
    @Test
    fun serverInputIsEnabledOnlyWhenEveryRuntimeGateIsOpen() {
        val enabled = serverGestureInputEnabled(
            globalEnabled = true,
            screenInteractive = true,
            packageBlocked = false,
            landscapeDisabled = false,
            keyboardDisabled = false,
            paused = false
        )
        assertTrue(enabled)

        val disabledCases = listOf(
            { serverGestureInputEnabled(false, true, false, false, false, false) },
            { serverGestureInputEnabled(true, false, false, false, false, false) },
            { serverGestureInputEnabled(true, true, true, false, false, false) },
            { serverGestureInputEnabled(true, true, false, true, false, false) },
            { serverGestureInputEnabled(true, true, false, false, true, false) },
            { serverGestureInputEnabled(true, true, false, false, false, true) }
        )
        disabledCases.forEach { check -> assertFalse(check()) }
    }
}
