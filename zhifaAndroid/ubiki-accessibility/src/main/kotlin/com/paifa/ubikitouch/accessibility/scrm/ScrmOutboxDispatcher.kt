package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val DefaultOutboxLeaseMillis = 30_000L
private const val DefaultTaskPollDelayMillis = 5_000L

internal interface ScrmOutboxPersistence {
    fun claimNext(
        workerId: String,
        now: Long,
        leaseDurationMillis: Long
    ): ScrmOutboxItem?

    fun transition(
        outboxId: String,
        expectedState: ScrmOutboxState,
        nextState: ScrmOutboxState,
        remoteTaskId: Long? = null,
        taskResultUrl: String? = null,
        lastErrorCode: String? = null,
        lastErrorMessage: String? = null,
        nextAttemptAt: Long? = null
    ): ScrmOutboxItem

    fun upsertTask(task: ScrmTaskRecord)
}

internal sealed interface ScrmOutboxDispatchOutcome {
    data object Idle : ScrmOutboxDispatchOutcome

    data class Submitted(
        val outboxId: String,
        val taskId: Long,
        val nextPollAt: Long
    ) : ScrmOutboxDispatchOutcome

    data class Failed(
        val outboxId: String,
        val state: ScrmOutboxState
    ) : ScrmOutboxDispatchOutcome
}

internal fun interface ScrmMediaContentResolver {
    fun resolve(payload: ScrmQueuedMediaPayload): ScrmResolvedMediaContent
}

internal data class ScrmResolvedMediaContent(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
) {
    init {
        require(fileName.isNotBlank()) { "fileName cannot be blank" }
        require(contentType.isNotBlank()) { "contentType cannot be blank" }
        require(bytes.isNotEmpty()) { "bytes cannot be empty" }
    }
}

private fun String.isRemoteMediaUrl(): Boolean {
    return startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
}

