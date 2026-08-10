package com.paifa.ubikitouch.accessibility.scrm

internal fun interface ScrmOperationRunScheduler {
    fun schedule(delayMillis: Long, operation: () -> Unit)
}

internal class ScrmOperationRunLoop(
    private val processorFactory: () -> ScrmDueOperationProcessor?,
    private val scheduler: ScrmOperationRunScheduler,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val workerId: String,
    private val onProcessed: (ScrmOperationProcessorResult) -> Unit = {},
    private val onError: (Throwable) -> Unit = {}
) {
    init {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
    }

    fun requestRun(delayMillis: Long = 0L) {
        scheduler.schedule(delayMillis.coerceAtLeast(0L)) {
            runScheduled()
        }
    }

    private fun runScheduled() {
        val result = try {
            processorFactory()?.runDue(workerId) ?: return
        } catch (error: Throwable) {
            onError(error)
            return
        }
        runCatching {
            onProcessed(result)
        }.onFailure(onError)
        val nextWakeAt = result.nextWakeAt ?: return
        requestRun(nextWakeAt - clockMillis())
    }
}
