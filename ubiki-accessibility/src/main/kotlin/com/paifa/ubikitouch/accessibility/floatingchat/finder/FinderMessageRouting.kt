package com.paifa.ubikitouch.accessibility.floatingchat.finder

import java.net.URI
import java.net.URLDecoder
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

/** Only explicit video-account message types may open the Finder workspace. */
internal fun isFinderMessage(message: FloatingChatMessage): Boolean {
    return message.type == FloatingChatMessageType.ChannelsVideo ||
        message.type == FloatingChatMessageType.ChannelsLive
}

/**
 * Keeps Finder navigation tied to structured metadata. Display text is intentionally ignored so
 * an arbitrary title cannot be treated as an account identifier.
 */
internal fun finderUserNameForMessage(message: FloatingChatMessage): String? {
    if (!isFinderMessage(message)) return null
    return message.finderUserName?.trim()?.takeIf(String::isNotEmpty)
        ?: finderUserNameFromHttpUrl(message.resourceUrl)
}

private fun finderUserNameFromHttpUrl(resourceUrl: String?): String? {
    val uri = runCatching { URI(resourceUrl?.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return uri.rawQuery.orEmpty()
        .split('&')
        .firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=').decodeQueryPart()
            if (key != "sphUserName") return@firstNotNullOfOrNull null
            pair.substringAfter('=', missingDelimiterValue = "")
                .decodeQueryPart()
                .trim()
                .takeIf(String::isNotEmpty)
        }
}

private fun String.decodeQueryPart(): String {
    return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
}
