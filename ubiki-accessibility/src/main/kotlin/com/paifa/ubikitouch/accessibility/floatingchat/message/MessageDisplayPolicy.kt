package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal enum class MessageDisplayGroup {
    Bubble,
    Card,
    Media,
    System,
    Unavailable
}

internal enum class MessageUnavailableState {
    ContentUnavailable,
    AccessPending,
    MediaExpired
}

internal fun messageDisplayGroupFor(message: FloatingChatMessage): MessageDisplayGroup {
    if (message.presentation == FloatingChatMessagePresentation.System) {
        return MessageDisplayGroup.System
    }
    if (messageUnavailableStateFor(message) != null) {
        return MessageDisplayGroup.Unavailable
    }
    return when (message.type) {
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote -> MessageDisplayGroup.Bubble
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.VideoPreview -> MessageDisplayGroup.Media
        else -> MessageDisplayGroup.Card
    }
}

internal fun messageUnavailableStateFor(message: FloatingChatMessage): MessageUnavailableState? {
    if (message.accessState == FloatingChatAccessState.NeedsApply ||
        message.accessState == FloatingChatAccessState.Applied
    ) {
        return MessageUnavailableState.AccessPending
    }
    if (message.type.isMediaMessage() &&
        message.thumbnailUrl.isNullOrBlank() &&
        message.resourceUrl.isNullOrBlank()
    ) {
        return MessageUnavailableState.MediaExpired
    }
    if (message.text.isBlank() &&
        message.detail.isNullOrBlank() &&
        message.resourceUrl.isNullOrBlank()
    ) {
        return MessageUnavailableState.ContentUnavailable
    }
    return null
}

internal fun messageDisplayGroupUsesBubbleChrome(group: MessageDisplayGroup): Boolean {
    return group == MessageDisplayGroup.Bubble
}

private fun FloatingChatMessageType.isMediaMessage(): Boolean {
    return this == FloatingChatMessageType.ImageThumbnail ||
        this == FloatingChatMessageType.VideoPreview
}
