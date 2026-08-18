package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.scrm.PaymentIdempotencyRegistry
import com.paifa.univerge.accessibility.scrm.PaymentRequestFactory
import com.paifa.univerge.accessibility.scrm.PaymentTaskRunner
import com.paifa.univerge.accessibility.scrm.ScrmPaymentApi
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.scrmFloatingAccountRouteForContactId
import com.paifa.univerge.accessibility.scrm.scrmFloatingContactConversationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FloatingChatTransferRecipient(
    val id: String,
    val name: String,
    val weChatId: String,
    val avatarUri: String?
)

data class FloatingChatTransferSession(
    val accountName: String,
    val recipients: List<FloatingChatTransferRecipient>,
    val requiresRecipientSelection: Boolean,
    val accountId: String,
    val roomId: String?
)

object FloatingChatTransferBridge {
    @Volatile private var session: FloatingChatTransferSession? = null
    private val idempotencyRegistry = PaymentIdempotencyRegistry()

    fun open(nextSession: FloatingChatTransferSession) {
        session = nextSession
        UniVergeAccessibilityService.instance?.requestFloatingChatTransfer()
    }

    fun currentSession(): FloatingChatTransferSession? = session

    fun submit(
        context: android.content.Context,
        recipient: FloatingChatTransferRecipient?,
        amount: String,
        note: String,
        paymentPassword: String,
        onResult: (String) -> Unit
    ) {
        val current = session
        val route = current?.let { value -> scrmFloatingAccountRouteForContactId(value.accountId) }
        val recipientId = recipient?.id?.let(::scrmFloatingContactConversationId)
        if (current == null || route == null || recipientId.isNullOrBlank()) {
            onResult("未提交：缺少发起账号或收款人 wxid")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val intent = "transfer:${route.deviceUuid}:${route.weChatId}:$recipientId:${current.roomId.orEmpty()}:$amount:$note"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val selectedSession = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
                    val api = selectedSession.readApi as? ScrmPaymentApi
                        ?: error("当前 SCRM 客户端不支持发送转账")
                    val request = PaymentRequestFactory.sendRemittance(
                        route = route,
                        recipientId = recipientId,
                        roomId = current.roomId,
                        amountText = amount,
                        paymentPassword = paymentPassword,
                        memo = note
                    )
                    val key = idempotencyRegistry.keyFor(intent, paymentPassword)
                    PaymentTaskRunner(selectedSession.taskApi).submitAndAwait {
                        api.sendRemittance(request, key)
                    }.also { outcome ->
                        if (outcome.completed) idempotencyRegistry.complete(intent, paymentPassword)
                    }
                }
            }
            onResult(result.fold(
                onSuccess = { outcome -> outcome.message },
                onFailure = { error -> "转账提交失败：${error.message ?: "未知错误"}" }
            ))
        }
    }

    fun notifyClosed() {
        session = null
        UniVergeAccessibilityService.instance?.onFloatingChatTransferClosed()
    }
}
