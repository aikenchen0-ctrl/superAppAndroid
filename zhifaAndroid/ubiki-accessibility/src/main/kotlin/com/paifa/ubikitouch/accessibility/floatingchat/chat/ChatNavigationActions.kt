package com.paifa.ubikitouch.accessibility.floatingchat.chat

internal class ChatNavigationActions(
    private val unreadThreadIds: MutableMap<String, Boolean>,
    private val onActiveAccountIdChanged: (String) -> Unit,
    private val onSelectedThreadChanged: (ChatThreadSelection) -> Unit,
    private val onHomeOverviewVisibleChanged: (Boolean) -> Unit
) {
    fun openChatThread(thread: ChatThreadSelection) {
        accountIdForScopedThreadSelection(thread)?.let { accountId ->
            onActiveAccountIdChanged(accountId)
        }
        onSelectedThreadChanged(thread)
        onHomeOverviewVisibleChanged(false)
        unreadThreadIds.remove(thread.toLocalThreadId())
    }

    fun openHomeUnread(summary: HomeUnreadThreadSummary) {
        onActiveAccountIdChanged(summary.accountId)
        onSelectedThreadChanged(summary.selection)
        onHomeOverviewVisibleChanged(false)
        unreadThreadIds.remove(summary.threadId)
    }
}
