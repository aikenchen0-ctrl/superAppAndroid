package com.paifa.univerge.accessibility.scrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFlowTest {
    @Test
    fun amountConversionUsesFenWithoutFloatingPointDrift() {
        assertEquals(1234, PaymentAmount.parseFen("12.34"))
        assertEquals("12.34", PaymentAmount.formatYuan(1234))
    }

    @Test
    fun redPacketRequestSerializesDocumentedFields() {
        val request = ScrmSendLuckyMoneyRequest(
            deviceUuid = "device-1",
            weChatId = "wxid-me",
            friendId = "room@chatroom",
            moneyFen = 500,
            number = 2,
            paymentPassword = "123456",
            wish = "恭喜发财"
        )
        val json = Json.encodeToString(ScrmSendLuckyMoneyRequest.serializer(), request)
        val objectValue = Json.parseToJsonElement(json).jsonObject
        assertEquals("room@chatroom", objectValue["friendId"]?.jsonPrimitive?.content)
        assertEquals("500", objectValue["moneyFen"]?.jsonPrimitive?.content)
        assertEquals("2", objectValue["number"]?.jsonPrimitive?.content)
    }

    @Test
    fun taskStatusMapsUnknownAndProcessingExplicitly() {
        assertEquals(PaymentTaskState.PROCESSING, PaymentTaskState.fromWire("processing"))
        assertEquals(PaymentTaskState.UNKNOWN, PaymentTaskState.fromWire("unknown"))
        assertEquals(PaymentTaskState.FAILED, PaymentTaskState.fromWire("failed"))
    }

    @Test
    fun redPacketDetailReadsCommonStateKeys() {
        val detail = PaymentDetailParser.parseRedPacket(
            """{"status":"RECEIVED","amountFen":888,"receivedBy":"wxid-me","entries":[{"name":"张三","amountFen":500}]}"""
        )
        assertEquals(PaymentCardState.RECEIVED, detail.state)
        assertEquals(888, detail.amountFen)
        assertTrue(detail.recipientId == "wxid-me")
        assertEquals("张三", detail.entries.single().name)
    }

    @Test
    fun paymentReadbackKeepsSubmittedTaskPendingUntilTerminalPayloadArrives() {
        val submitted = PaymentReadback.fromSubmission(
            ScrmTaskSubmissionResult(taskId = 99, success = true, message = "queued")
        )

        assertEquals(PaymentReadbackState.PROCESSING, submitted.state)
        assertEquals(99L, submitted.taskId)
    }

    @Test
    fun walletBalanceParserPrefersFenAndFormatsYuan() {
        val balance = WalletBalanceParser.parse(
            Json.parseToJsonElement("""{"data":{"balanceFen":862866}}""")
        )

        assertEquals(862866, balance.fen)
        assertEquals("¥8628.66", balance.displayText)
    }

    @Test
    fun splitBillDraftCalculatesPerPersonAndRejectsEmptyMembers() {
        val draft = SplitBillDraft(totalFen = 32000, participantCount = 4)
        assertEquals(8000, draft.perPersonFen)
        assertTrue(runCatching { SplitBillDraft(100, 0) }.isFailure)
    }

    @Test
    fun paymentRequestFactoryRejectsMissingAccountMessageAndPasswordContext() {
        assertTrue(
            runCatching {
                PaymentRequestFactory.sendLuckyMoney(
                    route = ScrmFloatingAccountRoute("device-1", "wxid-me"),
                    conversationId = "wxid-friend",
                    amountText = "8.88",
                    packetCount = 1,
                    paymentPassword = "",
                    wish = "best wishes"
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                PaymentRequestFactory.redPacketQuery(
                    route = ScrmFloatingAccountRoute("device-1", "wxid-me"),
                    serverMessageId = null,
                    msgSvrId = "88990011"
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                PaymentRequestFactory.takeTransfer(
                    route = ScrmFloatingAccountRoute("device-1", "wxid-me"),
                    serverMessageId = 71L,
                    msgSvrId = null
                )
            }.isFailure
        )
    }

    @Test
    fun idempotencyRegistryReusesKeyForSameIntentAndRotatesAfterCompletion() {
        var sequence = 0
        val registry = PaymentIdempotencyRegistry { "payment-${++sequence}" }

        val first = registry.keyFor("send-red-packet:device-1:wxid-me:friend-1:888:1")
        val retry = registry.keyFor("send-red-packet:device-1:wxid-me:friend-1:888:1")
        registry.complete("send-red-packet:device-1:wxid-me:friend-1:888:1")
        val nextIntent = registry.keyFor("send-red-packet:device-1:wxid-me:friend-1:888:1")

        assertEquals(first, retry)
        assertFalse(first == nextIntent)
    }

    @Test
    fun paymentTaskRunnerUpdatesClaimOnlyAfterTerminalSuccess() {
        val taskApi = SequenceTaskApi(
            taskResult(taskId = 91L, status = "processing", success = true),
            taskResult(taskId = 91L, status = "success", success = true)
        )
        val runner = PaymentTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 2,
            sleepMillis = {}
        )

        val outcome = runner.submitAndAwait {
            ScrmTaskSubmissionResult(taskId = 91L, success = true, message = "accepted")
        }

        assertEquals(PaymentTaskState.SUCCESS, outcome.state)
        assertTrue(outcome.completed)
        assertEquals(2, taskApi.calls)
    }

    @Test
    fun paymentTaskRunnerLeavesClaimPendingWhenNoTerminalResultArrives() {
        val taskApi = SequenceTaskApi(
            taskResult(taskId = 92L, status = "processing", success = true)
        )
        val runner = PaymentTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        val outcome = runner.submitAndAwait {
            ScrmTaskSubmissionResult(taskId = 92L, success = true, message = "accepted")
        }

        assertEquals(PaymentTaskState.PROCESSING, outcome.state)
        assertFalse(outcome.completed)
        assertNull(outcome.data)
    }

    private fun taskResult(taskId: Long, status: String, success: Boolean) = ScrmTaskResult(
        taskId = taskId,
        success = success,
        status = status,
        resultUnknown = false,
        receivedAt = "2026-08-12T10:00:00Z",
        rawHidden = true
    )

    private class SequenceTaskApi(vararg results: ScrmTaskResult) : ScrmTaskApi {
        private val queue = results.toMutableList()
        var calls: Int = 0
            private set

        override fun getTask(taskId: Long): ScrmTaskResult {
            calls += 1
            return queue.removeAt(0)
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int): ScrmRecentTaskResults {
            error("not used")
        }
    }
}
