package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.toLocalThreadId
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal data class MessageListViewportKey(
    val threadId: String,
    val selectedAccountId: String,
    val homeOverviewVisible: Boolean
)

internal fun messageListViewportKey(
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean
): MessageListViewportKey {
    return MessageListViewportKey(
        threadId = selection.toLocalThreadId(),
        selectedAccountId = selectedAccountId,
        homeOverviewVisible = homeOverviewVisible
    )
}

internal fun messageListInitialFirstVisibleItemIndex(
    messageCount: Int,
    homeOverviewVisible: Boolean = false
): Int {
    if (homeOverviewVisible) return 0
    return (messageCount - 1).coerceAtLeast(0)
}

internal fun shouldRetargetMessageList(
    previous: MessageListViewportKey,
    next: MessageListViewportKey
): Boolean {
    return previous != next
}

@Suppress("UNUSED_PARAMETER")
internal fun messageListReusableContentType(messageType: FloatingChatMessageType): String {
    return ReusableMessageRowContentType
}

private const val ReusableMessageRowContentType = "floating-chat-message-row"
