package com.paifa.ubikitouch.accessibility.floatingchat.message

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val ChatBubbleJson = Json { isLenient = true }
private val ChatTextKeys = listOf("content", "text", "message", "msg", "title", "description")
private val ChatContainerKeys = listOf("data", "payload", "body", "result")

// TODO(SCRM): populate senderName with the resolved nickname before mapping remote messages.

internal fun chatBubbleDisplayText(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return "消息"

    val jsonStart = listOf(trimmed.indexOf('{'), trimmed.indexOf('['))
        .filter { index -> index >= 0 }
        .minOrNull()
    if (jsonStart == null) {
        return trimmed.takeUnless(::isTechnicalChatValue) ?: "消息"
    }

    val element = runCatching {
        ChatBubbleJson.parseToJsonElement(trimmed.substring(jsonStart))
    }.getOrNull() ?: return trimmed.takeUnless(::isTechnicalChatValue) ?: "消息"

    return element.chatTextValue()
        ?.trim()
        ?.takeUnless(::isTechnicalChatValue)
        ?: "消息"
}

internal fun chatBubbleDisplaySenderName(
    fromMe: Boolean,
    rawSenderName: String,
    resolvedNickname: String?
): String {
    if (fromMe) return "我"
    resolvedNickname?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return rawSenderName.trim().takeUnless(::isTechnicalChatValue) ?: "对方"
}

internal fun shouldShowMessageSenderNickname(
    isGroupChat: Boolean,
    fromMe: Boolean,
    isSystem: Boolean
): Boolean {
    return isGroupChat && !fromMe && !isSystem
}

private fun JsonElement.chatTextValue(): String? = when (this) {
    is JsonObject -> {
        ChatTextKeys.firstNotNullOfOrNull { key -> get(key)?.chatTextPrimitive() }
            ?: ChatContainerKeys.firstNotNullOfOrNull { key -> get(key)?.chatTextValue() }
    }
    is JsonArray -> firstNotNullOfOrNull { element -> element.chatTextValue() }
    is JsonPrimitive -> contentOrNull
}

private fun JsonElement.chatTextPrimitive(): String? {
    val value = (this as? JsonPrimitive)?.contentOrNull ?: return chatTextValue()
    val trimmed = value.trim()
    if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) return trimmed
    return runCatching { ChatBubbleJson.parseToJsonElement(trimmed).chatTextValue() }.getOrNull()
}

private fun isTechnicalChatValue(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("wxid_", ignoreCase = true) ||
        trimmed.endsWith("@chatroom", ignoreCase = true) ||
        trimmed.startsWith('{') ||
        trimmed.startsWith('[')
}
