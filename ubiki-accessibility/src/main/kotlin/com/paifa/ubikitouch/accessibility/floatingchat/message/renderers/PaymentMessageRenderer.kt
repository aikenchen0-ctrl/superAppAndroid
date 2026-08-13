package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.message.MoneyMessageCard
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object PaymentMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(
        FloatingChatMessageType.RedPacket,
        FloatingChatMessageType.Transfer,
        FloatingChatMessageType.SplitBill,
        FloatingChatMessageType.Coupon
    )

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        MoneyMessageCard(model.message)
    }
}
