package com.paifa.ubikitouch.accessibility.scrm

internal enum class ScrmOutboxState(val storageValue: String) {
    Queued("QUEUED"),
    Uploading("UPLOADING"),
    Submitted("SUBMITTED"),
    Processing("PROCESSING"),
    Succeeded("SUCCEEDED"),
    FailedRetryable("FAILED_RETRYABLE"),
    FailedFinal("FAILED_FINAL"),
    Unknown("UNKNOWN"),
    Cancelled("CANCELLED");

    val isTerminal: Boolean
        get() = this == Succeeded || this == FailedFinal || this == Cancelled

    fun canTransitionTo(next: ScrmOutboxState): Boolean {
        if (this == next) return true
        return when (this) {
            Queued -> next in setOf(
                Uploading,
                Submitted,
                FailedRetryable,
                FailedFinal,
                Unknown,
                Cancelled
            )
            Uploading -> next in setOf(
                Submitted,
                FailedRetryable,
                FailedFinal,
                Unknown,
                Cancelled
            )
            FailedRetryable -> next == Queued || next == Cancelled
            Submitted -> next in setOf(Processing, Succeeded, FailedFinal, Unknown)
            Processing -> next in setOf(Succeeded, FailedFinal, Unknown)
            Unknown -> next in setOf(Processing, Succeeded, FailedFinal)
            Succeeded,
            FailedFinal,
            Cancelled -> false
        }
    }

    companion object {
        fun fromStorageValue(value: String): ScrmOutboxState {
            return entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("不支持的 Outbox 状态")
        }
    }
}

internal fun scrmOutboxFailureState(
    requestMayHaveReachedServer: Boolean,
    retryableTransportFailure: Boolean
): ScrmOutboxState {
    return when {
        requestMayHaveReachedServer -> ScrmOutboxState.Unknown
        retryableTransportFailure -> ScrmOutboxState.FailedRetryable
        else -> ScrmOutboxState.FailedFinal
    }
}

internal class ScrmConcurrentOperationException(message: String) : IllegalStateException(message)

internal data class ScrmOutboxItem(
    val outboxId: String,
    val operationType: String,
    val aggregateType: String,
    val aggregateId: String? = null,
    val accountWeChatId: String,
    val deviceUuid: String,
    val conversationId: String? = null,
    val clientRequestId: String,
    val requestJson: String,
    val state: ScrmOutboxState,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long? = null,
    val remoteTaskId: Long? = null,
    val taskResultUrl: String? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val leaseOwner: String? = null,
    val leaseUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val submittedAt: Long? = null,
    val completedAt: Long? = null
) {
    init {
        require(outboxId.isNotBlank()) { "outboxId 不能为空" }
        require(operationType.isNotBlank()) { "operationType 不能为空" }
        require(aggregateType.isNotBlank()) { "aggregateType 不能为空" }
        require(accountWeChatId.isNotBlank()) { "accountWeChatId 不能为空" }
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(clientRequestId.isNotBlank()) { "clientRequestId 不能为空" }
        require(requestJson.isNotBlank()) { "requestJson 不能为空" }
        require(attemptCount >= 0) { "attemptCount 不能小于 0" }
    }

    override fun toString(): String {
        return "ScrmOutboxItem(operationType=$operationType, aggregateType=$aggregateType, " +
            "state=$state, attemptCount=$attemptCount, remoteTaskId=$remoteTaskId, " +
            "hasError=${lastErrorCode != null || lastErrorMessage != null})"
    }
}

internal data class ScrmAccountRecord(
    val weChatId: String,
    val deviceUuid: String,
    val clientUuid: String? = null,
    val ownerId: String? = null,
    val nickname: String? = null,
    val accountStatus: Int? = null,
    val deviceOnline: Boolean,
    val capabilityJson: String? = null,
    val capabilityCheckedAt: Long? = null,
    val updatedAt: Long
) {
    init {
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
    }

    override fun toString(): String {
        return "ScrmAccountRecord(nickname=$nickname, accountStatus=$accountStatus, " +
            "deviceOnline=$deviceOnline, hasCapability=${capabilityJson != null}, " +
            "capabilityCheckedAt=$capabilityCheckedAt, updatedAt=$updatedAt)"
    }
}

internal enum class ScrmTaskPollState(val storageValue: String) {
    Pending("PENDING"),
    Completed("COMPLETED"),
    ManualReview("MANUAL_REVIEW"),
    FailedFinal("FAILED_FINAL");

    companion object {
        fun fromStorageValue(value: String): ScrmTaskPollState {
            return entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("不支持的任务轮询状态")
        }
    }
}

internal data class ScrmTaskRecord(
    val taskId: Long,
    val outboxId: String? = null,
    val operationType: String,
    val status: String? = null,
    val pollState: ScrmTaskPollState,
    val success: Boolean? = null,
    val resultUnknown: Boolean,
    val resultCode: String? = null,
    val message: String? = null,
    val deviceUuid: String? = null,
    val connectionIdHash: String? = null,
    val receivedAt: String? = null,
    val rawHidden: Boolean,
    val dataJson: String? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null,
    val nextStep: String? = null,
    val lastPollError: String? = null,
    val pollCount: Int = 0,
    val nextPollAt: Long? = null,
    val leaseOwner: String? = null,
    val leaseUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
) {
    init {
        require(taskId > 0) { "taskId 必须大于 0" }
        require(operationType.isNotBlank()) { "operationType 不能为空" }
        require(pollCount >= 0) { "pollCount 不能小于 0" }
    }

    override fun toString(): String {
        return "ScrmTaskRecord(taskId=$taskId, operationType=$operationType, status=$status, " +
            "pollState=$pollState, success=$success, resultUnknown=$resultUnknown, " +
            "resultCode=$resultCode, pollCount=$pollCount, hasData=${dataJson != null}, " +
            "hasPollError=${lastPollError != null})"
    }
}
