package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmOperationModelsTest {
    @Test
    fun outboxStateMachineAllowsOnlyRecoverableWorkflowTransitions() {
        assertTrue(ScrmOutboxState.Queued.canTransitionTo(ScrmOutboxState.Uploading))
        assertTrue(ScrmOutboxState.Queued.canTransitionTo(ScrmOutboxState.Submitted))
        assertTrue(ScrmOutboxState.Uploading.canTransitionTo(ScrmOutboxState.Submitted))
        assertTrue(ScrmOutboxState.Submitted.canTransitionTo(ScrmOutboxState.Processing))
        assertTrue(ScrmOutboxState.Processing.canTransitionTo(ScrmOutboxState.Succeeded))
        assertTrue(ScrmOutboxState.FailedRetryable.canTransitionTo(ScrmOutboxState.Queued))
        assertTrue(ScrmOutboxState.Unknown.canTransitionTo(ScrmOutboxState.Succeeded))

        assertFalse(ScrmOutboxState.Unknown.canTransitionTo(ScrmOutboxState.Queued))
        assertFalse(ScrmOutboxState.Succeeded.canTransitionTo(ScrmOutboxState.Queued))
        assertFalse(ScrmOutboxState.FailedFinal.canTransitionTo(ScrmOutboxState.Queued))
        assertFalse(ScrmOutboxState.Cancelled.canTransitionTo(ScrmOutboxState.Queued))
    }

    @Test
    fun uncertainSubmissionNeverBecomesAutomaticallyRetryable() {
        assertEquals(
            ScrmOutboxState.Unknown,
            scrmOutboxFailureState(
                requestMayHaveReachedServer = true,
                retryableTransportFailure = true
            )
        )
        assertEquals(
            ScrmOutboxState.FailedRetryable,
            scrmOutboxFailureState(
                requestMayHaveReachedServer = false,
                retryableTransportFailure = true
            )
        )
        assertEquals(
            ScrmOutboxState.FailedFinal,
            scrmOutboxFailureState(
                requestMayHaveReachedServer = false,
                retryableTransportFailure = false
            )
        )
    }

    @Test
    fun storageValuesRoundTripAndRejectUnknownState() {
        ScrmOutboxState.entries.forEach { state ->
            assertEquals(state, ScrmOutboxState.fromStorageValue(state.storageValue))
        }

        val result = runCatching { ScrmOutboxState.fromStorageValue("MYSTERY") }

        assertTrue(result.isFailure)
    }

    @Test
    fun outboxStringRepresentationOmitsPayloadAndRoutingIdentifiers() {
        val item = ScrmOutboxItem(
            outboxId = "outbox-sensitive",
            operationType = "message.text",
            aggregateType = "message",
            aggregateId = "message-sensitive",
            accountWeChatId = "wxid_sensitive",
            deviceUuid = "device-sensitive",
            conversationId = "friend-sensitive",
            clientRequestId = "request-sensitive",
            requestJson = "{\"content\":\"private message\"}",
            state = ScrmOutboxState.Queued,
            createdAt = 1L,
            updatedAt = 1L
        )

        val rendered = item.toString()

        assertFalse(rendered.contains("private message"))
        assertFalse(rendered.contains("wxid_sensitive"))
        assertFalse(rendered.contains("device-sensitive"))
        assertFalse(rendered.contains("friend-sensitive"))
        assertTrue(rendered.contains("operationType=message.text"))
        assertTrue(rendered.contains("state=Queued"))
    }
}
