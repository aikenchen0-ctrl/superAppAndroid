package com.paifa.univerge.accessibility.scrm

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

private const val DefaultPaymentTaskPollDelayMillis = 1_500L
private const val DefaultPaymentTaskMaxPollAttempts = 8

/**
 * Builds payment DTOs only after all context needed by the device task is present.
 * `msgSvrId` is validated for message-based operations but is intentionally not sent
 * to OpenAPI, which accepts only the SCRM `messageId`.
 */
internal object PaymentRequestFactory {
    fun sendLuckyMoney(
        route: ScrmFloatingAccountRoute,
        conversationId: String?,
        amountText: String,
        packetCount: Int,
        paymentPassword: String,
        wish: String
    ): ScrmSendLuckyMoneyRequest {
        val amountFen = validatedAmount(amountText, maxFen = 20_000)
        require(packetCount in 1..100) { "红包个数必须在 1 到 100 之间" }
        return ScrmSendLuckyMoneyRequest(
            deviceUuid = route.deviceUuid,
            weChatId = route.weChatId,
            friendId = requireConversationId(conversationId),
            moneyFen = amountFen,
            number = packetCount,
            paymentPassword = requirePaymentPassword(paymentPassword),
            wish = wish.trim().takeIf { it.isNotBlank() }
        )
    }

    fun sendRemittance(
        route: ScrmFloatingAccountRoute,
        recipientId: String?,
        roomId: String?,
        amountText: String,
        paymentPassword: String,
        memo: String
    ): ScrmSendRemittanceRequest {
        val normalizedRoomId = roomId?.trim()?.takeIf { it.isNotBlank() }
        return ScrmSendRemittanceRequest(
            deviceUuid = route.deviceUuid,
            weChatId = route.weChatId,
            friendId = requireConversationId(recipientId),
            roomId = normalizedRoomId,
            moneyFen = validatedAmount(amountText, maxFen = Int.MAX_VALUE),
            paymentPassword = requirePaymentPassword(paymentPassword),
            memo = memo.trim().takeIf { it.isNotBlank() }
        )
    }

    fun walletBalance(route: ScrmFloatingAccountRoute): ScrmWalletBalanceRequest =
        ScrmWalletBalanceRequest(
            deviceUuid = route.deviceUuid,
            weChatId = route.weChatId,
            flag = 0
        )

    fun redPacketQuery(
        route: ScrmFloatingAccountRoute,
        serverMessageId: Long?,
        msgSvrId: String?
    ): ScrmRedPacketQueryByMessageRequest = ScrmRedPacketQueryByMessageRequest(
        deviceUuid = route.deviceUuid,
        messageId = requireRealMessageId(serverMessageId, msgSvrId)
    )

    fun takeLuckyMoney(
        route: ScrmFloatingAccountRoute,
        serverMessageId: Long?,
        msgSvrId: String?
    ): ScrmTakeLuckyMoneyByMessageRequest = ScrmTakeLuckyMoneyByMessageRequest(
        deviceUuid = route.deviceUuid,
        messageId = requireRealMessageId(serverMessageId, msgSvrId),
        refuse = false
    )

    fun takeTransfer(
        route: ScrmFloatingAccountRoute,
        serverMessageId: Long?,
        msgSvrId: String?
    ): ScrmTakeTransferByMessageRequest = ScrmTakeTransferByMessageRequest(
        deviceUuid = route.deviceUuid,
        messageId = requireRealMessageId(serverMessageId, msgSvrId),
        refuse = false
    )

    private fun requireConversationId(value: String?): String =
        value?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("缺少真实会话或收款人 wxid")

    private fun validatedAmount(value: String, maxFen: Int): Int {
        val amountFen = PaymentAmount.parseFen(value)
        require(amountFen > 0) { "金额必须大于 0" }
        require(amountFen <= maxFen) { "金额超过接口允许范围" }
        return amountFen
    }

    private fun requirePaymentPassword(value: String): String {
        val password = value.trim()
        require(password.matches(Regex("\\d{6}"))) { "支付密码必须为 6 位数字" }
        return password
    }

    private fun requireRealMessageId(serverMessageId: Long?, msgSvrId: String?): Long {
        require(serverMessageId != null && serverMessageId > 0L) { "缺少 SCRM 服务端 messageId" }
        require(!msgSvrId.isNullOrBlank()) { "缺少真实 msgSvrId" }
        return serverMessageId
    }
}

/**
 * Keeps one idempotency key for one in-memory payment intent. The password is never
 * stored: only a one-way digest participates in the intent key so a password change
 * creates a fresh server idempotency key.
 */
