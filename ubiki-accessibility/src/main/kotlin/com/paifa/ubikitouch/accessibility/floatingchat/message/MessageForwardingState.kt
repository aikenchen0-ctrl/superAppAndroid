package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.accessibility.scrm.scrmMessageOperationType
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState

internal fun FloatingChatMessage.longPressCopyText(): String {
    return listOfNotNull(
        text.ifBlank { null },
        detail,
        quoteText,
        cardName,
        appName,
        locationTitle,
        locationAddress,
        fileName,
        resourceUrl,
        thumbnailUrl
    ).joinToString(" ").ifBlank {
        when (type) {
            FloatingChatMessageType.ImageThumbnail -> "[图片]"
            FloatingChatMessageType.VideoPreview -> "[视频]"
            FloatingChatMessageType.Voice -> "[语音]"
            else -> "[消息]"
        }
    }
}

internal fun outgoingTextMessageWithOptionalQuote(
    baseMessage: FloatingChatMessage,
    quotedMessage: FloatingChatMessage?
): FloatingChatMessage {
    if (quotedMessage == null) return baseMessage
    return baseMessage.copy(
        type = FloatingChatMessageType.Quote,
        quoteAuthor = quotedMessage.senderName.ifBlank { "引用" },
        quoteText = quotedMessage.longPressCopyText(),
        remoteMessageServerId = quotedMessage.remoteMessageServerId
    )
}

internal fun preparedForwardedMessageForSend(
    source: FloatingChatMessage,
    conversation: FloatingChatConversation,
    target: ChatThreadSelection,
    accountId: String,
    sequence: Int,
    prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage
): FloatingChatMessage {
    val threadId = target.toLocalThreadId()
    val baseMessage = source.forwardedCopyFor(
        conversation = conversation,
        target = target,
        accountId = accountId,
        sequence = sequence
    )
    if (scrmMessageOperationType(baseMessage) == null) {
        return baseMessage.copy(
            sendState = FloatingChatSendState.FailedFinal,
            sendErrorCode = "UnsupportedForwardMessageType",
            sendErrorMessage = "当前消息类型暂不支持真实转发"
        )
    }
    return prepareOutgoingMessage(baseMessage, threadId)
}

private fun FloatingChatMessage.forwardedCopyFor(
    conversation: FloatingChatConversation,
    target: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    val targetPrefix = when (target) {
        ChatThreadSelection.Group -> "group"
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    val threadContactId = when (target) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    return copy(
        id = "local-forward-$targetPrefix-$accountId-$sequence",
        fromMe = true,
        senderName = conversation.accountContacts.firstOrNull { account -> account.id == accountId }?.name
            ?: conversation.accountName,
        time = "刚刚",
        connectionTarget = FloatingChatConnectionTarget.Account,
        connectionTargetId = accountId,
        threadContactId = threadContactId,
        remoteMessageServerId = null,
        remoteTaskId = null,
        sendState = FloatingChatSendState.LocalOnly,
        sendErrorCode = null,
        sendErrorMessage = null,
        clientRequestId = null
    )
}

internal fun combinedForwardChatHistoryMessage(
    messages: List<FloatingChatMessage>,
    conversation: FloatingChatConversation,
    target: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    val accountName = conversation.accountContacts
        .firstOrNull { account -> account.id == accountId }
        ?.name
        ?: conversation.accountName.ifBlank { "我" }
    val threadContactId = when (target) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    val targetPrefix = when (target) {
        ChatThreadSelection.Group -> "group"
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    val previewLines = messages.take(CombinedForwardPreviewMaxLines).map { message ->
        val author = message.senderName.ifBlank {
            if (message.fromMe) accountName else "对方"
        }
        "$author：${message.longPressCopyText()}"
    }
    return FloatingChatMessage(
        id = "local-chat-history-$targetPrefix-$accountId-$sequence",
        type = FloatingChatMessageType.ChatHistory,
        text = chatHistoryTitleForTarget(conversation, target),
        fromMe = true,
        senderName = accountName,
        time = "刚刚",
        connectionTarget = FloatingChatConnectionTarget.Account,
        connectionTargetId = accountId,
        threadContactId = threadContactId,
        detail = "共 ${messages.size} 条聊天记录",
        filePreviewLines = previewLines,
        sendState = FloatingChatSendState.LocalOnly
    )
}

internal fun combinedForwardChatHistoryOpensDetailPage(): Boolean = true

private fun chatHistoryTitleForTarget(
    conversation: FloatingChatConversation,
    target: ChatThreadSelection
): String {
    val targetName = when (target) {
        ChatThreadSelection.Group -> conversation.peerName.ifBlank { "群聊" }
        is ChatThreadSelection.GroupChat -> conversation.groupContacts
            .firstOrNull { group -> group.id == target.groupId }
            ?.name
            ?: conversation.peerName.ifBlank { "群聊" }
        is ChatThreadSelection.Private -> conversation.contacts
            .firstOrNull { contact -> contact.id == target.contactId }
            ?.name
            ?: conversation.peerName.ifBlank { "联系人" }
    }
    return "${targetName}的聊天记录"
}

private const val CombinedForwardPreviewMaxLines = 20
