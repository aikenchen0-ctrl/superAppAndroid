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

internal class ScrmOutboxDispatcher(
    private val api: ScrmMessageApi,
    private val persistence: ScrmOutboxPersistence,
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
            ScrmOutboxDispatchOutcome.Submitted(item.outboxId, submitted.taskId, nextPollAt)
        } catch (error: ScrmException) {
            failAfterApiAttempt(item, error, now)
        }
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
