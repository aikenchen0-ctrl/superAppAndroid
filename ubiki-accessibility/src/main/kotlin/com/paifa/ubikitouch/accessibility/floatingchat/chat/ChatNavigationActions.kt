package com.paifa.ubikitouch.accessibility.floatingchat.chat

internal class ChatNavigationActions(
    private val unreadThreadIds: MutableMap<String, Boolean>,
    private val state: () -> ChatNavigationState,
    private val onStateChanged: (ChatNavigationState) -> Unit
) {
    fun openAllAccountsUnread() {
        onStateChanged(state().openAllAccountsUnread())
    }

    fun openSingleAccountUnread(accountId: String) {
        onStateChanged(state().openSingleAccountUnread(accountId))
    }

    fun openChatThread(thread: ChatThreadSelection) {
        val current = state()
        val accountId = accountIdForScopedThreadSelection(thread) ?: current.activeAccountId
        onStateChanged(
            current.copy(activeAccountId = accountId).openConversation(thread)
        )
        unreadThreadIds.remove(thread.toLocalThreadId())
    }

    fun openHomeUnread(summary: HomeUnreadThreadSummary) {
        onStateChanged(state().openUnreadConversation(summary))
        unreadThreadIds.remove(summary.threadId)
    }

    fun switchUnreadAccount(accountId: String) {
        onStateChanged(state().switchUnreadAccount(accountId))
    }

    fun switchConversationAccount(accountId: String, thread: ChatThreadSelection) {
        onStateChanged(state().switchConversationAccount(accountId, thread))
    }

    fun markHandled(summary: HomeUnreadThreadSummary) {
        onStateChanged(state().markHandled(summary))
    }

    fun markReplied(summary: HomeUnreadThreadSummary) {
        onStateChanged(state().markReplied(summary))
    }

    fun back(): ChatNavigationBackResult = state().back().also { result ->
        if (result is ChatNavigationBackResult.Navigate) {
            onStateChanged(result.state)
        }
    }
}
