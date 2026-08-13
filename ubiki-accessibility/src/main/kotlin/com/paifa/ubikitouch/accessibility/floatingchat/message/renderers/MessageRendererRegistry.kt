package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import com.paifa.ubikitouch.core.model.FloatingChatMessageType

/** Routes persisted message types to their renderer family with an explicit legacy fallback. */
internal class MessageRendererRegistry(
    renderers: List<MessageRenderer>,
    private val legacyRenderer: MessageRenderer
) {
    private val renderersByType: Map<FloatingChatMessageType, MessageRenderer> = buildMap {
        renderers.forEach { renderer ->
            renderer.supportedTypes.forEach { type ->
                require(put(type, renderer) == null) {
                    "Duplicate message renderer registration for $type"
                }
            }
        }
    }

    fun resolve(type: FloatingChatMessageType): MessageRenderer {
        return renderersByType[type] ?: legacyRenderer
    }

    companion object {
        fun default(): MessageRendererRegistry {
            return MessageRendererRegistry(
                renderers = listOf(
                    SystemMessageRenderer,
                    TextMessageRenderer,
                    MediaMessageRenderer,
                    VoiceCallMessageRenderer,
                    CardMessageRenderer,
                    PaymentMessageRenderer
                ),
                legacyRenderer = LegacyMessageRenderer
            )
        }
    }
}
