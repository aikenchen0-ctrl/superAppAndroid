package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.media.VoiceMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.CallMessageCard
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object VoiceCallMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(
        FloatingChatMessageType.Voice,
        FloatingChatMessageType.VoiceCall,
        FloatingChatMessageType.VideoCall
    )

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        when (model.type) {
            FloatingChatMessageType.Voice -> VoiceMessageContent(model.message)
            FloatingChatMessageType.VoiceCall,
            FloatingChatMessageType.VideoCall -> CallMessageCard(model.message)
            else -> renderLegacyMessage(model, environment)
        }
    }
}
