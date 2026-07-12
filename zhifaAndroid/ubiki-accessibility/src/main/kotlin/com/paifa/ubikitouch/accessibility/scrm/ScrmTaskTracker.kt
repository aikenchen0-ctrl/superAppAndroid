package com.paifa.ubikitouch.accessibility.scrm

private const val DefaultTaskLeaseMillis = 30_000L
private const val DefaultTaskRetryDelayMillis = 5_000L

internal interface ScrmTaskPersistence {
    fun claimDueTask(
        workerId: String,
        now: Long,
        leaseDurationMillis: Long
    ): ScrmTaskRecord?

    fun applyTaskResult(
        task: ScrmTaskRecord,
        outboxState: ScrmOutboxState
    ): ScrmOutboxItem

    fun rescheduleTaskPoll(
        taskId: Long,
        workerId: String,
        nextPollAt: Long,
        errorMessage: String
    ): ScrmTaskRecord
}

internal sealed interface ScrmTaskPollOutcome {
    data object Idle : ScrmTaskPollOutcome

    data class RetryScheduled(
        val taskId: Long,
        val nextPollAt: Long
    ) : ScrmTaskPollOutcome

    data class Updated(
        val taskId: Long,
        val pollState: ScrmTaskPollState,
        val outboxState: ScrmOutboxState,
        val nextPollAt: Long? = null
    ) : ScrmTaskPollOutcome
}

internal class ScrmTaskTracker(
    private val api: ScrmTaskApi,
    private val persistence: ScrmTaskPersistence,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val leaseDurationMillis: Long = DefaultTaskLeaseMillis,
    private val retryDelayMillis: Long = DefaultTaskRetryDelayMillis
) {
    init {
        require(leaseDurationMillis > 0) { "leaseDurationMillis 必须大于 0" }
        require(retryDelayMillis > 0) { "retryDelayMillis 必须大于 0" }
    }

    fun pollNext(workerId: String): ScrmTaskPollOutcome {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        val now = clockMillis()
        val claimed = persistence.claimDueTask(workerId, now, leaseDurationMillis)
            ?: return ScrmTaskPollOutcome.Idle
        return try {
            val result = api.getTask(claimed.taskId)
            if (result.taskId != claimed.taskId) {
                applyManualReview(claimed, "任务结果 ID 不匹配", now)
            } else {
                applyResult(claimed, result, now)
            }
        } catch (error: ScrmException) {
            if (error.isTransientTaskPollFailure()) {
                val delay = (error as? ScrmRateLimitException)
                    ?.retryAfterSeconds
                    ?.takeIf { it > 0 }
                    ?.times(1_000L)
                    ?: retryDelayMillis
                val nextPollAt = now + delay
                persistence.rescheduleTaskPoll(
                    taskId = claimed.taskId,
                    workerId = workerId,
                    nextPollAt = nextPollAt,
                    errorMessage = error.toUserMessage()
                )
                ScrmTaskPollOutcome.RetryScheduled(claimed.taskId, nextPollAt)
            } else {
                applyManualReview(claimed, error.toUserMessage(), now)
            }
        }
    }

    private fun applyResult(
        claimed: ScrmTaskRecord,
        result: ScrmTaskResult,
        now: Long
    ): ScrmTaskPollOutcome {
        val resolution = resolveScrmTaskResult(result)
        val nextPollAt = (now + retryDelayMillis)
            .takeIf { resolution.pollState == ScrmTaskPollState.Pending }
        val task = result.toTaskRecord(
            outboxId = claimed.outboxId,
            operationType = claimed.operationType,
            now = now,
            nextPollAt = nextPollAt
        ).copy(
            pollCount = claimed.pollCount,
            createdAt = claimed.createdAt,
            leaseOwner = null,
            leaseUntil = null
        )
        persistence.applyTaskResult(task, resolution.outboxState)
        return ScrmTaskPollOutcome.Updated(
            taskId = task.taskId,
            pollState = resolution.pollState,
            outboxState = resolution.outboxState,
            nextPollAt = task.nextPollAt
        )
    }

    private fun applyManualReview(
        claimed: ScrmTaskRecord,
        errorMessage: String,
        now: Long
    ): ScrmTaskPollOutcome {
        val task = claimed.copy(
            pollState = ScrmTaskPollState.ManualReview,
            resultUnknown = true,
            lastPollError = errorMessage,
            nextPollAt = null,
            leaseOwner = null,
            leaseUntil = null,
            updatedAt = now,
            completedAt = now
        )
        persistence.applyTaskResult(task, ScrmOutboxState.Unknown)
        return ScrmTaskPollOutcome.Updated(
            taskId = task.taskId,
            pollState = task.pollState,
            outboxState = ScrmOutboxState.Unknown
        )
    }
}

private fun ScrmException.isTransientTaskPollFailure(): Boolean {
    return this is ScrmTimeoutException ||
        this is ScrmNetworkException ||
        this is ScrmServerException ||
        this is ScrmRateLimitException
}
