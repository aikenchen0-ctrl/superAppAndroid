package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.JsonElement

internal data class ScrmMomentTaskAwaitOutcome(
    val taskId: Long,
    val completed: Boolean,
    val message: String,
    val data: List<JsonElement>
)

internal const val ScrmMomentTaskPollDelayMillis = 1_500L
internal const val ScrmMomentTaskMaxPollAttempts = 8
