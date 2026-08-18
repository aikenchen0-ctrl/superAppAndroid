package com.paifa.univerge.accessibility.floatingchat.message.renderers

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType

/** Immutable projection passed to renderers without changing the message domain model. */
internal data class MessageRenderModel(
    val id: String,
    val type: FloatingChatMessageType,
    val message: FloatingChatMessage,
    val index: Int
) {
    companion object {
        fun from(message: FloatingChatMessage, index: Int): MessageRenderModel {
            return MessageRenderModel(
                id = message.id,
                type = message.type,
                message = message,
                index = index
            )
        }
    }
}
