package com.paifa.ubikitouch.accessibility.scrm

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

internal object PaymentAmount {
    fun parseFen(value: String): Int {
        val amount = value.trim().removePrefix("¥").toBigDecimalOrNull()
            ?: throw IllegalArgumentException("金额格式无效")
        require(amount >= BigDecimal.ZERO) { "金额不能为负数" }
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    }

    fun formatYuan(fen: Int): String {
        require(fen >= 0) { "金额不能为负数" }
        return BigDecimal(fen).movePointLeft(2).setScale(2).toPlainString()
    }
}

internal enum class PaymentTaskState {
    PROCESSING, SUCCESS, FAILED, UNKNOWN;

    companion object {
        fun fromWire(value: String?): PaymentTaskState = when (value?.trim()?.lowercase()) {
            "processing", "pending", "queued", "running" -> PROCESSING
            "success", "succeeded", "completed" -> SUCCESS
            "failed", "failure", "error" -> FAILED
            else -> UNKNOWN
        }
    }
}

internal enum class PaymentCardState { AVAILABLE, RECEIVED, EXPIRED, EMPTY, REFUSED, UNKNOWN }

internal data class PaymentDetail(
    val state: PaymentCardState,
    val amountFen: Int? = null,
    val recipientId: String? = null,
    val message: String? = null,
    val entries: List<PaymentEntry> = emptyList()
)

internal data class PaymentEntry(val name: String, val amountFen: Int? = null)

internal enum class PaymentReadbackState { IDLE, LOADING, PROCESSING, AVAILABLE, RECEIVED, EXPIRED, EMPTY, REFUSED, FAILED, UNKNOWN }

internal data class PaymentReadback(
    val state: PaymentReadbackState = PaymentReadbackState.IDLE,
    val taskId: Long? = null,
    val detail: PaymentDetail? = null,
    val message: String? = null
) {
    companion object {
        fun fromSubmission(submission: ScrmTaskSubmissionResult): PaymentReadback {
            return PaymentReadback(
                state = PaymentReadbackState.PROCESSING,
                taskId = submission.taskId,
                message = submission.message
            )
        }

        fun fromDetail(detail: PaymentDetail): PaymentReadback = PaymentReadback(
            state = when (detail.state) {
                PaymentCardState.AVAILABLE -> PaymentReadbackState.AVAILABLE
                PaymentCardState.RECEIVED -> PaymentReadbackState.RECEIVED
                PaymentCardState.EXPIRED -> PaymentReadbackState.EXPIRED
                PaymentCardState.EMPTY -> PaymentReadbackState.EMPTY
                PaymentCardState.REFUSED -> PaymentReadbackState.REFUSED
                PaymentCardState.UNKNOWN -> PaymentReadbackState.UNKNOWN
            },
            detail = detail,
            message = detail.message
        )
    }
}

internal object PaymentDetailParser {
    fun parseRedPacket(rawJson: String): PaymentDetail =
        parseRedPacket(Json.parseToJsonElement(rawJson))

    fun parseRedPacket(element: JsonElement): PaymentDetail = parse(element)
    fun parseTransfer(element: JsonElement): PaymentDetail = parse(element)

    private fun parse(element: JsonElement): PaymentDetail {
        val root = unwrap(element.jsonObject)
        val wireState = root["status"]?.jsonPrimitive?.contentOrNull
            ?: root["state"]?.jsonPrimitive?.contentOrNull
        val state = when (wireState?.trim()?.uppercase()) {
            "RECEIVED", "CLAIMED", "TAKEN", "SUCCESS" -> PaymentCardState.RECEIVED
            "EXPIRED", "TIMEOUT" -> PaymentCardState.EXPIRED
            "EMPTY", "FINISHED", "ALL_TAKEN" -> PaymentCardState.EMPTY
            "REFUSED", "REJECTED" -> PaymentCardState.REFUSED
            "AVAILABLE", "WAITING_RECEIVE", "UNCLAIMED", "PENDING" -> PaymentCardState.AVAILABLE
            else -> PaymentCardState.UNKNOWN
        }
        val amount = listOf("amountFen", "moneyFen", "amount")
            .firstNotNullOfOrNull { root[it]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }
        val recipient = listOf("receivedBy", "recipientId", "friendId")
            .firstNotNullOfOrNull { root[it]?.jsonPrimitive?.contentOrNull }
        val entries = listOf("entries", "records", "receivers")
            .firstNotNullOfOrNull { root[it]?.jsonArray }
            ?.mapNotNull { item ->
                val value = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val name = listOf("name", "nickname", "receiverName", "wxid")
                    .firstNotNullOfOrNull { value[it]?.jsonPrimitive?.contentOrNull }
                    ?: return@mapNotNull null
                val entryAmount = listOf("amountFen", "moneyFen", "amount")
                    .firstNotNullOfOrNull { value[it]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }
                PaymentEntry(name, entryAmount)
            }
            .orEmpty()
        return PaymentDetail(state, amount, recipient, root["message"]?.jsonPrimitive?.contentOrNull, entries)
    }

    private fun unwrap(root: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        val nested = listOf("data", "result", "payload", "detail").firstNotNullOfOrNull {
            root[it] as? kotlinx.serialization.json.JsonObject
        }
        return nested?.let(::unwrap) ?: root
    }
}

internal data class WalletBalance(
    val fen: Int? = null,
    val message: String? = null
) {
    val displayText: String
        get() = fen?.let { "¥${PaymentAmount.formatYuan(it)}" } ?: "余额待回读"
}

internal object WalletBalanceParser {
    fun parse(element: JsonElement): WalletBalance {
        val root = unwrap(element.jsonObject)
        val fen = listOf("balanceFen", "moneyFen", "walletBalanceFen")
            .firstNotNullOfOrNull { root[it]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }
            ?: root["balance"]?.jsonPrimitive?.contentOrNull
                ?.toBigDecimalOrNull()
                ?.movePointRight(2)
                ?.setScale(0, RoundingMode.UNNECESSARY)
                ?.toInt()
        return WalletBalance(fen = fen, message = root["message"]?.jsonPrimitive?.contentOrNull)
    }

    private fun unwrap(root: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        val nested = listOf("data", "result", "payload").firstNotNullOfOrNull {
            root[it] as? kotlinx.serialization.json.JsonObject
        }
        return nested?.let(::unwrap) ?: root
    }
}

/** UI-only AA collection draft. The public API has no standalone AA collection endpoint. */
internal data class SplitBillDraft(
    val totalFen: Int,
    val participantCount: Int
) {
    init {
        require(totalFen > 0) { "群收款总金额必须大于 0" }
        require(participantCount > 0) { "至少选择一位收款成员" }
    }

    val perPersonFen: Int
        get() = totalFen / participantCount
}
