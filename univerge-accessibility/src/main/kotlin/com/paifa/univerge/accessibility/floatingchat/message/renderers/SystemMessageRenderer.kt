package com.paifa.univerge.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.univerge.accessibility.floatingchat.message.NoticeMessageCard
import com.paifa.univerge.core.model.FloatingChatMessageType

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
