package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.message.NoticeMessageCard
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object SystemMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(FloatingChatMessageType.GroupNotice)

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        NoticeMessageCard(model.message)
    }
}
