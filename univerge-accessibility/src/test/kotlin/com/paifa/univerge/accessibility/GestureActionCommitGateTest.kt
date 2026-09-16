package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionCommitGateTest {
    @Test
    fun acceptsOnlyTheFirstTerminalActionForAGestureId() {
        val gate = GestureActionCommitGate(maxEntries = 4)

        assertTrue(gate.accept(41L))
        assertFalse(gate.accept(41L))
        assertTrue(gate.accept(42L))
    }

    @Test
    fun zeroIsReservedForNonGestureActionsAndIsNotDeduplicated() {
        val gate = GestureActionCommitGate(maxEntries = 4)

        assertTrue(gate.accept(0L))
        assertTrue(gate.accept(0L))
    }

    @Test
    fun oldIdsAreEvictedWithoutBlockingNewGestureTransactions() {
        val gate = GestureActionCommitGate(maxEntries = 2)

        assertTrue(gate.accept(1L))
        assertTrue(gate.accept(2L))
        assertTrue(gate.accept(3L))
        assertTrue(gate.accept(1L))
    }

    @Test
    fun sameGestureIdFromDifferentOwnersIsNotDeduplicated() {
        val gate = GestureActionCommitGate(maxEntries = 4)

        assertTrue(gate.accept(GestureActionSource.Native, 41L))
        assertFalse(gate.accept(GestureActionSource.Native, 41L))
        assertTrue(gate.accept(GestureActionSource.Server, 41L))
        assertFalse(gate.accept(GestureActionSource.Server, 41L))
    }

    @Test
    fun releaseAllowsRetryWhenDispatchWasRejected() {
        val gate = GestureActionCommitGate(maxEntries = 4)

        assertTrue(gate.accept(GestureActionSource.Native, 41L))
        gate.release(GestureActionSource.Native, 41L)

        assertTrue(gate.accept(GestureActionSource.Native, 41L))
    }
}
