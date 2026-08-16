package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmVoiceTranscriptionTaskRunnerTest {
    @Test
    fun successUsesRemoteMessageIdAndPollsExistingTaskWithoutResubmitting() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 71L, success = true, message = "queued")
        )
        val taskApi = RecordingTaskApi(
            results = listOf(
                taskResult(taskId = 71L, status = "processing", message = "recognizing"),
                taskResult(
                    taskId = 71L,
                    status = "success",
                    message = "transcribed",
                    dataJson = """{"voiceText":"你好"}"""
                )
            )
        )
        val sleeps = mutableListOf<Long>()
        val acceptedTaskIds = mutableListOf<Long>()
        val runner = ScrmVoiceTranscriptionTaskRunner(
            messageApi = messageApi,
            taskApi = taskApi,
            pollDelayMillis = 5L,
            maxPollAttempts = 3,
            sleepMillis = { delay -> sleeps += delay }
        )

        val outcome = runner.transcribeAndAwait(
            remoteMessageId = 123L,
            deviceUuid = "device-1",
            weChatId = "wxid_account",
            onTaskAccepted = { taskId -> acceptedTaskIds += taskId }
        )

        assertEquals(ScrmVoiceTranscriptionState.SUCCEEDED, outcome.state)
        assertEquals(71L, outcome.taskId)
        assertEquals("transcribed", outcome.message)
        assertEquals("""{"voiceText":"你好"}""", outcome.data?.toString())
        assertEquals(
            listOf(VoiceTranscriptionCall(123L, ScrmMessageOperationRequest("device-1", "wxid_account"))),
            messageApi.calls
        )
        assertEquals(listOf(71L, 71L), taskApi.requestedTaskIds)
        assertEquals(listOf(5L), sleeps)
        assertEquals(listOf(71L), acceptedTaskIds)
    }

    @Test
    fun rejectedSubmissionIsReportedWithoutPolling() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(
                taskId = 72L,
                success = false,
                message = "permission denied"
            )
        )
        val taskApi = RecordingTaskApi()
        val runner = runner(messageApi, taskApi)

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.SUBMISSION_FAILED, outcome.state)
        assertEquals(72L, outcome.taskId)
        assertEquals("permission denied", outcome.message)
        assertEquals(1, messageApi.calls.size)
        assertTrue(taskApi.requestedTaskIds.isEmpty())
    }

    @Test
    fun submissionExceptionIsReportedWithoutPolling() {
        val messageApi = RecordingMessageOperationApi(submissionError = ScrmNetworkException())
        val taskApi = RecordingTaskApi()
        val runner = runner(messageApi, taskApi)

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.SUBMISSION_FAILED, outcome.state)
        assertEquals(null, outcome.taskId)
        assertEquals("SCRM 网络请求失败", outcome.message)
        assertEquals(1, messageApi.calls.size)
        assertTrue(taskApi.requestedTaskIds.isEmpty())
    }

    @Test
    fun terminalTaskFailureIsDistinctFromSubmissionFailure() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 73L, success = true)
        )
        val taskApi = RecordingTaskApi(
            results = listOf(taskResult(73L, "failed", success = false, message = "voice unavailable"))
        )
        val runner = runner(messageApi, taskApi)

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.TASK_FAILED, outcome.state)
        assertEquals(73L, outcome.taskId)
        assertEquals("voice unavailable", outcome.message)
        assertEquals(1, messageApi.calls.size)
        assertEquals(listOf(73L), taskApi.requestedTaskIds)
    }

    @Test
    fun pollLimitReturnsProcessingWithoutResubmitting() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 74L, success = true, message = "queued")
        )
        val taskApi = RecordingTaskApi(
            results = listOf(taskResult(74L, "processing", message = "still recognizing"))
        )
        val runner = ScrmVoiceTranscriptionTaskRunner(
            messageApi = messageApi,
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 2,
            sleepMillis = {}
        )

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.PROCESSING, outcome.state)
        assertEquals(74L, outcome.taskId)
        assertEquals("still recognizing", outcome.message)
        assertEquals(1, messageApi.calls.size)
        assertEquals(listOf(74L, 74L), taskApi.requestedTaskIds)
    }

    @Test
    fun pollingFailureKeepsSubmittedTaskExplicitlyProcessing() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 75L, success = true)
        )
        val taskApi = RecordingTaskApi(taskError = ScrmTimeoutException())
        val runner = runner(messageApi, taskApi)

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.PROCESSING, outcome.state)
        assertEquals(75L, outcome.taskId)
        assertTrue(outcome.message.contains("查询失败"))
        assertTrue(outcome.message.contains("超时"))
        assertEquals(1, messageApi.calls.size)
        assertEquals(listOf(75L), taskApi.requestedTaskIds)
    }

    @Test
    fun existingTaskCanResumeWithoutSubmittingTheMessageAgain() {
        val messageApi = RecordingMessageOperationApi()
        val taskApi = RecordingTaskApi(
            results = listOf(
                taskResult(75L, "processing", message = "still recognizing"),
                taskResult(75L, "success", message = "transcribed")
            )
        )
        val runner = ScrmVoiceTranscriptionTaskRunner(
            messageApi = messageApi,
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 2,
            sleepMillis = {}
        )

        val outcome = runner.awaitExistingTask(75L)

        assertEquals(ScrmVoiceTranscriptionState.SUCCEEDED, outcome.state)
        assertEquals(75L, outcome.taskId)
        assertTrue(messageApi.calls.isEmpty())
        assertEquals(listOf(75L, 75L), taskApi.requestedTaskIds)
    }

    @Test
    fun unknownTaskResultRequiresManualReview() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 76L, success = true)
        )
        val taskApi = RecordingTaskApi(
            results = listOf(
                taskResult(
                    taskId = 76L,
                    status = "processing",
                    resultUnknown = true,
                    message = "result unknown"
                )
            )
        )
        val runner = runner(messageApi, taskApi)

        val outcome = runner.transcribeAndAwait(123L, "device-1", "wxid_account")

        assertEquals(ScrmVoiceTranscriptionState.RESULT_UNKNOWN, outcome.state)
        assertEquals("result unknown", outcome.message)
        assertEquals(1, messageApi.calls.size)
    }

    @Test
    fun invalidInputsAreRejectedBeforeSubmission() {
        val messageApi = RecordingMessageOperationApi(
            submission = ScrmTaskSubmissionResult(taskId = 77L, success = true)
        )
        val runner = runner(messageApi, RecordingTaskApi())

        val invalidId = runCatching {
            runner.transcribeAndAwait(0L, "device-1", "wxid_account")
        }.exceptionOrNull()
        val invalidDevice = runCatching {
            runner.transcribeAndAwait(123L, " ", "wxid_account")
        }.exceptionOrNull()
        val invalidAccount = runCatching {
            runner.transcribeAndAwait(123L, "device-1", "")
        }.exceptionOrNull()
        val invalidTaskId = runCatching {
            runner.awaitExistingTask(0L)
        }.exceptionOrNull()

        assertTrue(invalidId is IllegalArgumentException)
        assertTrue(invalidDevice is IllegalArgumentException)
        assertTrue(invalidAccount is IllegalArgumentException)
        assertTrue(invalidTaskId is IllegalArgumentException)
        assertTrue(messageApi.calls.isEmpty())
    }

    private fun runner(
        messageApi: ScrmMessageOperationApi,
        taskApi: ScrmTaskApi
    ) = ScrmVoiceTranscriptionTaskRunner(
        messageApi = messageApi,
        taskApi = taskApi,
        pollDelayMillis = 1L,
        maxPollAttempts = 1,
        sleepMillis = {}
    )

    private fun taskResult(
        taskId: Long,
        status: String,
        success: Boolean = true,
        resultUnknown: Boolean = false,
        message: String? = null,
        dataJson: String? = null
    ) = ScrmTaskResult(
        taskId = taskId,
        success = success,
        status = status,
        resultUnknown = resultUnknown,
        message = message,
        data = dataJson?.let(Json::parseToJsonElement),
        receivedAt = "2026-08-16T00:00:00Z",
        rawHidden = true
    )

    private data class VoiceTranscriptionCall(
        val messageId: Long,
        val request: ScrmMessageOperationRequest
    )

    private class RecordingTaskApi(
        private val results: List<ScrmTaskResult> = emptyList(),
        private val taskError: ScrmException? = null
    ) : ScrmTaskApi {
        val requestedTaskIds = mutableListOf<Long>()
        private var index = 0

        override fun getTask(taskId: Long): ScrmTaskResult {
            requestedTaskIds += taskId
            taskError?.let { throw it }
            assertTrue("missing fake task result", results.isNotEmpty())
            return results[index.coerceAtMost(results.lastIndex)].also { index += 1 }
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults =
            error("not used")
    }

    private class RecordingMessageOperationApi(
        private val submission: ScrmTaskSubmissionResult? = null,
        private val submissionError: ScrmException? = null
    ) : ScrmMessageOperationApi {
        val calls = mutableListOf<VoiceTranscriptionCall>()

        override fun transcribeVoiceMessage(
            messageId: Long,
            request: ScrmMessageOperationRequest
        ): ScrmTaskSubmissionResult {
            calls += VoiceTranscriptionCall(messageId, request)
            submissionError?.let { throw it }
            return requireNotNull(submission)
        }

        override fun getCardTemplates(query: ScrmCardTemplateQuery): ScrmCardTemplateResponse = unused()
        override fun sendEmoji(request: ScrmSendEmojiRequest): ScrmTaskSubmissionResult = unused()
        override fun sendWeAppCard(request: ScrmSendWeAppCardRequest): ScrmTaskSubmissionResult = unused()
        override fun sendBatch(request: ScrmBatchSendMessageRequest): ScrmBatchSendMessageResponse = unused()
        override fun sendBatchByFilter(
            request: ScrmBatchSendMessageByFilterRequest
        ): ScrmBatchSendMessageResponse = unused()

        override fun syncConversationUnread(
            request: ScrmConversationMessageStateRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun syncHistory(request: ScrmSyncHistoryMessagesRequest): ScrmTaskSubmissionResult = unused()
        override fun syncMessageIds(request: ScrmSyncMessageIdsRequest): ScrmTaskSubmissionResult = unused()
        override fun syncReadState(
            request: ScrmConversationMessageStateRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun syncUnreadList(request: ScrmMessageOperationRequest): ScrmTaskSubmissionResult = unused()
        override fun clearAllChatMessages(
            request: ScrmClearAllChatMessagesRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun forwardMessages(
            request: ScrmForwardMessagesRequest,
            idempotencyKey: String
        ): ScrmTaskSubmissionResult = unused()

        override fun pullEmojiDetail(
            messageId: Long,
            request: ScrmMessageOperationRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun forwardMessage(
            messageId: Long,
            request: ScrmForwardMessageRequest,
            idempotencyKey: String
        ): ScrmTaskSubmissionResult = unused()

        override fun downloadMessageMedia(
            messageId: Long,
            request: ScrmMessageMediaDownloadRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun pullMessageDetail(
            messageId: Long,
            request: ScrmMessageDetailPullRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun pullMessageOriginal(
            messageId: Long,
            request: ScrmMessageOperationRequest
        ): ScrmTaskSubmissionResult = unused()

        override fun revokeMessage(
            messageId: Long,
            request: ScrmMessageOperationRequest
        ): ScrmTaskSubmissionResult = unused()

        private fun unused(): Nothing = error("not used")
    }
}
