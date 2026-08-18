package com.paifa.univerge.accessibility.scrm

private const val DefaultMessageRevokePollDelayMillis = 1_500L
private const val DefaultMessageRevokeMaxPollAttempts = 8

internal class ScrmMessageRevokeTaskRunner(
    private val messageApi: ScrmMessageOperationApi,
    private val taskApi: ScrmTaskApi,
    private val pollDelayMillis: Long = DefaultMessageRevokePollDelayMillis,
    private val maxPollAttempts: Int = DefaultMessageRevokeMaxPollAttempts,
    private val sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) }
) {
    fun revokeAndAwait(
        remoteMessageId: Long,
        deviceUuid: String,
        weChatId: String,
        onTaskAccepted: (Long) -> Unit = {}
    ): ScrmContactTaskOutcome {
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }

        return ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = pollDelayMillis,
            maxPollAttempts = maxPollAttempts,
            sleepMillis = sleepMillis
        ).submitAndAwait(
            reloadContactsOnSuccess = false,
            onTaskAccepted = onTaskAccepted
        ) {
            messageApi.revokeMessage(
                messageId = remoteMessageId,
                request = ScrmMessageOperationRequest(
                    deviceUuid = deviceUuid,
                    weChatId = weChatId
                )
            )
        }
    }

    fun awaitExistingTask(taskId: Long): ScrmContactTaskOutcome {
        return ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = pollDelayMillis,
            maxPollAttempts = maxPollAttempts,
            sleepMillis = sleepMillis
        ).awaitExistingTask(taskId)
    }
}
