package com.paifa.ubikitouch.accessibility.floatingchat.media

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal fun mediaMimeType(message: FloatingChatMessage): String =
    if (message.type == FloatingChatMessageType.VideoPreview) "video/*" else "image/*"
