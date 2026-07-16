package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.moments.scrmCircleIdForMomentPostId as parseMomentPostId
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentTaskAwaitOutcome
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.submitScrmMomentTaskAndAwait as runScrmMomentTask

internal fun submitScrmMomentTaskAndAwait(
    taskApi: ScrmTaskApi,
    treatMissingRecentTaskAsAccepted: Boolean = false,
    pollDelayMillis: Long = 1_500L,
    maxPollAttempts: Int = 8,
    sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) },
    submit: () -> ScrmTaskSubmissionResult
): ScrmMomentTaskAwaitOutcome = runScrmMomentTask(
    taskApi = taskApi,
    treatMissingRecentTaskAsAccepted = treatMissingRecentTaskAsAccepted,
    pollDelayMillis = pollDelayMillis,
    maxPollAttempts = maxPollAttempts,
    sleepMillis = sleepMillis,
    submit = submit
)

@Suppress("unused")
internal fun scrmCircleIdForMomentPostId(postId: String): Long? = parseMomentPostId(postId)
