package com.paifa.univerge.accessibility.floatingchat.aivoice

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageKind
import com.paifa.univerge.core.model.FloatingChatMessagePresentation

/** Tracks inbound messages that existed before automatic reply was enabled or processed. */
internal class AutoReplyMessageTracker {
    private val handledMessageIds = mutableSetOf<String>()

    fun markHandled(messages: Iterable<FloatingChatMessage>) {
        messages.forEach { message -> handledMessageIds += message.id }
    }

    fun shouldGenerate(
        enabled: Boolean,
        configured: Boolean,
        message: FloatingChatMessage
    ): Boolean {
        if (!enabled || !configured || !message.isAutoReplyEligible()) return false
        return handledMessageIds.add(message.id)
    }
}

private fun FloatingChatMessage.isAutoReplyEligible(): Boolean {
    return !fromMe &&
        kind == FloatingChatMessageKind.Normal &&
        presentation != FloatingChatMessagePresentation.System
}
