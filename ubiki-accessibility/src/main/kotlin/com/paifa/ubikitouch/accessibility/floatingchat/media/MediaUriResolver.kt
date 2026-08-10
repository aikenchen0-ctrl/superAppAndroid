package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.net.Uri
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal fun mediaActionUri(message: FloatingChatMessage): Uri? =
    (message.resourceUrl ?: message.thumbnailUrl)?.let(Uri::parse)

internal fun playableVideoUriTextForMessage(message: FloatingChatMessage): String? {
    if (message.type != FloatingChatMessageType.VideoPreview) return null
    return message.resourceUrl?.takeIf { it.isNotBlank() && isPlayableVideoUri(it) }
}

internal fun playableVideoUriForMessage(message: FloatingChatMessage): Uri? =
    playableVideoUriTextForMessage(message)?.let(Uri::parse)

private fun isPlayableVideoUri(uriText: String): Boolean =
    uriText.startsWith("content://") || uriText.startsWith("file://") || uriText.startsWith("/")
