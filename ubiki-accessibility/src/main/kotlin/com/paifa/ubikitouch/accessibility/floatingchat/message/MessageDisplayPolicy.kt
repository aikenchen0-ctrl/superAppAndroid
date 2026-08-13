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
    if (!message.hasDisplayableContent()) {
        return MessageUnavailableState.ContentUnavailable
    }
    return null
}

/**
 * Remote messages do not always put their visible value in `text`. Cards can
 * legitimately contain only structured fields such as a location title,
 * quoted text, or a file name, so those fields must participate in the
 * availability decision before the generic placeholder is shown.
 */
private fun FloatingChatMessage.hasDisplayableContent(): Boolean {
    if (listOfNotNull(
            text,
            detail,
            quoteAuthor,
            quoteText,
            cardName,
            cardSubtitle,
            appName,
            locationTitle,
            locationAddress,
            resourceUrl,
            fileName,
            fileSizeLabel,
            thumbnailUrl
        ).any { it.isNotBlank() }
    ) {
        return true
    }
    if (filePreviewLines.any(String::isNotBlank) || inlineTokens.isNotEmpty() || mediaDurationMs != null) {
        return true
    }

    // These cards have an explicit type title and useful empty-state copy.
    // They remain renderable even when the backend omits optional payload text.
    return when (type) {
        FloatingChatMessageType.Emoji,
        FloatingChatMessageType.StickerGif,
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.WebLink,
        FloatingChatMessageType.Article,
        FloatingChatMessageType.ChannelsLive,
        FloatingChatMessageType.Music,
        FloatingChatMessageType.Favorite,
        FloatingChatMessageType.RedPacket,
        FloatingChatMessageType.Transfer,
        FloatingChatMessageType.SplitBill,
        FloatingChatMessageType.Coupon,
        FloatingChatMessageType.VoiceCall,
        FloatingChatMessageType.VideoCall,
        FloatingChatMessageType.ChatHistory,
        FloatingChatMessageType.Relay,
        FloatingChatMessageType.GroupNotice -> true
        FloatingChatMessageType.Location,
        FloatingChatMessageType.LiveLocation,
        FloatingChatMessageType.InlineLocation,
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.GroupInvite,
        FloatingChatMessageType.InlineContact,
        FloatingChatMessageType.FilePreview,
        FloatingChatMessageType.Voice,
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.CapturedPhoto,
        FloatingChatMessageType.VideoPreview,
        FloatingChatMessageType.ChannelsVideo,
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote -> false
    }
}

internal fun messageDisplayGroupUsesBubbleChrome(group: MessageDisplayGroup): Boolean {
    return group == MessageDisplayGroup.Bubble
}

private fun FloatingChatMessageType.isMediaMessage(): Boolean {
    return this == FloatingChatMessageType.ImageThumbnail ||
        this == FloatingChatMessageType.CapturedPhoto ||
        this == FloatingChatMessageType.VideoPreview ||
        this == FloatingChatMessageType.ChannelsVideo
}
