package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.message.EmojiMessageCard
import com.paifa.ubikitouch.accessibility.floatingchat.message.MixedTextMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.QuoteMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.SimpleTextMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.StackedMessageCard
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object TextMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Emoji,
        FloatingChatMessageType.Quote,
        FloatingChatMessageType.Relay
    )

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        when (model.type) {
            FloatingChatMessageType.Text -> SimpleTextMessageContent(model.message, model.index)
            FloatingChatMessageType.MixedText -> MixedTextMessageContent(model.message)
            FloatingChatMessageType.Emoji -> EmojiMessageCard(model.message)
            FloatingChatMessageType.Quote -> QuoteMessageContent(model.message)
            FloatingChatMessageType.Relay -> StackedMessageCard(model.message)
            else -> renderLegacyMessage(model, environment)
        }
    }
}
