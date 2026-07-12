package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrmOperationProcessorTest {
    private var now = 1_000L

    @Test
    fun runDueDispatchesQueuedOutboxAndPollsSubmittedTaskWhenDue() {
        val persistence = FakeOperationPersistence(
            outbox = textOutbox(),
            clockMillis = { now }
        )
        val processor = ScrmOperationProcessor(
            dispatcher = ScrmOutboxDispatcher(
                api = AcceptingMessageApi(),
                persistence = persistence,
                clockMillis = { now },
                taskPollDelayMillis = 5_000L
            ),
            taskTracker = ScrmTaskTracker(
                api = SuccessfulTaskApi(),
                persistence = persistence,
                clockMillis = { now },
                retryDelayMillis = 5_000L
            ),
            clockMillis = { now }
        )

        val submitted = processor.runDue("worker-a")

        assertEquals(1, submitted.dispatchedCount)
        assertEquals(0, submitted.polledCount)
        assertEquals(6_000L, submitted.nextWakeAt)
        assertEquals(ScrmOutboxState.Submitted, persistence.outbox?.state)
        assertEquals(ScrmTaskPollState.Pending, persistence.task?.pollState)

        now = 6_000L
        val completed = processor.runDue("worker-a")

        assertEquals(0, completed.dispatchedCount)
        assertEquals(1, completed.polledCount)
        assertNull(completed.nextWakeAt)
        assertEquals(ScrmOutboxState.Succeeded, persistence.outbox?.state)
        assertEquals(ScrmTaskPollState.Completed, persistence.task?.pollState)
    }

    private fun textOutbox(): ScrmOutboxItem {
        return ScrmOutboxItem(
            outboxId = "outbox-1",
            operationType = "message.text",
            aggregateType = "message",
            aggregateId = "message-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "request-1",
            requestJson = """{"content":"你好"}""",
            state = ScrmOutboxState.Queued,
            createdAt = now,
            updatedAt = now
        )
    }

    private class AcceptingMessageApi : ScrmMessageApi {
        override fun sendText(request: ScrmSendTextMessageRequest): ScrmTaskSubmissionResult {
            return ScrmTaskSubmissionResult(
                taskId = 42L,
                success = true,
                message = "accepted",
                taskResultUrl = "/openapi/v1/tasks/42"
            )
        }
    }

    private class SuccessfulTaskApi : ScrmTaskApi {
        override fun getTask(taskId: Long): ScrmTaskResult {
            return ScrmTaskResult(
                taskId = taskId,
                success = true,
                status = "success",
                resultUnknown = false,
                receivedAt = "2026-07-12T10:00:00Z",
                rawHidden = true,
                taskResultUrl = "/openapi/v1/tasks/$taskId"
            )
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
            error("not used")
        }
    }

    private class FakeOperationPersistence(
        outbox: ScrmOutboxItem,
        private val clockMillis: () -> Long
    ) : ScrmOutboxPersistence, ScrmTaskPersistence {
        var outbox: ScrmOutboxItem? = outbox
            private set
        var task: ScrmTaskRecord? = null
            private set

        override fun claimNext(
            workerId: String,
            now: Long,
            leaseDurationMillis: Long
        ): ScrmOutboxItem? {
            val current = outbox ?: return null
            if (current.state != ScrmOutboxState.Queued) return null
            val claimed = current.copy(
                attemptCount = current.attemptCount + 1,
                leaseOwner = workerId,
                leaseUntil = now + leaseDurationMillis,
                updatedAt = now
            )
            outbox = claimed
            return claimed
        }

        override fun transition(
            outboxId: String,
            expectedState: ScrmOutboxState,
            nextState: ScrmOutboxState,
            remoteTaskId: Long?,
            taskResultUrl: String?,
            lastErrorCode: String?,
            lastErrorMessage: String?,
            nextAttemptAt: Long?
        ): ScrmOutboxItem {
            val current = requireNotNull(outbox)
            require(current.outboxId == outboxId)
            require(current.state == expectedState)
            val updated = current.copy(
                state = nextState,
                remoteTaskId = remoteTaskId ?: current.remoteTaskId,
                taskResultUrl = taskResultUrl ?: current.taskResultUrl,
                lastErrorCode = lastErrorCode,
                lastErrorMessage = lastErrorMessage,
                leaseOwner = null,
                leaseUntil = null,
                nextAttemptAt = nextAttemptAt,
                updatedAt = clockMillis()
            )
            outbox = updated
            return updated
        }

        override fun upsertTask(task: ScrmTaskRecord) {
            this.task = task
        }

        override fun claimDueTask(
            workerId: String,
            now: Long,
            leaseDurationMillis: Long
        ): ScrmTaskRecord? {
            val current = task ?: return null
            if (current.pollState != ScrmTaskPollState.Pending) return null
            if (current.nextPollAt != null && current.nextPollAt > now) return null
            val claimed = current.copy(
                pollCount = current.pollCount + 1,
                leaseOwner = workerId,
                leaseUntil = now + leaseDurationMillis,
                updatedAt = now
            )
            task = claimed
            return claimed
        }

        override fun applyTaskResult(
            task: ScrmTaskRecord,
            outboxState: ScrmOutboxState
        ): ScrmOutboxItem {
            this.task = task
            val updated = requireNotNull(outbox).copy(
                state = outboxState,
                remoteTaskId = task.taskId,
                leaseOwner = null,
                leaseUntil = null,
                updatedAt = clockMillis(),
                completedAt = clockMillis().takeIf { outboxState.isTerminal }
            )
            outbox = updated
            return updated
        }

        override fun rescheduleTaskPoll(
            taskId: Long,
            workerId: String,
            nextPollAt: Long,
            errorMessage: String
        ): ScrmTaskRecord {
            val updated = requireNotNull(task).copy(
                nextPollAt = nextPollAt,
                lastPollError = errorMessage,
                leaseOwner = null,
                leaseUntil = null,
                updatedAt = clockMillis()
            )
            task = updated
            return updated
        }
    }
}
