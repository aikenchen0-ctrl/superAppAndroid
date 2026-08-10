package com.paifa.ubikitouch.core.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenInteractiveStateTest {
    @Test
    fun requestsResumeWhenSystemBecomesInteractiveAfterMissedBroadcast() {
        val state = ScreenInteractiveState(initialInteractive = false)

        val shouldResume = state.updateFromSystem(actualInteractive = true)

        assertTrue(shouldResume)
        assertTrue(state.isInteractive)
    }

    @Test
    fun doesNotRequestResumeWhenAlreadyInteractive() {
        val state = ScreenInteractiveState(initialInteractive = true)

        val shouldResume = state.updateFromSystem(actualInteractive = true)

        assertFalse(shouldResume)
        assertTrue(state.isInteractive)
    }

    @Test
    fun screenOffKeepsStateNonInteractiveWithoutResume() {
        val state = ScreenInteractiveState(initialInteractive = true)

        val shouldResume = state.updateFromSystem(actualInteractive = false)

        assertFalse(shouldResume)
        assertFalse(state.isInteractive)
    }
}
