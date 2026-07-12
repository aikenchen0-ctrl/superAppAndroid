package com.paifa.ubikitouch.accessibility.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.paifa.ubikitouch.accessibility.scrm.ScrmConcurrentOperationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountRecord
import com.paifa.ubikitouch.accessibility.scrm.ScrmOutboxItem
import com.paifa.ubikitouch.accessibility.scrm.ScrmOutboxPersistence
import com.paifa.ubikitouch.accessibility.scrm.ScrmOutboxState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskPollState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskPersistence
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val ForbiddenOutboxJsonKeys = setOf("apikey", "x-api-key", "authorization")
private val OutboxJson = Json { isLenient = false }

internal class ScrmOperationStore(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : AutoCloseable, ScrmTaskPersistence, ScrmOutboxPersistence {
    private val database = FloatingChatDatabase(context)

    override fun close() {
        database.close()
    }

    fun enqueue(item: ScrmOutboxItem): ScrmOutboxItem {
        validateForEnqueue(item)
        return database.writableDatabase.transactionResult {
            insertOutboxOrReturnExisting(item)
        }
    }

    fun enqueueMessage(
        message: LocalChatMessage,
        item: ScrmOutboxItem
    ): ScrmOutboxItem {
        validateForEnqueue(item)
        require(item.aggregateType == "message") { "消息 Outbox 的 aggregateType 必须是 message" }
        require(item.aggregateId == message.messageId) { "消息 ID 与 Outbox aggregateId 不一致" }
        require(item.clientRequestId == message.clientRequestId) {
            "消息与 Outbox 的 clientRequestId 不一致"
        }
        require(message.sendState == ScrmOutboxState.Queued.storageValue) {
            "新消息必须以 QUEUED 状态入队"
        }
        return database.writableDatabase.transactionResult {
            outboxByClientRequestIdInTransaction(item.clientRequestId)?.let { return@transactionResult it }
            ensureThreadForMessage(message)
            insertWithOnConflict(
                FloatingChatDatabaseContract.tableMessages,
                null,
                message.toContentValues(),
                SQLiteDatabase.CONFLICT_ABORT
            )
            insertOutboxOrReturnExisting(item)
        }
    }

    fun outboxByClientRequestId(clientRequestId: String): ScrmOutboxItem? {
        require(clientRequestId.isNotBlank()) { "clientRequestId 不能为空" }
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableScrmOutbox,
            null,
            "client_request_id = ?",
            arrayOf(clientRequestId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmOutboxItem() else null
        }
    }

    fun outboxById(outboxId: String): ScrmOutboxItem? {
        require(outboxId.isNotBlank()) { "outboxId 不能为空" }
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableScrmOutbox,
            null,
            "outbox_id = ?",
            arrayOf(outboxId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmOutboxItem() else null
        }
    }

    fun outboxCount(): Int {
        return database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${FloatingChatDatabaseContract.tableScrmOutbox}",
            emptyArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun upsertAccount(account: ScrmAccountRecord) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableScrmAccounts,
            null,
            account.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun accountByWeChatId(weChatId: String): ScrmAccountRecord? {
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableScrmAccounts,
            null,
            "wechat_id = ?",
            arrayOf(weChatId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmAccountRecord() else null
        }
    }

    override fun upsertTask(task: ScrmTaskRecord) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableScrmTasks,
            null,
            task.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun taskById(taskId: Long): ScrmTaskRecord? {
        require(taskId > 0) { "taskId 必须大于 0" }
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableScrmTasks,
            null,
            "task_id = ?",
            arrayOf(taskId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmTaskRecord() else null
        }
    }

    override fun applyTaskResult(
        task: ScrmTaskRecord,
        outboxState: ScrmOutboxState
    ): ScrmOutboxItem {
        val outboxId = task.outboxId ?: throw IllegalArgumentException("任务缺少 outboxId")
        val now = clockMillis()
        return database.writableDatabase.transactionResult {
            val currentOutbox = outboxByIdInTransaction(outboxId)
                ?: throw IllegalArgumentException("Outbox 不存在")
            val effectiveOutboxState = currentOutbox.state.withoutProgressRegression(outboxState)
            require(currentOutbox.state.canTransitionTo(effectiveOutboxState)) {
                "不允许从 ${currentOutbox.state.storageValue} 转换到 " +
                    effectiveOutboxState.storageValue
            }
            val existingTask = taskByIdInTransaction(task.taskId)
            val storedTask = task.copy(
                leaseOwner = null,
                leaseUntil = null,
                createdAt = existingTask?.createdAt ?: task.createdAt,
                updatedAt = now
            )
            insertWithOnConflict(
                FloatingChatDatabaseContract.tableScrmTasks,
                null,
                storedTask.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )

            val terminal = effectiveOutboxState.isTerminal
            val updatedOutbox = currentOutbox.copy(
                state = effectiveOutboxState,
                nextAttemptAt = null,
                remoteTaskId = task.taskId,
                taskResultUrl = task.taskResultUrl ?: currentOutbox.taskResultUrl,
                lastErrorCode = task.resultCode.takeIf {
                    effectiveOutboxState != ScrmOutboxState.Succeeded
                },
                lastErrorMessage = task.message.takeIf {
                    effectiveOutboxState != ScrmOutboxState.Succeeded
                },
                leaseOwner = null,
                leaseUntil = null,
                updatedAt = now,
                completedAt = if (terminal) now else currentOutbox.completedAt
            )
            val rows = update(
                FloatingChatDatabaseContract.tableScrmOutbox,
                updatedOutbox.toContentValues(),
                "outbox_id = ? AND state = ?",
                arrayOf(outboxId, currentOutbox.state.storageValue)
            )
            if (rows != 1) {
                throw ScrmConcurrentOperationException("Outbox 状态并发更新失败")
            }
            updateLinkedMessage(updatedOutbox)
            updatedOutbox
        }
    }

    override fun claimDueTask(
        workerId: String,
        now: Long,
        leaseDurationMillis: Long
    ): ScrmTaskRecord? {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        require(leaseDurationMillis > 0) { "leaseDurationMillis 必须大于 0" }
        return database.writableDatabase.transactionResult {
            val candidate = query(
                FloatingChatDatabaseContract.tableScrmTasks,
                null,
                "poll_state = ? AND (next_poll_at IS NULL OR next_poll_at <= ?) " +
                    "AND (lease_until IS NULL OR lease_until < ?)",
                arrayOf(
                    ScrmTaskPollState.Pending.storageValue,
                    now.toString(),
                    now.toString()
                ),
                null,
                null,
                "created_at ASC",
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toScrmTaskRecord() else null
            } ?: return@transactionResult null

            val claimed = candidate.copy(
                pollCount = candidate.pollCount + 1,
                leaseOwner = workerId,
                leaseUntil = now + leaseDurationMillis,
                updatedAt = now
            )
            val rows = update(
                FloatingChatDatabaseContract.tableScrmTasks,
                claimed.toContentValues(),
                "task_id = ? AND poll_state = ? AND (lease_until IS NULL OR lease_until < ?)",
                arrayOf(
                    candidate.taskId.toString(),
                    ScrmTaskPollState.Pending.storageValue,
                    now.toString()
                )
            )
            if (rows != 1) {
                throw ScrmConcurrentOperationException("任务已被其他 worker 抢占")
            }
            claimed
        }
    }

    override fun rescheduleTaskPoll(
        taskId: Long,
        workerId: String,
        nextPollAt: Long,
        errorMessage: String
    ): ScrmTaskRecord {
        require(taskId > 0) { "taskId 必须大于 0" }
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        require(errorMessage.isNotBlank()) { "errorMessage 不能为空" }
        val now = clockMillis()
        return database.writableDatabase.transactionResult {
            val current = taskByIdInTransaction(taskId)
                ?: throw IllegalArgumentException("任务不存在")
            if (current.pollState != ScrmTaskPollState.Pending || current.leaseOwner != workerId) {
                throw ScrmConcurrentOperationException("任务租约不属于当前 worker")
            }
            val updated = current.copy(
                nextPollAt = nextPollAt,
                lastPollError = errorMessage,
                leaseOwner = null,
                leaseUntil = null,
                updatedAt = now
            )
            val rows = update(
                FloatingChatDatabaseContract.tableScrmTasks,
                updated.toContentValues(),
                "task_id = ? AND poll_state = ? AND lease_owner = ?",
                arrayOf(
                    taskId.toString(),
                    ScrmTaskPollState.Pending.storageValue,
                    workerId
                )
            )
            if (rows != 1) {
                throw ScrmConcurrentOperationException("任务租约并发更新失败")
            }
            updated
        }
    }

    override fun claimNext(
        workerId: String,
        now: Long,
        leaseDurationMillis: Long
    ): ScrmOutboxItem? {
        require(workerId.isNotBlank()) { "workerId 不能为空" }
        require(leaseDurationMillis > 0) { "leaseDurationMillis 必须大于 0" }
        return database.writableDatabase.transactionResult {
            val candidate = query(
                FloatingChatDatabaseContract.tableScrmOutbox,
                null,
                "state IN (?, ?) AND (next_attempt_at IS NULL OR next_attempt_at <= ?) " +
                    "AND (lease_until IS NULL OR lease_until < ?)",
                arrayOf(
                    ScrmOutboxState.Queued.storageValue,
                    ScrmOutboxState.FailedRetryable.storageValue,
                    now.toString(),
                    now.toString()
                ),
                null,
                null,
                "created_at ASC",
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toScrmOutboxItem() else null
            } ?: return@transactionResult null

            val claimed = candidate.copy(
                state = ScrmOutboxState.Queued,
                attemptCount = candidate.attemptCount + 1,
                leaseOwner = workerId,
                leaseUntil = now + leaseDurationMillis,
                updatedAt = now
            )
            val rows = update(
                FloatingChatDatabaseContract.tableScrmOutbox,
                claimed.toContentValues(),
                "outbox_id = ? AND state = ? AND (lease_until IS NULL OR lease_until < ?)",
                arrayOf(candidate.outboxId, candidate.state.storageValue, now.toString())
            )
            if (rows != 1) {
                throw ScrmConcurrentOperationException("Outbox 已被其他 worker 抢占")
            }
            claimed
        }
    }

    override fun transition(
        outboxId: String,
        expectedState: ScrmOutboxState,
        nextState: ScrmOutboxState,
        remoteTaskId: Long?,
        taskResultUrl: String?,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        nextAttemptAt: Long?
    ): ScrmOutboxItem {
        require(expectedState.canTransitionTo(nextState)) {
            "不允许从 ${expectedState.storageValue} 转换到 ${nextState.storageValue}"
        }
        val now = clockMillis()
        return database.writableDatabase.transactionResult {
            val current = outboxByIdInTransaction(outboxId)
                ?: throw IllegalArgumentException("Outbox 不存在")
            if (current.state != expectedState) {
                throw ScrmConcurrentOperationException("Outbox 状态已变化")
            }
            val updated = current.copy(
                state = nextState,
                nextAttemptAt = nextAttemptAt,
                remoteTaskId = remoteTaskId ?: current.remoteTaskId,
                taskResultUrl = taskResultUrl ?: current.taskResultUrl,
                lastErrorCode = lastErrorCode,
                lastErrorMessage = lastErrorMessage,
                leaseOwner = null,
                leaseUntil = null,
                updatedAt = now,
                submittedAt = if (nextState == ScrmOutboxState.Submitted) {
                    current.submittedAt ?: now
                } else {
                    current.submittedAt
                },
                completedAt = if (nextState.isTerminal) now else current.completedAt
            )
            val rows = update(
                FloatingChatDatabaseContract.tableScrmOutbox,
                updated.toContentValues(),
                "outbox_id = ? AND state = ?",
                arrayOf(outboxId, expectedState.storageValue)
            )
            if (rows != 1) {
                throw ScrmConcurrentOperationException("Outbox 状态并发更新失败")
            }
            updateLinkedMessage(updated)
            updated
        }
    }

    private fun validateForEnqueue(item: ScrmOutboxItem) {
        require(item.state == ScrmOutboxState.Queued) { "新 Outbox 必须以 QUEUED 状态入队" }
        val json = runCatching { OutboxJson.parseToJsonElement(item.requestJson) }
            .getOrElse { throw IllegalArgumentException("Outbox requestJson 必须是有效 JSON") }
        require(!json.containsForbiddenCredentialKey()) {
            "Outbox requestJson 禁止包含认证凭据字段"
        }
    }

    private fun SQLiteDatabase.insertOutboxOrReturnExisting(item: ScrmOutboxItem): ScrmOutboxItem {
        val rowId = insertWithOnConflict(
            FloatingChatDatabaseContract.tableScrmOutbox,
            null,
            item.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (rowId != -1L) return item
        return outboxByClientRequestIdInTransaction(item.clientRequestId)
            ?: throw ScrmConcurrentOperationException("Outbox ID 冲突")
    }

    private fun SQLiteDatabase.ensureThreadForMessage(message: LocalChatMessage) {
        val thread = LocalChatThread(
            threadId = message.threadId,
            kind = if (message.threadId.startsWith("private:")) "private" else "group",
            createdAt = message.createdAt,
            updatedAt = message.createdAt
        )
        insertWithOnConflict(
            FloatingChatDatabaseContract.tableThreads,
            null,
            thread.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun SQLiteDatabase.outboxByClientRequestIdInTransaction(
        clientRequestId: String
    ): ScrmOutboxItem? {
        return query(
            FloatingChatDatabaseContract.tableScrmOutbox,
            null,
            "client_request_id = ?",
            arrayOf(clientRequestId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmOutboxItem() else null
        }
    }

    private fun SQLiteDatabase.outboxByIdInTransaction(outboxId: String): ScrmOutboxItem? {
        return query(
            FloatingChatDatabaseContract.tableScrmOutbox,
            null,
            "outbox_id = ?",
            arrayOf(outboxId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmOutboxItem() else null
        }
    }

    private fun SQLiteDatabase.taskByIdInTransaction(taskId: Long): ScrmTaskRecord? {
        return query(
            FloatingChatDatabaseContract.tableScrmTasks,
            null,
            "task_id = ?",
            arrayOf(taskId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toScrmTaskRecord() else null
        }
    }

    private fun SQLiteDatabase.updateLinkedMessage(item: ScrmOutboxItem) {
        if (item.aggregateType != "message" || item.aggregateId == null) return
        val values = ContentValues().apply {
            put("send_state", item.state.storageValue)
            item.remoteTaskId?.let { put("remote_task_id", it) } ?: putNull("remote_task_id")
            put("send_error_code", item.lastErrorCode)
            put("send_error_message", item.lastErrorMessage)
        }
        update(
            FloatingChatDatabaseContract.tableMessages,
            values,
            "message_id = ? AND client_request_id = ?",
            arrayOf(item.aggregateId, item.clientRequestId)
        )
    }
}

private fun ScrmOutboxState.withoutProgressRegression(next: ScrmOutboxState): ScrmOutboxState {
    return if (this == ScrmOutboxState.Processing && next == ScrmOutboxState.Submitted) {
        this
    } else {
        next
    }
}

private fun ScrmOutboxItem.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("outbox_id", outboxId)
        put("operation_type", operationType)
        put("aggregate_type", aggregateType)
        put("aggregate_id", aggregateId)
        put("account_wechat_id", accountWeChatId)
        put("device_uuid", deviceUuid)
        put("conversation_id", conversationId)
        put("client_request_id", clientRequestId)
        put("request_json", requestJson)
        put("state", state.storageValue)
        put("attempt_count", attemptCount)
        nextAttemptAt?.let { put("next_attempt_at", it) } ?: putNull("next_attempt_at")
        remoteTaskId?.let { put("remote_task_id", it) } ?: putNull("remote_task_id")
        put("task_result_url", taskResultUrl)
        put("last_error_code", lastErrorCode)
        put("last_error_message", lastErrorMessage)
        put("lease_owner", leaseOwner)
        leaseUntil?.let { put("lease_until", it) } ?: putNull("lease_until")
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        submittedAt?.let { put("submitted_at", it) } ?: putNull("submitted_at")
        completedAt?.let { put("completed_at", it) } ?: putNull("completed_at")
    }
}

private fun ScrmAccountRecord.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("wechat_id", weChatId)
        put("device_uuid", deviceUuid)
        put("client_uuid", clientUuid)
        put("owner_id", ownerId)
        put("nickname", nickname)
        accountStatus?.let { put("account_status", it) } ?: putNull("account_status")
        put("device_online", if (deviceOnline) 1 else 0)
        put("capability_json", capabilityJson)
        capabilityCheckedAt?.let { put("capability_checked_at", it) }
            ?: putNull("capability_checked_at")
        put("updated_at", updatedAt)
    }
}

private fun ScrmTaskRecord.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("task_id", taskId)
        put("outbox_id", outboxId)
        put("operation_type", operationType)
        put("status", status)
        put("poll_state", pollState.storageValue)
        success?.let { put("success", if (it) 1 else 0) } ?: putNull("success")
        put("result_unknown", if (resultUnknown) 1 else 0)
        put("result_code", resultCode)
        put("message", message)
        put("device_uuid", deviceUuid)
        put("connection_id_hash", connectionIdHash)
        put("received_at", receivedAt)
        put("raw_hidden", if (rawHidden) 1 else 0)
        put("data_json", dataJson)
        put("task_result_url", taskResultUrl)
        put("recent_task_results_url", recentTaskResultsUrl)
        put("next_step", nextStep)
        put("last_poll_error", lastPollError)
        put("poll_count", pollCount)
        nextPollAt?.let { put("next_poll_at", it) } ?: putNull("next_poll_at")
        put("lease_owner", leaseOwner)
        leaseUntil?.let { put("lease_until", it) } ?: putNull("lease_until")
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        completedAt?.let { put("completed_at", it) } ?: putNull("completed_at")
    }
}

private fun Cursor.toScrmOutboxItem(): ScrmOutboxItem {
    return ScrmOutboxItem(
        outboxId = getString(getColumnIndexOrThrow("outbox_id")),
        operationType = getString(getColumnIndexOrThrow("operation_type")),
        aggregateType = getString(getColumnIndexOrThrow("aggregate_type")),
        aggregateId = nullableString("aggregate_id"),
        accountWeChatId = getString(getColumnIndexOrThrow("account_wechat_id")),
        deviceUuid = getString(getColumnIndexOrThrow("device_uuid")),
        conversationId = nullableString("conversation_id"),
        clientRequestId = getString(getColumnIndexOrThrow("client_request_id")),
        requestJson = getString(getColumnIndexOrThrow("request_json")),
        state = ScrmOutboxState.fromStorageValue(getString(getColumnIndexOrThrow("state"))),
        attemptCount = getInt(getColumnIndexOrThrow("attempt_count")),
        nextAttemptAt = nullableLong("next_attempt_at"),
        remoteTaskId = nullableLong("remote_task_id"),
        taskResultUrl = nullableString("task_result_url"),
        lastErrorCode = nullableString("last_error_code"),
        lastErrorMessage = nullableString("last_error_message"),
        leaseOwner = nullableString("lease_owner"),
        leaseUntil = nullableLong("lease_until"),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        submittedAt = nullableLong("submitted_at"),
        completedAt = nullableLong("completed_at")
    )
}

private fun Cursor.toScrmAccountRecord(): ScrmAccountRecord {
    return ScrmAccountRecord(
        weChatId = getString(getColumnIndexOrThrow("wechat_id")),
        deviceUuid = getString(getColumnIndexOrThrow("device_uuid")),
        clientUuid = nullableString("client_uuid"),
        ownerId = nullableString("owner_id"),
        nickname = nullableString("nickname"),
        accountStatus = nullableInt("account_status"),
        deviceOnline = getInt(getColumnIndexOrThrow("device_online")) == 1,
        capabilityJson = nullableString("capability_json"),
        capabilityCheckedAt = nullableLong("capability_checked_at"),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
    )
}

private fun Cursor.toScrmTaskRecord(): ScrmTaskRecord {
    return ScrmTaskRecord(
        taskId = getLong(getColumnIndexOrThrow("task_id")),
        outboxId = nullableString("outbox_id"),
        operationType = getString(getColumnIndexOrThrow("operation_type")),
        status = nullableString("status"),
        pollState = ScrmTaskPollState.fromStorageValue(
            getString(getColumnIndexOrThrow("poll_state"))
        ),
        success = nullableBoolean("success"),
        resultUnknown = getInt(getColumnIndexOrThrow("result_unknown")) == 1,
        resultCode = nullableString("result_code"),
        message = nullableString("message"),
        deviceUuid = nullableString("device_uuid"),
        connectionIdHash = nullableString("connection_id_hash"),
        receivedAt = nullableString("received_at"),
        rawHidden = getInt(getColumnIndexOrThrow("raw_hidden")) == 1,
        dataJson = nullableString("data_json"),
        taskResultUrl = nullableString("task_result_url"),
        recentTaskResultsUrl = nullableString("recent_task_results_url"),
        nextStep = nullableString("next_step"),
        lastPollError = nullableString("last_poll_error"),
        pollCount = getInt(getColumnIndexOrThrow("poll_count")),
        nextPollAt = nullableLong("next_poll_at"),
        leaseOwner = nullableString("lease_owner"),
        leaseUntil = nullableLong("lease_until"),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        completedAt = nullableLong("completed_at")
    )
}

private fun Cursor.nullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.nullableLong(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.nullableInt(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}

private fun Cursor.nullableBoolean(column: String): Boolean? {
    return nullableInt(column)?.let { it == 1 }
}

private fun JsonElement.containsForbiddenCredentialKey(): Boolean {
    return when (this) {
        is JsonObject -> entries.any { (key, value) ->
            key.lowercase() in ForbiddenOutboxJsonKeys || value.containsForbiddenCredentialKey()
        }
        is JsonArray -> any { it.containsForbiddenCredentialKey() }
        else -> false
    }
}

private inline fun <T> SQLiteDatabase.transactionResult(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
