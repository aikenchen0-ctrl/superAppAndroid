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
    fun dispatchImageMessageWithRemoteUrlSubmitsImageRequest() {
        val outbox = ScrmOutboxItem(
            outboxId = "outbox-image",
            operationType = "message.image",
            aggregateType = "message",
            aggregateId = "image-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "image-request",
            requestJson = """{"mediaUrl":"https://cdn.example.com/image.jpg","mimeType":"image/jpeg"}""",
            state = ScrmOutboxState.Queued,
            createdAt = 900L,
            updatedAt = 900L
        )
        val api = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 43L, success = true))
        val persistence = RecordingOutboxPersistence(outbox)
        val dispatcher = ScrmOutboxDispatcher(
            api = api,
            persistence = persistence,
            clockMillis = { 1_000L }
        )

        val outcome = dispatcher.dispatchNext("worker-a")

        assertTrue(outcome is ScrmOutboxDispatchOutcome.Submitted)
        assertEquals(
            ScrmSendImageMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                imageUrl = "https://cdn.example.com/image.jpg"
            ),
            api.lastImageRequest
        )
        assertNull(api.lastUploadRequest)
        assertEquals("message.image", persistence.savedTask?.operationType)
    }

    @Test
    fun dispatchVoiceMessageUploadsLocalMediaBeforeSubmittingVoiceRequest() {
        val outbox = ScrmOutboxItem(
            outboxId = "outbox-voice",
            operationType = "message.voice",
            aggregateType = "message",
            aggregateId = "voice-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "voice-request",
            requestJson = """{"mediaUrl":"content://media/audio/1","mimeType":"audio/mp4","durationSeconds":4}""",
            state = ScrmOutboxState.Queued,
            createdAt = 900L,
            updatedAt = 900L
        )
        val api = RecordingMessageApi(
            result = ScrmTaskSubmissionResult(taskId = 45L, success = true),
            voiceUpload = ScrmVoiceUploadResponse(
                success = true,
                voiceUrl = "https://cdn.example.com/voice.amr",
                durationSeconds = 6
            )
        )
        val resolver = ScrmMediaContentResolver { payload ->
            assertEquals("content://media/audio/1", payload.mediaUrl)
            ScrmResolvedMediaContent(
                fileName = "voice.m4a",
                contentType = "audio/mp4",
                bytes = byteArrayOf(0x56, 0x4f, 0x43)
            )
        }
        val persistence = RecordingOutboxPersistence(outbox)
        val dispatcher = ScrmOutboxDispatcher(
            api = api,
            persistence = persistence,
            mediaContentResolver = resolver,
            clockMillis = { 1_000L }
        )

        val outcome = dispatcher.dispatchNext("worker-a")

        assertTrue(outcome is ScrmOutboxDispatchOutcome.Submitted)
        assertEquals("voice.m4a", api.lastUploadRequest?.fileName)
        assertEquals(
            ScrmSendVoiceMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                voiceUrl = "https://cdn.example.com/voice.amr",
                durationSeconds = 6
            ),
            api.lastVoiceRequest
        )
        assertEquals("message.voice", persistence.savedTask?.operationType)
    }

    @Test
    fun dispatchFileMessageUploadsLocalDocumentBeforeSubmittingFileRequest() {
        val outbox = ScrmOutboxItem(
            outboxId = "outbox-file",
            operationType = "message.file",
            aggregateType = "message",
            aggregateId = "file-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "file-request",
            requestJson = """{"mediaUrl":"content://documents/quote","mimeType":"application/pdf","fileName":"quote.pdf"}""",
            state = ScrmOutboxState.Queued,
            createdAt = 900L,
            updatedAt = 900L
        )
        val api = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 46L, success = true))
        val resolver = ScrmMediaContentResolver { payload ->
            assertEquals("content://documents/quote", payload.mediaUrl)
            ScrmResolvedMediaContent(
                fileName = "quote.pdf",
                contentType = "application/pdf",
                bytes = byteArrayOf(0x50, 0x44, 0x46)
            )
        }
        val persistence = RecordingOutboxPersistence(outbox)
        val dispatcher = ScrmOutboxDispatcher(
            api = api,
            persistence = persistence,
            mediaContentResolver = resolver,
            clockMillis = { 1_000L }
        )

        val outcome = dispatcher.dispatchNext("worker-a")

        assertTrue(outcome is ScrmOutboxDispatchOutcome.Submitted)
        assertEquals("quote.pdf", api.lastUploadRequest?.fileName)
        assertEquals("application/pdf", api.lastUploadRequest?.contentType)
        assertEquals(
            ScrmSendFileMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                fileName = "quote.pdf",
                fileUrl = "https://cdn.example.com/media.bin"
            ),
            api.lastFileRequest
        )
        assertEquals("message.file", persistence.savedTask?.operationType)
    }

    @Test
    fun dispatchCardAndQuoteMessagesSubmitsMatchingApiRequests() {
        val linkApi = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 47L, success = true))
        val linkPersistence = RecordingOutboxPersistence(
            cardOutbox(
                outboxId = "outbox-link",
                operationType = "message.link-card",
                requestJson = """{"url":"https://example.com/order/1","title":"订单详情","description":"点击查看报价","thumb":"https://example.com/thumb.jpg","sourceName":"销售系统"}"""
            )
        )
        ScrmOutboxDispatcher(
            api = linkApi,
            persistence = linkPersistence,
            clockMillis = { 1_000L }
        ).dispatchNext("worker-a")
        assertEquals(
            ScrmSendLinkCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                url = "https://example.com/order/1",
                title = "订单详情",
                description = "点击查看报价",
                thumb = "https://example.com/thumb.jpg",
                sourceName = "销售系统"
            ),
            linkApi.lastLinkCardRequest
        )
        assertEquals("message.link-card", linkPersistence.savedTask?.operationType)

        val noteApi = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 48L, success = true))
        ScrmOutboxDispatcher(
            api = noteApi,
            persistence = RecordingOutboxPersistence(
                cardOutbox(
                    outboxId = "outbox-note",
                    operationType = "message.note-card",
                    requestJson = """{"title":"会议纪要","description":"重点事项","recordItem":"{\"type\":\"note\",\"id\":\"note-1\"}"}"""
                )
            ),
            clockMillis = { 1_000L }
        ).dispatchNext("worker-a")
        assertEquals(
            ScrmSendNoteCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                title = "会议纪要",
                description = "重点事项",
                recordItem = """{"type":"note","id":"note-1"}"""
            ),
            noteApi.lastNoteCardRequest
        )

        val officialApi = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 49L, success = true))
        ScrmOutboxDispatcher(
            api = officialApi,
            persistence = RecordingOutboxPersistence(
                cardOutbox(
                    outboxId = "outbox-official",
                    operationType = "message.official-article-card",
                    requestJson = """{"url":"https://mp.weixin.qq.com/s/article","title":"公众号文章","description":"文章摘要","sourceName":"公众号"}"""
                )
            ),
            clockMillis = { 1_000L }
        ).dispatchNext("worker-a")
        assertEquals(
            ScrmSendLinkCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                url = "https://mp.weixin.qq.com/s/article",
                title = "公众号文章",
                description = "文章摘要",
                sourceName = "公众号"
            ),
            officialApi.lastOfficialArticleCardRequest
        )

        val quoteApi = RecordingMessageApi(ScrmTaskSubmissionResult(taskId = 50L, success = true))
        ScrmOutboxDispatcher(
            api = quoteApi,
            persistence = RecordingOutboxPersistence(
                cardOutbox(
                    outboxId = "outbox-quote",
                    operationType = "message.quote",
                    requestJson = """{"content":"这条引用回复","quoteMsgSvrId":88990011}"""
                )
            ),
            clockMillis = { 1_000L }
        ).dispatchNext("worker-a")
        assertEquals(
            ScrmSendQuoteMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                content = "这条引用回复",
                quoteMsgSvrId = 88990011L
            ),
            quoteApi.lastQuoteRequest
        )
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

    private fun cardOutbox(
        outboxId: String,
        operationType: String,
        requestJson: String
    ): ScrmOutboxItem {
        return ScrmOutboxItem(
            outboxId = outboxId,
            operationType = operationType,
            aggregateType = "message",
            aggregateId = outboxId.removePrefix("outbox-"),
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = "$outboxId-request",
            requestJson = requestJson,
            state = ScrmOutboxState.Queued,
            createdAt = 900L,
            updatedAt = 900L
        )
    }

    private class RecordingMessageApi(
        private val result: ScrmTaskSubmissionResult,
        private val mediaUpload: ScrmMediaUploadResponse = ScrmMediaUploadResponse(
            success = true,
            fileUrl = "https://cdn.example.com/media.bin"
        ),
        private val voiceUpload: ScrmVoiceUploadResponse = ScrmVoiceUploadResponse(
            success = true,
            voiceUrl = "https://cdn.example.com/voice.amr",
            durationSeconds = 1
        )
    ) : ScrmMessageApi {
        var lastRequest: ScrmSendTextMessageRequest? = null
            private set
        var lastImageRequest: ScrmSendImageMessageRequest? = null
            private set
        var lastVideoRequest: ScrmSendVideoMessageRequest? = null
            private set
        var lastVoiceRequest: ScrmSendVoiceMessageRequest? = null
            private set
        var lastFileRequest: ScrmSendFileMessageRequest? = null
            private set
        var lastLinkCardRequest: ScrmSendLinkCardMessageRequest? = null
            private set
        var lastNoteCardRequest: ScrmSendNoteCardMessageRequest? = null
            private set
        var lastOfficialArticleCardRequest: ScrmSendLinkCardMessageRequest? = null
            private set
        var lastQuoteRequest: ScrmSendQuoteMessageRequest? = null
            private set
        var lastUploadRequest: ScrmMediaUploadRequest? = null
            private set

        override fun sendText(request: ScrmSendTextMessageRequest): ScrmTaskSubmissionResult {
            lastRequest = request
            return result
        }

        override fun sendImage(request: ScrmSendImageMessageRequest): ScrmTaskSubmissionResult {
            lastImageRequest = request
            return result
        }

        override fun sendVideo(request: ScrmSendVideoMessageRequest): ScrmTaskSubmissionResult {
            lastVideoRequest = request
            return result
        }

        override fun sendVoice(request: ScrmSendVoiceMessageRequest): ScrmTaskSubmissionResult {
            lastVoiceRequest = request
            return result
        }

        override fun sendFile(request: ScrmSendFileMessageRequest): ScrmTaskSubmissionResult {
            lastFileRequest = request
            return result
        }

        override fun sendLinkCard(request: ScrmSendLinkCardMessageRequest): ScrmTaskSubmissionResult {
            lastLinkCardRequest = request
            return result
        }

        override fun sendNoteCard(request: ScrmSendNoteCardMessageRequest): ScrmTaskSubmissionResult {
            lastNoteCardRequest = request
            return result
        }

        override fun sendOfficialArticleCard(request: ScrmSendLinkCardMessageRequest): ScrmTaskSubmissionResult {
            lastOfficialArticleCardRequest = request
            return result
        }

        override fun sendQuote(request: ScrmSendQuoteMessageRequest): ScrmTaskSubmissionResult {
            lastQuoteRequest = request
            return result
        }

        override fun uploadMedia(request: ScrmMediaUploadRequest): ScrmMediaUploadResponse {
            lastUploadRequest = request
            return mediaUpload
        }

        override fun uploadVoice(request: ScrmMediaUploadRequest): ScrmVoiceUploadResponse {
            lastUploadRequest = request
            return voiceUpload
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
