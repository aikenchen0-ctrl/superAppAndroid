package com.paifa.ubikitouch.accessibility.scrm

import java.util.Locale
import kotlinx.serialization.json.JsonElement

internal fun submitScrmMomentTaskAndAwait(
    taskApi: ScrmTaskApi,
    treatMissingRecentTaskAsAccepted: Boolean = false,
    pollDelayMillis: Long = ScrmMomentTaskPollDelayMillis,
    maxPollAttempts: Int = ScrmMomentTaskMaxPollAttempts,
    sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) },
    submit: () -> ScrmTaskSubmissionResult
): ScrmMomentTaskAwaitOutcome {
    require(pollDelayMillis > 0L) { "pollDelayMillis must be greater than 0" }
    require(maxPollAttempts > 0) { "maxPollAttempts must be greater than 0" }
    val submitted = submit()
    if (!submitted.success) {
        throw ScrmRequestException(
            statusCode = 400,
            message = submitted.message ?: "SCRM 未受理朋友圈任务"
        )
    }
    val data = mutableListOf<JsonElement>()
    submitted.data?.let(data::add)
    var lastResult: ScrmTaskResult? = null
    repeat(maxPollAttempts) { attempt ->
        if (attempt > 0) sleepMillis(pollDelayMillis)
        val result = try {
            taskApi.getTask(submitted.taskId)
        } catch (error: ScrmRequestException) {
            if (treatMissingRecentTaskAsAccepted && error.isMissingRecentTaskResult()) {
                return ScrmMomentTaskAwaitOutcome(
                    taskId = submitted.taskId,
                    completed = false,
                    message = submitted.message?.takeIf { it.isNotBlank() }
                        ?.let { message -> "$message #${submitted.taskId}" }
                        ?: "朋友圈发布任务已提交 #${submitted.taskId}，等待 Android 回包",
                    data = data
                )
            }
            throw error
        }
        lastResult = result
        result.data?.let(data::add)
        when (resolveScrmTaskResult(result).pollState) {
            ScrmTaskPollState.Completed -> {
                return ScrmMomentTaskAwaitOutcome(
                    taskId = submitted.taskId,
                    completed = true,
                    message = result.message?.takeIf { it.isNotBlank() }
                        ?: submitted.message?.takeIf { it.isNotBlank() }
                        ?: "朋友圈任务已完成 #${submitted.taskId}",
                    data = data
                )
            }

            ScrmTaskPollState.FailedFinal -> {
                throw ScrmRequestException(
                    statusCode = 400,
                    message = result.message ?: "朋友圈任务执行失败 #${submitted.taskId}"
                )
            }

            ScrmTaskPollState.ManualReview -> {
                throw ScrmInvalidResponseException(
                    result.message ?: "朋友圈任务结果未知 #${submitted.taskId}"
                )
            }

            ScrmTaskPollState.Pending -> Unit
        }
    }
    return ScrmMomentTaskAwaitOutcome(
        taskId = submitted.taskId,
        completed = false,
        message = lastResult?.message?.takeIf { it.isNotBlank() }
            ?: submitted.message?.takeIf { it.isNotBlank() }
            ?: "朋友圈任务仍在处理中 #${submitted.taskId}，请稍后刷新",
        data = data
    )
}

private fun ScrmRequestException.isMissingRecentTaskResult(): Boolean {
    val normalized = message.orEmpty().lowercase(Locale.ROOT)
    return normalized.contains("taskid") &&
        (
            normalized.contains("\u672a\u627e\u5230") ||
                normalized.contains("\u8fd1\u671f\u7ed3\u679c") ||
                normalized.contains("not found") ||
                normalized.contains("recent")
            )
}

