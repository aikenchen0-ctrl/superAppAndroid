package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmOutboxDispatcherTest {
    @Test
    fun dispatchTextMessageSubmitsApiRequestAndSchedulesTaskPolling() {
        val outbox = ScrmOutboxItem(
            outboxId = "outbox-1",
            operationType = "message.text",
            aggregateType = "message",
            aggregateId = "message-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "request-1",
            requestJson = """{"content":"你好，稍后给你报价","atIds":"wxid_member_1"}""",
            state = ScrmOutboxState.Queued,
            createdAt = 900L,
            updatedAt = 900L
        )
        val api = RecordingMessageApi(
            ScrmTaskSubmissionResult(
                taskId = 42L,
                success = true,
                message = "accepted",
                taskResultUrl = "/openapi/v1/tasks/42",
                recentTaskResultsUrl = "/openapi/v1/tasks/recent"
            )
        )
        val persistence = RecordingOutboxPersistence(outbox)
        val dispatcher = ScrmOutboxDispatcher(
            api = api,
            persistence = persistence,
            clockMillis = { 1_000L },
            leaseDurationMillis = 100L,
            taskPollDelayMillis = 5_000L
        )

        val outcome = dispatcher.dispatchNext("worker-a")

        assertTrue(outcome is ScrmOutboxDispatchOutcome.Submitted)
        outcome as ScrmOutboxDispatchOutcome.Submitted
        assertEquals("outbox-1", outcome.outboxId)
        assertEquals(42L, outcome.taskId)
        assertEquals(6_000L, outcome.nextPollAt)
        assertEquals("worker-a", persistence.claimWorkerId)
        assertEquals(1_000L, persistence.claimNow)
        assertEquals(100L, persistence.claimLeaseDurationMillis)
        assertEquals(
            ScrmSendTextMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                content = "你好，稍后给你报价",
                atIds = "wxid_member_1"
            ),
            api.lastRequest
        )
        assertEquals(ScrmOutboxState.Queued, persistence.transitionExpectedState)
        assertEquals(ScrmOutboxState.Submitted, persistence.transitionNextState)
        assertEquals(42L, persistence.transitionRemoteTaskId)
        assertEquals("/openapi/v1/tasks/42", persistence.transitionTaskResultUrl)
        assertEquals(42L, persistence.savedTask?.taskId)
        assertEquals("outbox-1", persistence.savedTask?.outboxId)
        assertEquals("message.text", persistence.savedTask?.operationType)
        assertEquals(ScrmTaskPollState.Pending, persistence.savedTask?.pollState)
        assertEquals(6_000L, persistence.savedTask?.nextPollAt)
    }

    @Test
    fun dispatchNextReturnsIdleWhenNoOutboxItemIsDue() {
        val dispatcher = ScrmOutboxDispatcher(
            api = RecordingMessageApi(
                ScrmTaskSubmissionResult(taskId = 1L, success = true)
            ),
            persistence = RecordingOutboxPersistence(null),
            clockMillis = { 1_000L }
        )

        assertEquals(ScrmOutboxDispatchOutcome.Idle, dispatcher.dispatchNext("worker-a"))
    }

    private class RecordingMessageApi(
        private val result: ScrmTaskSubmissionResult
    ) : ScrmMessageApi {
        var lastRequest: ScrmSendTextMessageRequest? = null
            private set

        override fun sendText(request: ScrmSendTextMessageRequest): ScrmTaskSubmissionResult {
            lastRequest = request
            return result
        }
    }

    private class RecordingOutboxPersistence(
        private val item: ScrmOutboxItem?
    ) : ScrmOutboxPersistence {
        var claimWorkerId: String? = null
            private set
        var claimNow: Long? = null
            private set
        var claimLeaseDurationMillis: Long? = null
            private set
        var transitionExpectedState: ScrmOutboxState? = null
            private set
        var transitionNextState: ScrmOutboxState? = null
            private set
        var transitionRemoteTaskId: Long? = null
            private set
        var transitionTaskResultUrl: String? = null
            private set
        var savedTask: ScrmTaskRecord? = null
            private set

        override fun claimNext(
            workerId: String,
            now: Long,
            leaseDurationMillis: Long
        ): ScrmOutboxItem? {
            claimWorkerId = workerId
            claimNow = now
            claimLeaseDurationMillis = leaseDurationMillis
            return item
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
            transitionExpectedState = expectedState
            transitionNextState = nextState
            transitionRemoteTaskId = remoteTaskId
            transitionTaskResultUrl = taskResultUrl
            return requireNotNull(item).copy(
                state = nextState,
                remoteTaskId = remoteTaskId,
                taskResultUrl = taskResultUrl,
                lastErrorCode = lastErrorCode,
                lastErrorMessage = lastErrorMessage,
                nextAttemptAt = nextAttemptAt
            )
        }

        override fun upsertTask(task: ScrmTaskRecord) {
            savedTask = task
        }
    }
}
