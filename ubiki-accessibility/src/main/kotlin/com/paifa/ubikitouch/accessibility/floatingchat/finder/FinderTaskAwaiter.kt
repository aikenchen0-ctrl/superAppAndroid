package com.paifa.ubikitouch.accessibility.floatingchat.finder

import com.paifa.ubikitouch.accessibility.scrm.ScrmInvalidResponseException
import com.paifa.ubikitouch.accessibility.scrm.ScrmRequestException
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskPollState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTimeoutException
import com.paifa.ubikitouch.accessibility.scrm.resolveScrmTaskResult
import kotlinx.serialization.json.JsonElement

internal const val FinderTaskPollDelayMillis = 1_500L
internal const val FinderTaskMaxPollAttempts = 8

internal data class FinderTaskOutcome(
    val taskId: Long,
    val completed: Boolean,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Awaits only the task already accepted by the server. Unknown and timeout results are returned
 * as explicit non-success outcomes/errors; this class never submits a second write request.
 */
internal class FinderTaskAwaiter(
    private val taskApi: ScrmTaskApi,
    private val pollDelayMillis: Long = FinderTaskPollDelayMillis,
    private val maxPollAttempts: Int = FinderTaskMaxPollAttempts,
    private val sleepMillis: (Long) -> Unit = Thread::sleep
) {
    init {
        require(pollDelayMillis > 0L) { "pollDelayMillis must be greater than 0" }
        require(maxPollAttempts > 0) { "maxPollAttempts must be greater than 0" }
    }

    fun await(submit: () -> ScrmTaskSubmissionResult): FinderTaskOutcome {
        val submitted = submit()
        if (!submitted.success) {
            throw ScrmRequestException(400, submitted.message ?: "Finder task was not accepted")
        }
        var lastMessage: String? = null
        var lastData: JsonElement? = submitted.data
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) sleepMillis(pollDelayMillis)
            val result = taskApi.getTask(submitted.taskId)
            lastMessage = result.message?.takeIf(String::isNotBlank) ?: lastMessage
            lastData = result.data ?: lastData
            when (resolveScrmTaskResult(result).pollState) {
                ScrmTaskPollState.Completed -> return FinderTaskOutcome(
                    taskId = submitted.taskId,
                    completed = true,
                    message = lastMessage ?: submitted.message ?: "Finder task completed #${submitted.taskId}",
                    data = lastData
                )

                ScrmTaskPollState.FailedFinal -> throw ScrmRequestException(
                    400,
                    lastMessage ?: "Finder task failed #${submitted.taskId}"
                )

                ScrmTaskPollState.ManualReview -> throw ScrmInvalidResponseException(
                    lastMessage ?: "Finder task result is unknown #${submitted.taskId}"
                )

                ScrmTaskPollState.Pending -> Unit
            }
        }
        throw ScrmTimeoutException()
    }
}
