package com.paifa.univerge.accessibility.scrm

import kotlinx.serialization.json.JsonElement

private const val DefaultVoiceTranscriptionPollDelayMillis = 1_500L
private const val DefaultVoiceTranscriptionMaxPollAttempts = 8

internal enum class ScrmVoiceTranscriptionState {
    SUBMISSION_FAILED,
    TASK_FAILED,
    PROCESSING,
    SUCCEEDED,
    RESULT_UNKNOWN
}

internal data class ScrmVoiceTranscriptionOutcome(
    val taskId: Long?,
    val state: ScrmVoiceTranscriptionState,
    val message: String,
    val data: JsonElement? = null
)

/**
 * 对已同步语音消息提交一次转文字任务，并只轮询该 taskId；任何状态查询都不会重提写操作。
 * 成功只表示 Android 转写任务完成，调用方仍需刷新聊天消息以读取最新 voiceText。
 */
internal class ScrmVoiceTranscriptionTaskRunner(
    private val messageApi: ScrmMessageOperationApi,
    private val taskApi: ScrmTaskApi,
    private val pollDelayMillis: Long = DefaultVoiceTranscriptionPollDelayMillis,
    private val maxPollAttempts: Int = DefaultVoiceTranscriptionMaxPollAttempts,
    private val sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) }
) {
    init {
        require(pollDelayMillis > 0L) { "pollDelayMillis 必须大于 0" }
        require(maxPollAttempts > 0) { "maxPollAttempts 必须大于 0" }
    }

    fun transcribeAndAwait(
        remoteMessageId: Long,
        deviceUuid: String,
        weChatId: String,
        onTaskAccepted: (Long) -> Unit = {}
    ): ScrmVoiceTranscriptionOutcome {
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }

        val request = ScrmMessageOperationRequest(
            deviceUuid = deviceUuid,
            weChatId = weChatId
        )
        val submitted = try {
            messageApi.transcribeVoiceMessage(remoteMessageId, request)
        } catch (error: ScrmException) {
            return ScrmVoiceTranscriptionOutcome(
                taskId = null,
                state = ScrmVoiceTranscriptionState.SUBMISSION_FAILED,
                message = error.message?.takeIf { it.isNotBlank() }
                    ?: "语音转文字任务提交失败"
            )
        }
        if (!submitted.success) {
            return ScrmVoiceTranscriptionOutcome(
                taskId = submitted.taskId,
                state = ScrmVoiceTranscriptionState.SUBMISSION_FAILED,
                message = submitted.message?.takeIf { it.isNotBlank() }
                    ?: "语音转文字任务未被服务端受理",
                data = submitted.data
            )
        }

        onTaskAccepted(submitted.taskId)
        return awaitTask(
            taskId = submitted.taskId,
            initialMessage = submitted.message,
            initialData = submitted.data
        )
    }

    /** 继续轮询既有 taskId；该路径绝不再次调用 voice-trans-text 写接口。 */
    fun awaitExistingTask(taskId: Long): ScrmVoiceTranscriptionOutcome {
        require(taskId > 0L) { "taskId 必须大于 0" }
        return awaitTask(taskId = taskId)
    }

    private fun awaitTask(
        taskId: Long,
        initialMessage: String? = null,
        initialData: JsonElement? = null
    ): ScrmVoiceTranscriptionOutcome {
        var lastResult: ScrmTaskResult? = null
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) sleepMillis(pollDelayMillis)
            val result = try {
                taskApi.getTask(taskId)
            } catch (error: ScrmException) {
                return ScrmVoiceTranscriptionOutcome(
                    taskId = taskId,
                    state = ScrmVoiceTranscriptionState.PROCESSING,
                    message = "语音转文字任务已提交，但状态查询失败：" +
                        (error.message?.takeIf { it.isNotBlank() } ?: "未知查询错误"),
                    data = lastResult?.data ?: initialData
                )
            }
            lastResult = result
            when (resolveScrmTaskResult(result).pollState) {
                ScrmTaskPollState.Completed -> return ScrmVoiceTranscriptionOutcome(
                    taskId = taskId,
                    state = ScrmVoiceTranscriptionState.SUCCEEDED,
                    message = result.message?.takeIf { it.isNotBlank() }
                        ?: initialMessage?.takeIf { it.isNotBlank() }
                        ?: "语音转文字任务已完成",
                    data = result.data
                )

                ScrmTaskPollState.FailedFinal -> return ScrmVoiceTranscriptionOutcome(
                    taskId = taskId,
                    state = ScrmVoiceTranscriptionState.TASK_FAILED,
                    message = result.message?.takeIf { it.isNotBlank() }
                        ?: "语音转文字任务执行失败",
                    data = result.data
                )

                ScrmTaskPollState.ManualReview -> return ScrmVoiceTranscriptionOutcome(
                    taskId = taskId,
                    state = ScrmVoiceTranscriptionState.RESULT_UNKNOWN,
                    message = result.message?.takeIf { it.isNotBlank() }
                        ?: "语音转文字任务结果未知，需要人工核对",
                    data = result.data
                )

                ScrmTaskPollState.Pending -> Unit
            }
        }

        return ScrmVoiceTranscriptionOutcome(
            taskId = taskId,
            state = ScrmVoiceTranscriptionState.PROCESSING,
            message = lastResult?.message?.takeIf { it.isNotBlank() }
                ?: initialMessage?.takeIf { it.isNotBlank() }
                ?: "语音转文字任务仍在处理中",
            data = lastResult?.data ?: initialData
        )
    }
}
