package com.paifa.univerge.accessibility.floatingchat.media

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType

internal fun mediaMimeType(message: FloatingChatMessage): String =
    if (message.type == FloatingChatMessageType.VideoPreview) "video/*" else "image/*"
