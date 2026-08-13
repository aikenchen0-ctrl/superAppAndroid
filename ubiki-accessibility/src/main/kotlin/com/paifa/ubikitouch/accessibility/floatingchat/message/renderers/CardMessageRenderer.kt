package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.accessibility.floatingchat.media.FilePreviewContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.ChatHistoryMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.ContactLinkCardContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.InlineContactContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.InlineLocationContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.LinkMessageCard
import com.paifa.ubikitouch.accessibility.floatingchat.message.LocationMessageContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.MiniProgramLinkContent
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal object CardMessageRenderer : MessageRenderer {
    override val supportedTypes = setOf(
        FloatingChatMessageType.Location,
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.ChatHistory,
        FloatingChatMessageType.FilePreview,
        FloatingChatMessageType.InlineContact,
        FloatingChatMessageType.InlineLocation,
        FloatingChatMessageType.LiveLocation,
        FloatingChatMessageType.GroupInvite,
        FloatingChatMessageType.WebLink,
        FloatingChatMessageType.Article,
        FloatingChatMessageType.ChannelsLive,
        FloatingChatMessageType.Music,
        FloatingChatMessageType.Favorite
    )

    @Composable
    override fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    ) {
        val message = model.message
        when (model.type) {
            FloatingChatMessageType.FilePreview -> FilePreviewContent(message)
            FloatingChatMessageType.Location,
            FloatingChatMessageType.LiveLocation -> LocationMessageContent(message)
            FloatingChatMessageType.InlineLocation -> InlineLocationContent(message)
            FloatingChatMessageType.InlineContact -> InlineContactContent(message)
            FloatingChatMessageType.ContactLink,
            FloatingChatMessageType.GroupInvite -> ContactLinkCardContent(
                message.copy(type = FloatingChatMessageType.ContactLink)
            )
            FloatingChatMessageType.MiniProgramLink -> MiniProgramLinkContent(message, environment.claimed)
            FloatingChatMessageType.WebLink,
            FloatingChatMessageType.Article,
            FloatingChatMessageType.ChannelsLive,
            FloatingChatMessageType.Music,
            FloatingChatMessageType.Favorite -> LinkMessageCard(message)
            FloatingChatMessageType.ChatHistory -> ChatHistoryMessageContent(message)
            else -> renderLegacyMessage(model, environment)
        }
    }
}