internal class PaymentIdempotencyRegistry(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val keysByIntent = linkedMapOf<String, String>()

    @Synchronized
    fun keyFor(intent: String, paymentPassword: String? = null): String {
        require(intent.isNotBlank()) { "支付意图不能为空" }
        val stableIntent = if (paymentPassword == null) intent else "$intent:${sha256(paymentPassword)}"
        return keysByIntent.getOrPut(stableIntent) { keyGenerator() }
    }

    @Synchronized
    fun complete(intent: String, paymentPassword: String? = null) {
        if (intent.isBlank()) return
        val stableIntent = if (paymentPassword == null) intent else "$intent:${sha256(paymentPassword)}"
        keysByIntent.remove(stableIntent)
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal data class PaymentTaskOutcome(
    val taskId: Long,
    val state: PaymentTaskState,
    val message: String,
    val data: JsonElement? = null
) {
    val completed: Boolean
        get() = state == PaymentTaskState.SUCCESS
}

internal fun PaymentTaskOutcome.toPaymentReadback(
    detail: PaymentDetail? = null
): PaymentReadback {
    detail?.let { return PaymentReadback.fromDetail(it) }
    return PaymentReadback(
        state = when (state) {
            PaymentTaskState.PROCESSING -> PaymentReadbackState.PROCESSING
            PaymentTaskState.SUCCESS -> PaymentReadbackState.UNKNOWN
            PaymentTaskState.FAILED -> PaymentReadbackState.FAILED
            PaymentTaskState.UNKNOWN -> PaymentReadbackState.UNKNOWN
        },
        taskId = taskId,
        message = message
    )
}

/** Polls only the task created by one payment operation. It never resubmits a write. */
internal class PaymentTaskRunner(
    private val taskApi: ScrmTaskApi,
    private val pollDelayMillis: Long = DefaultPaymentTaskPollDelayMillis,
    private val maxPollAttempts: Int = DefaultPaymentTaskMaxPollAttempts,
    private val sleepMillis: (Long) -> Unit = { delay -> Thread.sleep(delay) }
) {
    init {
        require(pollDelayMillis > 0) { "pollDelayMillis 必须大于 0" }
        require(maxPollAttempts > 0) { "maxPollAttempts 必须大于 0" }
    }

    fun submitAndAwait(submit: () -> ScrmTaskSubmissionResult): PaymentTaskOutcome {
        val submitted = submit()
        if (!submitted.success) {
            return PaymentTaskOutcome(
                taskId = submitted.taskId,
                state = PaymentTaskState.FAILED,
                message = submitted.message ?: "支付任务未被服务端受理",
                data = submitted.data
            )
        }

        var lastResult: ScrmTaskResult? = null
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) sleepMillis(pollDelayMillis)
            val result = taskApi.getTask(submitted.taskId)
            lastResult = result
            when (resolveScrmTaskResult(result).pollState) {
                ScrmTaskPollState.Completed -> return PaymentTaskOutcome(
                    taskId = submitted.taskId,
                    state = PaymentTaskState.SUCCESS,
                    message = result.message ?: submitted.message ?: "支付任务已完成",
                    data = result.data
                )
                ScrmTaskPollState.FailedFinal -> return PaymentTaskOutcome(
                    taskId = submitted.taskId,
                    state = PaymentTaskState.FAILED,
                    message = result.message ?: "支付任务执行失败",
                    data = result.data
                )
                ScrmTaskPollState.ManualReview -> return PaymentTaskOutcome(
                    taskId = submitted.taskId,
                    state = PaymentTaskState.UNKNOWN,
                    message = result.message ?: "支付任务状态需要人工核对",
                    data = result.data
                )
                ScrmTaskPollState.Pending -> Unit
            }
        }
        return PaymentTaskOutcome(
            taskId = submitted.taskId,
            state = PaymentTaskState.PROCESSING,
            message = lastResult?.message ?: submitted.message ?: "支付任务处理中",
            data = lastResult?.data
        )
    }
}

internal data class PaymentMessageIds(
    val messageId: Long?,
    val msgSvrId: String?
) {
    companion object {
        fun from(data: JsonElement?): PaymentMessageIds {
            val root = data as? JsonObject ?: return PaymentMessageIds(null, null)
            return PaymentMessageIds(
                messageId = firstLong(root, listOf("messageId", "id", "remoteMessageId")),
                msgSvrId = firstString(root, listOf("msgSvrId", "msgSvrID", "messageServerId", "remoteMessageServerId"))
            )
        }

        private fun firstLong(root: JsonObject, keys: List<String>): Long? = keys
            .firstNotNullOfOrNull { key -> root[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() }

        private fun firstString(root: JsonObject, keys: List<String>): String? = keys
            .firstNotNullOfOrNull { key -> root[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
    }
}

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
