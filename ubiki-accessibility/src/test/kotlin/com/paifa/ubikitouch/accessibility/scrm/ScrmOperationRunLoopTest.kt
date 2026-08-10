package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrmOperationRunLoopTest {
    private var now = 1_000L

    @Test
    fun requestRunSchedulesImmediateProcessingAndNextWake() {
        val scheduler = RecordingScheduler()
        val processor = RecordingProcessor(
            ScrmOperationProcessorResult(
                dispatchedCount = 1,
                polledCount = 0,
                nextWakeAt = 6_000L
            ),
            ScrmOperationProcessorResult(
                dispatchedCount = 0,
                polledCount = 1,
                nextWakeAt = null
            )
        )
        val loop = ScrmOperationRunLoop(
            processorFactory = { processor },
            scheduler = scheduler,
            clockMillis = { now },
            workerId = "worker-a",
            onProcessed = { result -> processedResults.add(result) }
        )

        loop.requestRun()
        scheduler.runNext()
        now = 6_000L
        scheduler.runNext()

        assertEquals(listOf(0L, 5_000L), scheduler.delays)
        assertEquals(listOf("worker-a", "worker-a"), processor.workerIds)
        assertEquals(2, processedResults.size)
        assertEquals(1, processedResults.first().dispatchedCount)
        assertEquals(1, processedResults.last().polledCount)
    }

    private val processedResults = mutableListOf<ScrmOperationProcessorResult>()

    private class RecordingProcessor(
        private vararg val results: ScrmOperationProcessorResult
    ) : ScrmDueOperationProcessor {
        val workerIds = mutableListOf<String>()
        private var index = 0

        override fun runDue(workerId: String): ScrmOperationProcessorResult {
            workerIds += workerId
            return results[index++]
        }
    }

    private class RecordingScheduler : ScrmOperationRunScheduler {
        val delays = mutableListOf<Long>()
        private val operations = ArrayDeque<() -> Unit>()

        override fun schedule(delayMillis: Long, operation: () -> Unit) {
            delays += delayMillis
            operations += operation
        }

        fun runNext() {
            operations.removeFirst().invoke()
        }
    }
}
