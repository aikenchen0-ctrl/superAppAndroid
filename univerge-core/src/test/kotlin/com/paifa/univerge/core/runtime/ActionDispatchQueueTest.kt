package com.paifa.univerge.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatchQueueTest {
    @Test
    fun submitDoesNotRunActionInline() {
        val queued = mutableListOf<Runnable>()
        var executed = false
        val queue = ActionDispatchQueue<Unit>(
            executor = { command -> queued += command },
            action = { executed = true }
        )

        assertTrue(queue.submit(Unit))
        assertFalse(executed)
        queued.single().run()
        assertTrue(executed)
    }

    @Test
    fun closeRejectsNewWorkButDoesNotDiscardAlreadySubmittedWork() {
        val queued = mutableListOf<Runnable>()
        var executed = 0
        val queue = ActionDispatchQueue<Unit>(
            executor = { command -> queued += command },
            action = { executed += 1 }
        )

        assertTrue(queue.submit(Unit))
        queue.close()

        assertFalse(queue.submit(Unit))
        queued.single().run()

        assertTrue("accepted work must survive close", executed == 1)
    }

    @Test
    fun boundedQueueRejectsExcessPendingWorkAndRecoversAfterExecution() {
        val queued = mutableListOf<Runnable>()
        val queue = ActionDispatchQueue<Unit>(
            executor = { command -> queued += command },
            action = {},
            maxPending = 1
        )

        assertTrue(queue.submit(Unit))
        assertFalse(queue.submit(Unit))
        queued.single().run()
        assertTrue(queue.submit(Unit))
    }
}
