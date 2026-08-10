package com.paifa.ubikitouch.accessibility.data

import android.app.Application
import com.paifa.ubikitouch.accessibility.scrm.ScrmConcurrentOperationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountRecord
import com.paifa.ubikitouch.accessibility.scrm.ScrmOutboxItem
import com.paifa.ubikitouch.accessibility.scrm.ScrmOutboxState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskPollState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrmOperationStoreTest {
    private lateinit var context: Application
    private var now: Long = 1_000L
    private val closeables = mutableListOf<AutoCloseable>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    @After
    fun tearDown() {
        closeables.asReversed().forEach(AutoCloseable::close)
        closeables.clear()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    @Test(timeout = 60_000L)
    fun enqueueMessagePersistsBubbleAndOutboxAtomicallyAndIsIdempotent() {
        val store = operationStore()
        val first = outbox(outboxId = "outbox-1", clientRequestId = "request-1")

        val inserted = store.enqueueMessage(message("message-1", "request-1"), first)
        val duplicate = store.enqueueMessage(
            message("message-duplicate", "request-1"),
            outbox(outboxId = "outbox-duplicate", clientRequestId = "request-1").copy(
                aggregateId = "message-duplicate"
            )
        )

        assertEquals("outbox-1", inserted.outboxId)
        assertEquals("outbox-1", duplicate.outboxId)
        assertEquals(1, store.outboxCount())
        val messages = messageStore().messagesForThread("private:friend-1")
        assertEquals(1, messages.size)
        assertEquals("message-1", messages.single().id)
        assertEquals("request-1", messages.single().clientRequestId)
        assertEquals(com.paifa.ubikitouch.core.model.FloatingChatSendState.Queued, messages.single().sendState)

        val restored = operationStore().outboxByClientRequestId("request-1")
        assertEquals(first.copy(), restored)
    }

    @Test(timeout = 60_000L)
    fun claimNextUsesLeaseAndAllowsRecoveryOnlyAfterLeaseExpires() {
        val store = operationStore()
        store.enqueue(outbox(outboxId = "outbox-1", clientRequestId = "request-1"))

        val firstClaim = store.claimNext(
            workerId = "worker-a",
            now = 1_000L,
            leaseDurationMillis = 100L
        )
        val blockedClaim = store.claimNext(
            workerId = "worker-b",
            now = 1_050L,
            leaseDurationMillis = 100L
        )
        val recoveredClaim = store.claimNext(
            workerId = "worker-b",
            now = 1_101L,
            leaseDurationMillis = 100L
        )

        assertEquals("worker-a", firstClaim?.leaseOwner)
        assertEquals(1, firstClaim?.attemptCount)
        assertEquals(1_100L, firstClaim?.leaseUntil)
        assertNull(blockedClaim)
        assertEquals("worker-b", recoveredClaim?.leaseOwner)
        assertEquals(2, recoveredClaim?.attemptCount)
        assertEquals(1_201L, recoveredClaim?.leaseUntil)
    }

    @Test(timeout = 60_000L)
    fun transitionChecksExpectedStateAndStateMachine() {
        val store = operationStore()
        store.enqueue(outbox(outboxId = "outbox-1", clientRequestId = "request-1"))

        val submitted = store.transition(
            outboxId = "outbox-1",
            expectedState = ScrmOutboxState.Queued,
            nextState = ScrmOutboxState.Submitted,
            remoteTaskId = 42L,
            taskResultUrl = "/openapi/v1/tasks/42",
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null
        )
        val staleResult = runCatching {
            store.transition(
                outboxId = "outbox-1",
                expectedState = ScrmOutboxState.Queued,
                nextState = ScrmOutboxState.Submitted,
                remoteTaskId = null,
                taskResultUrl = null,
                lastErrorCode = null,
                lastErrorMessage = null,
                nextAttemptAt = null
            )
        }
        val succeeded = store.transition(
            outboxId = "outbox-1",
            expectedState = ScrmOutboxState.Submitted,
            nextState = ScrmOutboxState.Succeeded,
            remoteTaskId = null,
            taskResultUrl = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null
        )
        val invalidResult = runCatching {
            store.transition(
                outboxId = "outbox-1",
                expectedState = ScrmOutboxState.Succeeded,
                nextState = ScrmOutboxState.Queued,
                remoteTaskId = null,
                taskResultUrl = null,
                lastErrorCode = null,
                lastErrorMessage = null,
                nextAttemptAt = null
            )
        }

        assertEquals(42L, submitted.remoteTaskId)
        assertEquals(ScrmOutboxState.Submitted, submitted.state)
        assertTrue(staleResult.exceptionOrNull() is ScrmConcurrentOperationException)
        assertEquals(ScrmOutboxState.Succeeded, succeeded.state)
        assertEquals(now, succeeded.completedAt)
        assertTrue(invalidResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test(timeout = 60_000L)
    fun enqueueRejectsCredentialFieldsInPersistedRequestJson() {
        val store = operationStore()
        val unsafe = outbox(
            outboxId = "outbox-unsafe",
            clientRequestId = "request-unsafe"
        ).copy(requestJson = "{\"X-API-Key\":\"secret\"}")

        val result = runCatching { store.enqueue(unsafe) }

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull().toString().contains("secret"))
        assertEquals(0, store.outboxCount())
    }

    @Test(timeout = 60_000L)
    fun accountSnapshotUpsertPersistsLatestCapabilityStateWithoutLoggingIdentifiers() {
        val store = operationStore()
        val first = ScrmAccountRecord(
            weChatId = "wxid_sensitive",
            deviceUuid = "device-sensitive",
            clientUuid = "client-sensitive",
            ownerId = "owner-sensitive",
            nickname = "测试账号",
            accountStatus = 1,
            deviceOnline = false,
            capabilityJson = "{\"readyCount\":0}",
            capabilityCheckedAt = 900L,
            updatedAt = 900L
        )

        store.upsertAccount(first)
        store.upsertAccount(first.copy(deviceOnline = true, capabilityJson = "{\"readyCount\":3}", updatedAt = 1_000L))
        val restored = store.accountByWeChatId("wxid_sensitive")

        assertEquals(true, restored?.deviceOnline)
        assertEquals("{\"readyCount\":3}", restored?.capabilityJson)
        assertEquals(1_000L, restored?.updatedAt)
        assertFalse(restored.toString().contains("wxid_sensitive"))
        assertFalse(restored.toString().contains("device-sensitive"))
        assertFalse(restored.toString().contains("readyCount"))
    }

    @Test(timeout = 60_000L)
    fun taskPollingLeaseRecoversAfterProcessInterruption() {
        val store = operationStore()
        store.enqueue(outbox(outboxId = "outbox-1", clientRequestId = "request-1"))
        store.upsertTask(task(taskId = 42L, outboxId = "outbox-1"))

        assertNull(store.claimDueTask("worker-a", now = 999L, leaseDurationMillis = 100L))
        val firstClaim = store.claimDueTask("worker-a", now = 1_000L, leaseDurationMillis = 100L)
        assertNull(store.claimDueTask("worker-b", now = 1_050L, leaseDurationMillis = 100L))
        val recovered = store.claimDueTask("worker-b", now = 1_101L, leaseDurationMillis = 100L)

        assertEquals(42L, firstClaim?.taskId)
        assertEquals("worker-a", firstClaim?.leaseOwner)
        assertEquals(1, firstClaim?.pollCount)
        assertEquals("worker-b", recovered?.leaseOwner)
        assertEquals(2, recovered?.pollCount)
        assertFalse(recovered.toString().contains("device-sensitive"))
    }

    @Test(timeout = 60_000L)
    fun applyTaskResultUpdatesTaskOutboxAndMessageInOneTransaction() {
        val store = operationStore()
        store.enqueueMessage(
            message("message-1", "request-1"),
            outbox(outboxId = "outbox-1", clientRequestId = "request-1")
        )
        store.transition(
            outboxId = "outbox-1",
            expectedState = ScrmOutboxState.Queued,
            nextState = ScrmOutboxState.Submitted,
            remoteTaskId = 42L,
            taskResultUrl = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null
        )
        val completedTask = task(taskId = 42L, outboxId = "outbox-1").copy(
            status = "success",
            pollState = ScrmTaskPollState.Completed,
            success = true,
            dataJson = """{"msgSvrId":"88990011"}""",
            nextPollAt = null,
            leaseOwner = "worker-a",
            leaseUntil = 1_100L,
            completedAt = now
        )

        val updated = store.applyTaskResult(
            task = completedTask,
            outboxState = ScrmOutboxState.Succeeded
        )

        assertEquals(ScrmOutboxState.Succeeded, updated.state)
        assertEquals(ScrmTaskPollState.Completed, store.taskById(42L)?.pollState)
        assertNull(store.taskById(42L)?.leaseOwner)
        assertNull(store.taskById(42L)?.leaseUntil)
        assertEquals(ScrmOutboxState.Succeeded, store.outboxById("outbox-1")?.state)
        val message = messageStore()
            .messagesForThread("private:friend-1")
            .single()
        assertEquals(com.paifa.ubikitouch.core.model.FloatingChatSendState.Succeeded, message.sendState)
        assertEquals(42L, message.remoteTaskId)
        assertEquals("88990011", message.remoteMessageServerId)
    }

    @Test(timeout = 60_000L)
    fun applyTaskResultDoesNotDowngradeProcessingOutboxWhenRemoteProgressIsUnclear() {
        val store = operationStore()
        store.enqueueMessage(
            message("message-1", "request-1"),
            outbox(outboxId = "outbox-1", clientRequestId = "request-1")
        )
        store.transition(
            outboxId = "outbox-1",
            expectedState = ScrmOutboxState.Queued,
            nextState = ScrmOutboxState.Submitted,
            remoteTaskId = 42L,
            taskResultUrl = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null
        )
        store.transition(
            outboxId = "outbox-1",
            expectedState = ScrmOutboxState.Submitted,
            nextState = ScrmOutboxState.Processing,
            remoteTaskId = 42L,
            taskResultUrl = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null
        )
        val stillPendingTask = task(taskId = 42L, outboxId = "outbox-1").copy(
            status = null,
            pollState = ScrmTaskPollState.Pending,
            success = false,
            nextPollAt = 2_000L,
            leaseOwner = "worker-a",
            leaseUntil = 1_100L
        )

        val updated = store.applyTaskResult(
            task = stillPendingTask,
            outboxState = ScrmOutboxState.Submitted
        )

        assertEquals(ScrmOutboxState.Processing, updated.state)
        assertEquals(ScrmOutboxState.Processing, store.outboxById("outbox-1")?.state)
        val message = messageStore()
            .messagesForThread("private:friend-1")
            .single()
        assertEquals(com.paifa.ubikitouch.core.model.FloatingChatSendState.Processing, message.sendState)
        assertEquals(ScrmTaskPollState.Pending, store.taskById(42L)?.pollState)
        assertEquals(2_000L, store.taskById(42L)?.nextPollAt)
    }

    @Test(timeout = 60_000L)
    fun transientTaskPollFailureReleasesOwnedLeaseAndSchedulesRetry() {
        val store = operationStore()
        store.enqueue(outbox(outboxId = "outbox-1", clientRequestId = "request-1"))
        store.upsertTask(task(taskId = 42L, outboxId = "outbox-1"))
        store.claimDueTask("worker-a", now = 1_000L, leaseDurationMillis = 100L)

        val rescheduled = store.rescheduleTaskPoll(
            taskId = 42L,
            workerId = "worker-a",
            nextPollAt = 2_000L,
            errorMessage = "连接超时"
        )
        val staleWorker = runCatching {
            store.rescheduleTaskPoll(
                taskId = 42L,
                workerId = "worker-b",
                nextPollAt = 3_000L,
                errorMessage = "stale"
            )
        }

        assertEquals(ScrmTaskPollState.Pending, rescheduled.pollState)
        assertEquals(2_000L, rescheduled.nextPollAt)
        assertEquals("连接超时", rescheduled.lastPollError)
        assertNull(rescheduled.leaseOwner)
        assertNull(rescheduled.leaseUntil)
        assertTrue(staleWorker.exceptionOrNull() is ScrmConcurrentOperationException)
    }

    private fun outbox(outboxId: String, clientRequestId: String): ScrmOutboxItem {
        return ScrmOutboxItem(
            outboxId = outboxId,
            operationType = "message.text",
            aggregateType = "message",
            aggregateId = "message-1",
            accountWeChatId = "wxid_account",
            deviceUuid = "device-1",
            conversationId = "wxid_friend",
            clientRequestId = clientRequestId,
            requestJson = "{\"content\":\"hello\"}",
            state = ScrmOutboxState.Queued,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun operationStore(): ScrmOperationStore {
        return ScrmOperationStore(context) { now }.also(closeables::add)
    }

    private fun messageStore(): FloatingChatMessageStore {
        return FloatingChatMessageStore(context).also(closeables::add)
    }

    private fun message(messageId: String, clientRequestId: String): LocalChatMessage {
        return LocalChatMessage(
            messageId = messageId,
            threadId = "private:friend-1",
            senderName = "Tester",
            messageType = "Text",
            body = "hello",
            createdAt = now,
            isFromMe = true,
            kind = "Normal",
            presentation = "Bubble",
            connectionTarget = "Account",
            sendState = "QUEUED",
            clientRequestId = clientRequestId
        )
    }

    private fun task(taskId: Long, outboxId: String): ScrmTaskRecord {
        return ScrmTaskRecord(
            taskId = taskId,
            outboxId = outboxId,
            operationType = "message.text",
            status = "queued",
            pollState = ScrmTaskPollState.Pending,
            success = null,
            resultUnknown = false,
            deviceUuid = "device-sensitive",
            rawHidden = true,
            pollCount = 0,
            nextPollAt = 1_000L,
            createdAt = 900L,
            updatedAt = 900L
        )
    }
}
