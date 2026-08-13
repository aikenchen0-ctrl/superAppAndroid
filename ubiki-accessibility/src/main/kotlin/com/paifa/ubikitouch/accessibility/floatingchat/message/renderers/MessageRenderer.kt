package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import androidx.compose.runtime.Composable
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

/** A focused renderer family for one or more immutable message types. */
internal interface MessageRenderer {
    val supportedTypes: Set<FloatingChatMessageType>

    @Composable
    fun render(
        model: MessageRenderModel,
        state: MessageRenderState,
        environment: MessageRenderEnvironment
    )
}
