package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrmTaskResolutionTest {
    @Test
    fun successAndFailureStatusesResolveToTerminalStatesCaseInsensitively() {
        val success = resolveScrmTaskResult(task(status = "SUCCESS", success = true))
        val failure = resolveScrmTaskResult(task(status = "failed", success = false))

        assertEquals(ScrmTaskPollState.Completed, success.pollState)
        assertEquals(ScrmOutboxState.Succeeded, success.outboxState)
        assertEquals(ScrmTaskPollState.FailedFinal, failure.pollState)
        assertEquals(ScrmOutboxState.FailedFinal, failure.outboxState)
    }

    @Test
    fun unknownResultRequiresManualReviewAndCannotAutoRetry() {
        val resolution = resolveScrmTaskResult(
            task(status = "failed", success = false, resultUnknown = true)
        )

        assertEquals(ScrmTaskPollState.ManualReview, resolution.pollState)
        assertEquals(ScrmOutboxState.Unknown, resolution.outboxState)
    }

    @Test
    fun nonTerminalStatusStaysPendingAndSchedulesNextPoll() {
        val result = task(status = "processing", success = false)
        val resolution = resolveScrmTaskResult(result)
        val record = result.toTaskRecord(
            outboxId = "outbox-1",
            operationType = "message.text",
            now = 1_000L,
            nextPollAt = 2_000L
        )

        assertEquals(ScrmTaskPollState.Pending, resolution.pollState)
        assertEquals(ScrmOutboxState.Processing, resolution.outboxState)
        assertEquals(2_000L, record.nextPollAt)
        assertNull(record.completedAt)
        assertEquals("\"payload\"", record.dataJson)
    }

    private fun task(
        status: String,
        success: Boolean,
        resultUnknown: Boolean = false
    ): ScrmTaskResult {
        return ScrmTaskResult(
            taskId = 42L,
            success = success,
            status = status,
            resultUnknown = resultUnknown,
            resultCode = if (success) "Success" else "InternalError",
            message = null,
            deviceUuid = "device-1",
            connectionIdHash = null,
            receivedAt = "2026-07-12T10:00:00Z",
            rawHidden = true,
            data = JsonPrimitive("payload"),
            taskResultUrl = "/openapi/v1/tasks/42",
            recentTaskResultsUrl = "/openapi/v1/tasks/recent",
            nextStep = null
        )
    }
}
