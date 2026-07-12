package com.paifa.ubikitouch.accessibility.scrm

private const val DefaultMaxDispatchesPerRun = 25
private const val DefaultMaxPollsPerRun = 25

internal data class ScrmOperationProcessorResult(
    val dispatchedCount: Int,
    val polledCount: Int,
    val nextWakeAt: Long?
)

internal fun interface ScrmDueOperationProcessor {
    fun runDue(workerId: String): ScrmOperationProcessorResult
}

internal class ScrmOperationProcessor(
    private val dispatcher: ScrmOutboxDispatcher,
    private val taskTracker: ScrmTaskTracker,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxDispatchesPerRun: Int = DefaultMaxDispatchesPerRun,
    private val maxPollsPerRun: Int = DefaultMaxPollsPerRun
) : ScrmDueOperationProcessor {
    init {
        require(maxDispatchesPerRun > 0) { "maxDispatchesPerRun 必须大于 0" }
        require(maxPollsPerRun > 0) { "maxPollsPerRun 必须大于 0" }
    }

    override fun runDue(workerId: String): ScrmOperationProcessorResult {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        var dispatchedCount = 0
        var polledCount = 0
        var nextWakeAt: Long? = null

        while (dispatchedCount < maxDispatchesPerRun) {
            when (val outcome = dispatcher.dispatchNext(workerId)) {
                ScrmOutboxDispatchOutcome.Idle -> break
                is ScrmOutboxDispatchOutcome.Submitted -> {
                    dispatchedCount += 1
                    nextWakeAt = earlier(nextWakeAt, outcome.nextPollAt)
                }
                is ScrmOutboxDispatchOutcome.Failed -> {
                    dispatchedCount += 1
                }
            }
        }

        while (polledCount < maxPollsPerRun) {
            when (val outcome = taskTracker.pollNext(workerId)) {
                ScrmTaskPollOutcome.Idle -> break
                is ScrmTaskPollOutcome.RetryScheduled -> {
                    polledCount += 1
                    nextWakeAt = earlier(nextWakeAt, outcome.nextPollAt)
                }
                is ScrmTaskPollOutcome.Updated -> {
                    polledCount += 1
                    nextWakeAt = earlier(nextWakeAt, outcome.nextPollAt)
                }
            }
        }

        return ScrmOperationProcessorResult(
            dispatchedCount = dispatchedCount,
            polledCount = polledCount,
            nextWakeAt = nextWakeAt?.coerceAtLeast(clockMillis())
        )
    }

    private fun earlier(current: Long?, candidate: Long?): Long? {
        if (candidate == null) return current
        return if (current == null || candidate < current) candidate else current
    }
}