internal class ScrmOutboxDispatcher(
    private val api: ScrmMessageApi,
    private val persistence: ScrmOutboxPersistence,
    private val mediaContentResolver: ScrmMediaContentResolver = ScrmMediaContentResolver {
        throw ScrmLocalMediaException("No media content resolver configured")
    },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val leaseDurationMillis: Long = DefaultOutboxLeaseMillis,
    private val taskPollDelayMillis: Long = DefaultTaskPollDelayMillis,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }
) {
    init {
        require(leaseDurationMillis > 0) { "leaseDurationMillis 必须大于 0" }
        require(taskPollDelayMillis > 0) { "taskPollDelayMillis 必须大于 0" }
    }

    fun dispatchNext(workerId: String): ScrmOutboxDispatchOutcome {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        val now = clockMillis()
        val item = persistence.claimNext(workerId, now, leaseDurationMillis)
            ?: return ScrmOutboxDispatchOutcome.Idle
        return when (item.operationType) {
            "message.text" -> dispatchText(item, now)
            "message.image",
            "message.video",
            "message.voice",
            "message.file" -> dispatchMedia(item, now)
            "message.link-card",
            "message.note-card",
            "message.official-article-card",
            "message.quote" -> dispatchCard(item, now)
            else -> failFinal(item, "UnsupportedOperation", "不支持的 SCRM Outbox 操作")
        }
    }

    private fun dispatchText(
        item: ScrmOutboxItem,
        now: Long
    ): ScrmOutboxDispatchOutcome {
        val payload = try {
            json.decodeFromString<ScrmQueuedTextPayload>(item.requestJson)
        } catch (_: SerializationException) {
            return failFinal(item, "InvalidOutboxPayload", "文本消息 Outbox 请求体无效")
        } catch (_: IllegalArgumentException) {
            return failFinal(item, "InvalidOutboxPayload", "文本消息 Outbox 请求体无效")
        }
        val conversationId = item.conversationId
            ?: return failFinal(item, "MissingConversationId", "文本消息缺少 conversationId")
        return try {
            val submitted = api.sendText(
                ScrmSendTextMessageRequest(
                    deviceUuid = item.deviceUuid,
                    weChatId = item.accountWeChatId,
                    conversationId = conversationId,
                    content = payload.content,
                    atIds = payload.atIds
                )
            )
            if (!submitted.success) {
                return failFinal(
                    item = item,
                    code = "TaskRejected",
                    message = submitted.message ?: "SCRM 未受理文本消息任务"
                )
            }
            submitAndTrack(item, submitted, now)
        } catch (error: ScrmException) {
            failAfterApiAttempt(item, error, now)
        }
    }

    private fun dispatchMedia(
        item: ScrmOutboxItem,
        now: Long
    ): ScrmOutboxDispatchOutcome {
        val payload = try {
            json.decodeFromString<ScrmQueuedMediaPayload>(item.requestJson)
        } catch (_: SerializationException) {
            return failFinal(item, "InvalidOutboxPayload", "Media outbox payload is invalid")
        } catch (_: IllegalArgumentException) {
            return failFinal(item, "InvalidOutboxPayload", "Media outbox payload is invalid")
        }
        val conversationId = item.conversationId
            ?: return failFinal(item, "MissingConversationId", "Media message missing conversationId")
        return try {
            val media = resolveRemoteMediaUrl(item.operationType, payload)
            val submitted = when (item.operationType) {
                "message.image" -> api.sendImage(
                    ScrmSendImageMessageRequest(
                        deviceUuid = item.deviceUuid,
                        weChatId = item.accountWeChatId,
                        conversationId = conversationId,
                        imageUrl = media.url
                    )
                )
                "message.video" -> api.sendVideo(
                    ScrmSendVideoMessageRequest(
                        deviceUuid = item.deviceUuid,
                        weChatId = item.accountWeChatId,
                        conversationId = conversationId,
                        videoUrl = media.url
                    )
                )
                "message.voice" -> api.sendVoice(
                    ScrmSendVoiceMessageRequest(
                        deviceUuid = item.deviceUuid,
                        weChatId = item.accountWeChatId,
                        conversationId = conversationId,
                        voiceUrl = media.url,
                        durationSeconds = media.durationSeconds ?: payload.durationSeconds ?: 1
                    )
                )
                "message.file" -> api.sendFile(
                    ScrmSendFileMessageRequest(
                        deviceUuid = item.deviceUuid,
                        weChatId = item.accountWeChatId,
                        conversationId = conversationId,
                        fileName = payload.fileName ?: media.fileName ?: "file",
                        fileUrl = media.url
                    )
                )
                else -> return failFinal(item, "UnsupportedOperation", "Unsupported media operation")
            }
            if (!submitted.success) {
                return failFinal(
                    item = item,
                    code = "TaskRejected",
                    message = submitted.message ?: "SCRM did not accept media message task"
                )
            }
            submitAndTrack(item, submitted, now)
        } catch (error: ScrmException) {
            failAfterApiAttempt(item, error, now)
        }
    }

    private fun dispatchCard(
        item: ScrmOutboxItem,
        now: Long
    ): ScrmOutboxDispatchOutcome {
        val conversationId = item.conversationId
            ?: return failFinal(item, "MissingConversationId", "Card message missing conversationId")
        return try {
            val submitted = when (item.operationType) {
                "message.link-card" -> {
                    val payload = decodeOutboxPayload<ScrmQueuedLinkCardPayload>(item)
                        ?: return failFinal(item, "InvalidOutboxPayload", "Card outbox payload is invalid")
                    api.sendLinkCard(payload.toLinkCardRequest(item, conversationId))
                }
                "message.note-card" -> {
                    val payload = decodeOutboxPayload<ScrmQueuedNoteCardPayload>(item)
                        ?: return failFinal(item, "InvalidOutboxPayload", "Note card outbox payload is invalid")
                    api.sendNoteCard(
                        ScrmSendNoteCardMessageRequest(
                            deviceUuid = item.deviceUuid,
                            weChatId = item.accountWeChatId,
                            conversationId = conversationId,
                            title = payload.title,
                            description = payload.description,
                            thumb = payload.thumb,
                            recordItem = payload.recordItem
                        )
                    )
                }
                "message.official-article-card" -> {
                    val payload = decodeOutboxPayload<ScrmQueuedLinkCardPayload>(item)
                        ?: return failFinal(
                            item,
                            "InvalidOutboxPayload",
                            "Official article card outbox payload is invalid"
                        )
                    api.sendOfficialArticleCard(payload.toLinkCardRequest(item, conversationId))
                }
                "message.quote" -> {
                    val payload = decodeOutboxPayload<ScrmQueuedQuotePayload>(item)
                        ?: return failFinal(item, "InvalidOutboxPayload", "Quote outbox payload is invalid")
                    val quoteMsgSvrId = payload.quoteMsgSvrId
                        ?: return failFinal(
                            item,
                            "MissingQuoteMsgSvrId",
                            "Quote message missing quoteMsgSvrId"
                        )
                    api.sendQuote(
                        ScrmSendQuoteMessageRequest(
                            deviceUuid = item.deviceUuid,
                            weChatId = item.accountWeChatId,
                            conversationId = conversationId,
                            content = payload.content,
                            quoteMsgSvrId = quoteMsgSvrId
                        )
                    )
                }
                else -> return failFinal(item, "UnsupportedOperation", "Unsupported card operation")
            }
            if (!submitted.success) {
                return failFinal(
                    item = item,
                    code = "TaskRejected",
                    message = submitted.message ?: "SCRM did not accept card message task"
                )
            }
            submitAndTrack(item, submitted, now)
        } catch (error: ScrmException) {
            failAfterApiAttempt(item, error, now)
        }
    }

    private inline fun <reified T> decodeOutboxPayload(item: ScrmOutboxItem): T? {
        return try {
            json.decodeFromString<T>(item.requestJson)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun ScrmQueuedLinkCardPayload.toLinkCardRequest(
        item: ScrmOutboxItem,
        conversationId: String
    ): ScrmSendLinkCardMessageRequest {
        return ScrmSendLinkCardMessageRequest(
            deviceUuid = item.deviceUuid,
            weChatId = item.accountWeChatId,
            conversationId = conversationId,
            url = url,
            title = title,
            description = description,
            thumb = thumb,
            appId = appId,
            sourceName = sourceName,
            source = source
        )
    }

    private data class ResolvedRemoteMedia(
        val url: String,
        val durationSeconds: Int? = null,
        val fileName: String? = null
    )

    private fun resolveRemoteMediaUrl(
        operationType: String,
        payload: ScrmQueuedMediaPayload
    ): ResolvedRemoteMedia {
        if (payload.mediaUrl.isRemoteMediaUrl()) {
            return ResolvedRemoteMedia(
                url = payload.mediaUrl,
                fileName = payload.fileName
            )
        }
        val content = mediaContentResolver.resolve(payload)
        val uploadRequest = ScrmMediaUploadRequest(
            fileName = payload.fileName ?: content.fileName,
            contentType = payload.mimeType ?: content.contentType,
            bytes = content.bytes
        )
        return if (operationType == "message.voice") {
            val upload = api.uploadVoice(uploadRequest)
            if (!upload.success || upload.voiceUrl.isNullOrBlank()) {
                throw ScrmInvalidResponseException(upload.message ?: "SCRM voice upload did not return voiceUrl")
            }
            ResolvedRemoteMedia(
                url = upload.voiceUrl,
                durationSeconds = upload.durationSeconds.takeIf { it > 0 }
            )
        } else {
            val upload = api.uploadMedia(uploadRequest)
            if (!upload.success || upload.fileUrl.isNullOrBlank()) {
                throw ScrmInvalidResponseException(upload.message ?: "SCRM media upload did not return fileUrl")
            }
            ResolvedRemoteMedia(
                url = upload.fileUrl,
                fileName = upload.fileName ?: upload.storedFileName ?: uploadRequest.fileName
            )
        }
    }

    private fun submitAndTrack(
        item: ScrmOutboxItem,
        submitted: ScrmTaskSubmissionResult,
        now: Long
    ): ScrmOutboxDispatchOutcome.Submitted {
        val nextPollAt = now + taskPollDelayMillis
        persistence.upsertTask(
            submitted.toPendingTaskRecord(
                outboxId = item.outboxId,
                operationType = item.operationType,
                now = now,
                nextPollAt = nextPollAt
            )
        )
        persistence.transition(
            outboxId = item.outboxId,
            expectedState = ScrmOutboxState.Queued,
            nextState = ScrmOutboxState.Submitted,
            remoteTaskId = submitted.taskId,
            taskResultUrl = submitted.taskResultUrl
        )
        return ScrmOutboxDispatchOutcome.Submitted(item.outboxId, submitted.taskId, nextPollAt)
    }

    private fun ScrmTaskSubmissionResult.toPendingTaskRecord(
        outboxId: String,
        operationType: String,
        now: Long,
        nextPollAt: Long
    ): ScrmTaskRecord {
        return ScrmTaskRecord(
            taskId = taskId,
            outboxId = outboxId,
            operationType = operationType,
            status = "submitted",
            pollState = ScrmTaskPollState.Pending,
            success = success,
            resultUnknown = false,
            resultCode = null,
            message = message,
            deviceUuid = null,
            connectionIdHash = null,
            receivedAt = null,
            rawHidden = true,
            dataJson = data?.toString(),
            taskResultUrl = taskResultUrl,
            recentTaskResultsUrl = recentTaskResultsUrl,
            nextStep = null,
            nextPollAt = nextPollAt,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun failAfterApiAttempt(
        item: ScrmOutboxItem,
        error: ScrmException,
        now: Long
    ): ScrmOutboxDispatchOutcome {
        val state = when (error) {
            is ScrmAuthenticationException,
            is ScrmPermissionException,
            is ScrmRequestException,
            is ScrmConfigurationException,
            is ScrmLocalMediaException,
            is ScrmCredentialStorageException,
            is ScrmCredentialCorruptedException -> ScrmOutboxState.FailedFinal
            is ScrmRateLimitException -> ScrmOutboxState.FailedRetryable
            is ScrmServerException,
            is ScrmTimeoutException,
            is ScrmNetworkException,
            is ScrmInvalidResponseException -> ScrmOutboxState.Unknown
        }
        val nextAttemptAt = (error as? ScrmRateLimitException)
            ?.retryAfterSeconds
            ?.takeIf { it > 0 }
            ?.let { now + it * 1_000L }
        persistence.transition(
            outboxId = item.outboxId,
            expectedState = ScrmOutboxState.Queued,
            nextState = state,
            lastErrorCode = error::class.simpleName,
            lastErrorMessage = error.toUserMessage(),
            nextAttemptAt = nextAttemptAt
        )
        return ScrmOutboxDispatchOutcome.Failed(item.outboxId, state)
    }

    private fun failFinal(
        item: ScrmOutboxItem,
        code: String,
        message: String
    ): ScrmOutboxDispatchOutcome {
        persistence.transition(
            outboxId = item.outboxId,
            expectedState = ScrmOutboxState.Queued,
            nextState = ScrmOutboxState.FailedFinal,
            lastErrorCode = code,
            lastErrorMessage = message
        )
        return ScrmOutboxDispatchOutcome.Failed(item.outboxId, ScrmOutboxState.FailedFinal)
    }
}
