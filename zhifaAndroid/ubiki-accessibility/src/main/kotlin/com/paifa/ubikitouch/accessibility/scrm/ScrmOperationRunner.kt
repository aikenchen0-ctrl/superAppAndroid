package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context
import android.os.Process
import android.util.Log
import com.paifa.ubikitouch.accessibility.data.ScrmOperationStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val ScrmOperationRunnerTag = "ScrmOperationRunner"

internal class ScrmOperationRunner(
    context: Context,
    private val operationStore: ScrmOperationStore,
    private val credentials: ScrmCredentialRepository = createAndroidScrmCredentialsStore(
        context.applicationContext
    ),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val onProcessed: (ScrmOperationProcessorResult) -> Unit = {},
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ScrmOperationRunner").apply {
            isDaemon = true
        }
    }
) : AutoCloseable {
    private val mediaContentResolver = AndroidScrmMediaContentResolver(context.applicationContext)

    private val runLoop = ScrmOperationRunLoop(
        processorFactory = ::createProcessor,
        scheduler = ScrmOperationRunScheduler { delayMillis, operation ->
            executor.schedule(operation, delayMillis, TimeUnit.MILLISECONDS)
        },
        clockMillis = clockMillis,
        workerId = "scrm-runner-${Process.myPid()}",
        onProcessed = onProcessed,
        onError = ::logFailure
    )

    fun requestRun(delayMillis: Long = 0L) {
        runLoop.requestRun(delayMillis)
    }

    override fun close() {
        executor.shutdown()
    }

    private fun createProcessor(): ScrmDueOperationProcessor? {
        val storedCredentials = credentials.load() ?: return null
        val api = ScrmApiClient(
            ScrmApiConfig(
                baseUrl = storedCredentials.baseUrl,
                apiKey = storedCredentials.apiKey
            )
        )
        return ScrmOperationProcessor(
            dispatcher = ScrmOutboxDispatcher(
                api = api,
                persistence = operationStore,
                mediaContentResolver = mediaContentResolver,
                clockMillis = clockMillis
            ),
            taskTracker = ScrmTaskTracker(
                api = api,
                persistence = operationStore,
                clockMillis = clockMillis
            ),
            clockMillis = clockMillis
        )
    }

    private fun logFailure(error: Throwable) {
        val message = (error as? ScrmException)?.toUserMessage()
            ?: error::class.java.simpleName
        Log.w(ScrmOperationRunnerTag, "SCRM operation runner failed: $message", error)
    }
}
