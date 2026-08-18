package com.paifa.univerge.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmMessageRevokeTaskRunnerTest {
    @Test
    fun completedTaskUsesRemoteMessageIdAndAccountRoute() {
        val messageApi = RecordingRevokeApi(
            submission = ScrmTaskSubmissionResult(taskId = 91L, success = true, message = "queued")
        )
        val taskApi = RecordingRevokeTaskApi(
            result = ScrmTaskResult(
                taskId = 91L,
                success = true,
                status = "success",
                resultUnknown = false,
                message = "revoked",
                receivedAt = "2026-08-17T00:00:00Z",
                rawHidden = true
            )
        )
        val runner = ScrmMessageRevokeTaskRunner(
            messageApi = messageApi,
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        val outcome = runner.revokeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(
            RevokeCall(123L, ScrmMessageOperationRequest("device-1", "wxid_account")),
            messageApi.calls.single()
        )
        assertEquals(listOf(91L), taskApi.requestedTaskIds)
        assertTrue(outcome.completed)
        assertEquals("revoked", outcome.message)
    }

    @Test
    fun pendingTaskDoesNotReportCompletion() {
        val runner = ScrmMessageRevokeTaskRunner(
            messageApi = RecordingRevokeApi(
                ScrmTaskSubmissionResult(taskId = 92L, success = true, message = "queued")
            ),
            taskApi = RecordingRevokeTaskApi(
                ScrmTaskResult(
                    taskId = 92L,
                    success = true,
                    status = "processing",
                    resultUnknown = false,
                    message = "still processing",
                    receivedAt = "2026-08-17T00:00:00Z",
                    rawHidden = true
                )
            ),
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        val outcome = runner.revokeAndAwait(123L, "device-1", "wxid_account")

        assertFalse(outcome.completed)
        assertEquals("still processing", outcome.message)
    }

    @Test
    fun existingTaskCanBePolledWithoutSubmittingRevokeAgain() {
        val messageApi = RecordingRevokeApi(
            ScrmTaskSubmissionResult(taskId = 94L, success = true, message = "queued")
        )
        val taskApi = RecordingRevokeTaskApi(
            ScrmTaskResult(
                taskId = 94L,
                success = true,
                status = "processing",
                resultUnknown = false,
                message = "still processing",
                receivedAt = "2026-08-17T00:00:00Z",
                rawHidden = true
            )
        )
        val runner = ScrmMessageRevokeTaskRunner(
            messageApi = messageApi,
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        var acceptedTaskId: Long? = null
        runner.revokeAndAwait(123L, "device-1", "wxid_account") {
            acceptedTaskId = it
        }
        val outcome = runner.awaitExistingTask(requireNotNull(acceptedTaskId))

        assertEquals(1, messageApi.calls.size)
        assertEquals(listOf(94L, 94L), taskApi.requestedTaskIds)
        assertFalse(outcome.completed)
    }

    @Test
    fun invalidInputsFailBeforeSubmitting() {
        val messageApi = RecordingRevokeApi(
            ScrmTaskSubmissionResult(taskId = 93L, success = true)
        )
        val runner = ScrmMessageRevokeTaskRunner(
            messageApi = messageApi,
            taskApi = RecordingRevokeTaskApi(),
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        assertTrue(runCatching { runner.revokeAndAwait(0L, "device-1", "wxid_account") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { runner.revokeAndAwait(123L, " ", "wxid_account") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { runner.revokeAndAwait(123L, "device-1", "") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(messageApi.calls.isEmpty())
    }

    private data class RevokeCall(
        val messageId: Long,
        val request: ScrmMessageOperationRequest
    )

    private class RecordingRevokeTaskApi(
        private val result: ScrmTaskResult? = null
    ) : ScrmTaskApi {
        val requestedTaskIds = mutableListOf<Long>()

        override fun getTask(taskId: Long): ScrmTaskResult {
            requestedTaskIds += taskId
            return requireNotNull(result)
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults =
            error("not used")
    }

    private class RecordingRevokeApi(
        private val submission: ScrmTaskSubmissionResult
    ) : MessageOperationApiAdapter() {
        val calls = mutableListOf<RevokeCall>()

        override fun revokeMessage(
            messageId: Long,
            request: ScrmMessageOperationRequest
        ): ScrmTaskSubmissionResult {
            calls += RevokeCall(messageId, request)
            return submission
        }
    }

    private abstract class MessageOperationApiAdapter : ScrmMessageOperationApi {
        private fun unused(): Nothing = error("not used")

        override fun getCardTemplates(query: ScrmCardTemplateQuery): ScrmCardTemplateResponse = unused()
        override fun sendEmoji(request: ScrmSendEmojiRequest): ScrmTaskSubmissionResult = unused()
        override fun sendWeAppCard(request: ScrmSendWeAppCardRequest): ScrmTaskSubmissionResult = unused()
        override fun sendBatch(request: ScrmBatchSendMessageRequest): ScrmBatchSendMessageResponse = unused()
        override fun sendBatchByFilter(request: ScrmBatchSendMessageByFilterRequest): ScrmBatchSendMessageResponse = unused()
        override fun syncConversationUnread(request: ScrmConversationMessageStateRequest): ScrmTaskSubmissionResult = unused()
        override fun syncHistory(request: ScrmSyncHistoryMessagesRequest): ScrmTaskSubmissionResult = unused()
        override fun syncMessageIds(request: ScrmSyncMessageIdsRequest): ScrmTaskSubmissionResult = unused()
        override fun syncReadState(request: ScrmConversationMessageStateRequest): ScrmTaskSubmissionResult = unused()
        override fun syncUnreadList(request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult = unused()
        override fun clearAllChatMessages(request: ScrmClearAllChatMessagesRequest): ScrmTaskSubmissionResult = unused()
        override fun forwardMessages(request: ScrmForwardMessagesRequest, idempotencyKey: String): ScrmTaskSubmissionResult = unused()
        override fun pullEmojiDetail(messageId: Long, request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult = unused()
        override fun forwardMessage(messageId: Long, request: ScrmForwardMessageRequest, idempotencyKey: String): ScrmTaskSubmissionResult = unused()
        override fun downloadMessageMedia(messageId: Long, request: ScrmMessageMediaDownloadRequest): ScrmTaskSubmissionResult = unused()
        override fun pullMessageDetail(messageId: Long, request: ScrmMessageDetailPullRequest): ScrmTaskSubmissionResult = unused()
        override fun pullMessageOriginal(messageId: Long, request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult = unused()
        override fun transcribeVoiceMessage(messageId: Long, request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult = unused()
    }
}
