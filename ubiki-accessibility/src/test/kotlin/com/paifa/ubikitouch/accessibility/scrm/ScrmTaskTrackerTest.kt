package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmTaskTrackerTest {
    private var now = 1_000L

    @Test
    fun successfulPollAppliesTaskAndOutboxResolution() {
        val persistence = FakeTaskPersistence(claimedTask())
        val tracker = ScrmTaskTracker(
            api = ResultTaskApi(taskResult(status = "success", success = true)),
            persistence = persistence,
            clockMillis = { now }
        )

        val outcome = tracker.pollNext("worker-a")

        assertEquals(
            ScrmTaskPollOutcome.Updated(
                taskId = 42L,
                pollState = ScrmTaskPollState.Completed,
                outboxState = ScrmOutboxState.Succeeded
            ),
            outcome
        )
        assertEquals(ScrmTaskPollState.Completed, persistence.appliedTask?.pollState)
        assertEquals(3, persistence.appliedTask?.pollCount)
        assertEquals(900L, persistence.appliedTask?.createdAt)
        assertEquals(ScrmOutboxState.Succeeded, persistence.appliedOutboxState)
        assertNull(persistence.rescheduledTaskId)
    }

    @Test
    fun transientNetworkFailureReleasesLeaseAndSchedulesRetry() {
        val persistence = FakeTaskPersistence(claimedTask())
        val tracker = ScrmTaskTracker(
            api = ThrowingTaskApi(ScrmTimeoutException()),
            persistence = persistence,
            clockMillis = { now },
            retryDelayMillis = 5_000L
        )

        val outcome = tracker.pollNext("worker-a")

        assertEquals(ScrmTaskPollOutcome.RetryScheduled(42L, 6_000L), outcome)
        assertEquals(42L, persistence.rescheduledTaskId)
        assertEquals("worker-a", persistence.rescheduledWorkerId)
        assertEquals(6_000L, persistence.rescheduledAt)
        assertEquals("连接 SCRM 服务超时", persistence.rescheduledError)
        assertNull(persistence.appliedTask)
    }

    @Test
    fun authenticationFailureMarksRemoteResultUnknownForManualReview() {
        val persistence = FakeTaskPersistence(claimedTask())
        val tracker = ScrmTaskTracker(
            api = ThrowingTaskApi(ScrmAuthenticationException("unauthorized")),
            persistence = persistence,
            clockMillis = { now }
        )

        val outcome = tracker.pollNext("worker-a")

        assertTrue(outcome is ScrmTaskPollOutcome.Updated)
        assertEquals(ScrmTaskPollState.ManualReview, persistence.appliedTask?.pollState)
        assertEquals(true, persistence.appliedTask?.resultUnknown)
        assertEquals(ScrmOutboxState.Unknown, persistence.appliedOutboxState)
        assertEquals("API Key 无效或已停用", persistence.appliedTask?.lastPollError)
    }

    @Test
    fun noDueTaskDoesNotCallApi() {
        val persistence = FakeTaskPersistence(null)
        val api = CountingTaskApi()
        val tracker = ScrmTaskTracker(api, persistence, clockMillis = { now })

        val outcome = tracker.pollNext("worker-a")

        assertEquals(ScrmTaskPollOutcome.Idle, outcome)
        assertEquals(0, api.callCount)
    }

    @Test
    fun mismatchedTaskIdIsStoredAsManualReviewForClaimedTask() {
        val persistence = FakeTaskPersistence(claimedTask())
        val tracker = ScrmTaskTracker(
            api = ResultTaskApi(taskResult(status = "success", success = true).copy(taskId = 43L)),
            persistence = persistence,
            clockMillis = { now }
        )

        val outcome = tracker.pollNext("worker-a")

        assertTrue(outcome is ScrmTaskPollOutcome.Updated)
        assertEquals(42L, persistence.appliedTask?.taskId)
        assertEquals(ScrmTaskPollState.ManualReview, persistence.appliedTask?.pollState)
        assertEquals(ScrmOutboxState.Unknown, persistence.appliedOutboxState)
        assertEquals("任务结果 ID 不匹配", persistence.appliedTask?.lastPollError)
    }

    private fun claimedTask(): ScrmTaskRecord {
        return ScrmTaskRecord(
            taskId = 42L,
            outboxId = "outbox-1",
            operationType = "message.text",
            status = "queued",
            pollState = ScrmTaskPollState.Pending,
            success = null,
            resultUnknown = false,
            rawHidden = true,
            pollCount = 3,
            nextPollAt = 1_000L,
            leaseOwner = "worker-a",
            leaseUntil = 31_000L,
            createdAt = 900L,
            updatedAt = 1_000L
        )
    }

    private fun taskResult(status: String, success: Boolean): ScrmTaskResult {
        return ScrmTaskResult(
            taskId = 42L,
            success = success,
            status = status,
            resultUnknown = false,
            receivedAt = "2026-07-12T10:00:00Z",
            rawHidden = true
        )
    }

    private class FakeTaskPersistence(
        private var nextClaim: ScrmTaskRecord?
    ) : ScrmTaskPersistence {
        var appliedTask: ScrmTaskRecord? = null
        var appliedOutboxState: ScrmOutboxState? = null
        var rescheduledTaskId: Long? = null
        var rescheduledWorkerId: String? = null
        var rescheduledAt: Long? = null
        var rescheduledError: String? = null

        override fun claimDueTask(
            workerId: String,
            now: Long,
            leaseDurationMillis: Long
        ): ScrmTaskRecord? {
            return nextClaim.also { nextClaim = null }
        }

        override fun applyTaskResult(
            task: ScrmTaskRecord,
            outboxState: ScrmOutboxState
        ): ScrmOutboxItem {
            appliedTask = task
            appliedOutboxState = outboxState
            return outboxStateItem(outboxState)
        }

        override fun rescheduleTaskPoll(
            taskId: Long,
            workerId: String,
            nextPollAt: Long,
            errorMessage: String
        ): ScrmTaskRecord {
            rescheduledTaskId = taskId
            rescheduledWorkerId = workerId
            rescheduledAt = nextPollAt
            rescheduledError = errorMessage
            return claimedTaskForRetry(taskId, nextPollAt, errorMessage)
        }

        private fun outboxStateItem(state: ScrmOutboxState): ScrmOutboxItem {
            return ScrmOutboxItem(
                outboxId = "outbox-1",
                operationType = "message.text",
                aggregateType = "message",
                aggregateId = "message-1",
                accountWeChatId = "wxid-1",
                deviceUuid = "device-1",
                conversationId = "friend-1",
                clientRequestId = "request-1",
                requestJson = "{}",
                state = state,
                createdAt = 900L,
                updatedAt = 1_000L
            )
        }

        private fun claimedTaskForRetry(
            taskId: Long,
            nextPollAt: Long,
            errorMessage: String
        ): ScrmTaskRecord {
            return ScrmTaskRecord(
                taskId = taskId,
                outboxId = "outbox-1",
                operationType = "message.text",
                pollState = ScrmTaskPollState.Pending,
                success = null,
                resultUnknown = false,
                rawHidden = true,
                nextPollAt = nextPollAt,
                lastPollError = errorMessage,
                createdAt = 900L,
                updatedAt = 1_000L
            )
        }
    }

    private class ResultTaskApi(
        private val result: ScrmTaskResult
    ) : ScrmTaskApi {
        override fun getTask(taskId: Long): ScrmTaskResult = result
        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
            error("not used")
        }
    }

    private class ThrowingTaskApi(
        private val error: Exception
    ) : ScrmTaskApi {
        override fun getTask(taskId: Long): ScrmTaskResult = throw error
        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
            error("not used")
        }
    }

    private class CountingTaskApi : ScrmTaskApi {
        var callCount: Int = 0

        override fun getTask(taskId: Long): ScrmTaskResult {
            callCount += 1
            error("not expected")
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
            error("not used")
        }
    }
}
