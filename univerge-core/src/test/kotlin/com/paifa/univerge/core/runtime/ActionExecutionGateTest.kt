package com.paifa.univerge.core.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActionExecutionGateTest {
    @Test
    fun closeBeforeAcquireRejectsNewExecution() {
        val gate = ActionExecutionGate()

        gate.close()

        assertNull(gate.tryAcquire())
    }

    @Test
    fun executionAcquiredBeforeCloseRemainsValidForInFlightWork() {
        val gate = ActionExecutionGate()
        val permit = gate.tryAcquire()

        gate.close()

        assertNotNull(permit)
        permit?.close()
    }
}
