package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.JsonElement

private const val DefaultContactTaskPollDelayMillis = 1_500L
private const val DefaultContactTaskMaxPollAttempts = 8

internal data class ScrmContactTaskOutcome(
    val taskId: Long,
    val message: String,
    val shouldReloadContacts: Boolean,
    val data: JsonElement? = null
)

internal class ScrmContactTaskRunner(
    private val taskApi: ScrmTaskApi,
    private val pollDelayMillis: Long = DefaultContactTaskPollDelayMillis,
    private val maxPollAttempts: Int = DefaultContactTaskMaxPollAttempts,
    private val sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) }
) {
    init {
        require(pollDelayMillis > 0) { "pollDelayMillis 必须大于 0" }
        require(maxPollAttempts > 0) { "maxPollAttempts 必须大于 0" }
    }

    fun submitAndAwait(
        reloadContactsOnSuccess: Boolean,
        submit: () -> ScrmTaskSubmissionResult
    ): ScrmContactTaskOutcome {
        val submitted = submit()
        if (!submitted.success) {
            throw ScrmRequestException(
                statusCode = 400,
                message = submitted.message ?: "SCRM 未受理联系人任务"
            )
        }

        var lastResult: ScrmTaskResult? = null
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) sleepMillis(pollDelayMillis)
            val result = taskApi.getTask(submitted.taskId)
            lastResult = result
            when (resolveScrmTaskResult(result).pollState) {
                ScrmTaskPollState.Completed -> {
                    return ScrmContactTaskOutcome(
                        taskId = submitted.taskId,
                        message = result.message?.takeIf { it.isNotBlank() }
                            ?: submitted.message?.takeIf { it.isNotBlank() }
                            ?: "联系人任务已完成 #${submitted.taskId}",
                        shouldReloadContacts = reloadContactsOnSuccess,
                        data = result.data
                    )
                }

                ScrmTaskPollState.FailedFinal -> {
                    throw ScrmRequestException(
                        statusCode = 400,
                        message = result.message ?: "联系人任务执行失败 #${submitted.taskId}"
                    )
                }

                ScrmTaskPollState.ManualReview -> {
                    throw ScrmInvalidResponseException(
                        result.message ?: "联系人任务结果未知 #${submitted.taskId}"
                    )
                }

                ScrmTaskPollState.Pending -> Unit
            }
        }

        return ScrmContactTaskOutcome(
            taskId = submitted.taskId,
            message = lastResult?.message?.takeIf { it.isNotBlank() }
                ?: submitted.message?.takeIf { it.isNotBlank() }
                ?: "联系人任务仍在处理中 #${submitted.taskId}，请稍后刷新",
            shouldReloadContacts = false,
            data = lastResult?.data
        )
    }
}
