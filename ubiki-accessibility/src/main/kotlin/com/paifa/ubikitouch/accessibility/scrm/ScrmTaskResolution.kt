package com.paifa.ubikitouch.accessibility.scrm

internal data class ScrmTaskResolution(
    val pollState: ScrmTaskPollState,
    val outboxState: ScrmOutboxState
)

internal fun resolveScrmTaskResult(result: ScrmTaskResult): ScrmTaskResolution {
    if (result.resultUnknown) {
        return ScrmTaskResolution(
            pollState = ScrmTaskPollState.ManualReview,
            outboxState = ScrmOutboxState.Unknown
        )
    }
    return when (result.status?.trim()?.lowercase()) {
        "success" -> ScrmTaskResolution(
            pollState = ScrmTaskPollState.Completed,
            outboxState = ScrmOutboxState.Succeeded
        )
        "failed" -> ScrmTaskResolution(
            pollState = ScrmTaskPollState.FailedFinal,
            outboxState = ScrmOutboxState.FailedFinal
        )
        "processing", "running" -> ScrmTaskResolution(
            pollState = ScrmTaskPollState.Pending,
            outboxState = ScrmOutboxState.Processing
        )
        else -> ScrmTaskResolution(
            pollState = ScrmTaskPollState.Pending,
            outboxState = ScrmOutboxState.Submitted
        )
    }
}

internal fun ScrmTaskResult.toTaskRecord(
    outboxId: String?,
    operationType: String,
    now: Long,
    nextPollAt: Long?
): ScrmTaskRecord {
    val resolution = resolveScrmTaskResult(this)
    return ScrmTaskRecord(
        taskId = taskId,
        outboxId = outboxId,
        operationType = operationType,
        status = status,
        pollState = resolution.pollState,
        success = success,
        resultUnknown = resultUnknown,
        resultCode = resultCode,
        message = message,
        deviceUuid = deviceUuid,
        connectionIdHash = connectionIdHash,
        receivedAt = receivedAt,
        rawHidden = rawHidden,
        dataJson = data?.toString(),
        taskResultUrl = taskResultUrl,
        recentTaskResultsUrl = recentTaskResultsUrl,
        nextStep = nextStep,
        nextPollAt = nextPollAt.takeIf { resolution.pollState == ScrmTaskPollState.Pending },
        createdAt = now,
        updatedAt = now,
        completedAt = now.takeIf { resolution.pollState != ScrmTaskPollState.Pending }
    )
}
