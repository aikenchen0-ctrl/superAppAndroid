package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
}
