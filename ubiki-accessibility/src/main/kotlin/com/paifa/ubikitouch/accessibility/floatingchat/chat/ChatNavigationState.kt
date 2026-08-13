package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal enum class ChatNavigationRoute {
    AllAccountsUnread,
    SingleAccountUnread,
    Conversation
}

internal sealed interface ChatNavigationBackResult {
    data class Navigate(val state: ChatNavigationState) : ChatNavigationBackResult
    data object Collapse : ChatNavigationBackResult
}

internal data class ChatNavigationState(
    val route: ChatNavigationRoute = ChatNavigationRoute.Conversation,
    val activeAccountId: String = "",
    val selectedThread: ChatThreadSelection = ChatThreadSelection.Group,
    val unreadSourceAccountId: String? = null,
    val handledSummaryMessageIds: Set<String> = emptySet()
) {
    fun openAllAccountsUnread(): ChatNavigationState = copy(
        route = ChatNavigationRoute.AllAccountsUnread,
        unreadSourceAccountId = null
    )

    fun openSingleAccountUnread(accountId: String): ChatNavigationState = copy(
        route = ChatNavigationRoute.SingleAccountUnread,
        activeAccountId = accountId,
        unreadSourceAccountId = null
    )

    fun switchUnreadAccount(accountId: String): ChatNavigationState = copy(
        route = ChatNavigationRoute.SingleAccountUnread,
        activeAccountId = accountId,
        unreadSourceAccountId = null
    )

    fun openConversation(thread: ChatThreadSelection): ChatNavigationState = copy(
        route = ChatNavigationRoute.Conversation,
        selectedThread = thread,
        unreadSourceAccountId = activeAccountId.takeIf {
            route == ChatNavigationRoute.SingleAccountUnread
        }
    )

    fun openUnreadConversation(summary: HomeUnreadThreadSummary): ChatNavigationState = copy(
        route = ChatNavigationRoute.Conversation,
        activeAccountId = summary.accountId,
        selectedThread = summary.selection,
        unreadSourceAccountId = summary.accountId.takeIf {
            route == ChatNavigationRoute.SingleAccountUnread
        }
    )

    fun switchConversationAccount(
        accountId: String,
        thread: ChatThreadSelection
    ): ChatNavigationState = copy(
        route = ChatNavigationRoute.Conversation,
        activeAccountId = accountId,
        selectedThread = thread,
        unreadSourceAccountId = null
    )

    fun back(): ChatNavigationBackResult = when (route) {
        ChatNavigationRoute.Conversation -> ChatNavigationBackResult.Navigate(
            unreadSourceAccountId?.let { sourceAccountId ->
                copy(
                    route = ChatNavigationRoute.SingleAccountUnread,
                    activeAccountId = sourceAccountId,
                    unreadSourceAccountId = null
                )
            } ?: openAllAccountsUnread()
        )
        ChatNavigationRoute.SingleAccountUnread -> ChatNavigationBackResult.Navigate(openAllAccountsUnread())
        ChatNavigationRoute.AllAccountsUnread -> ChatNavigationBackResult.Collapse
    }

    fun markHandled(summary: HomeUnreadThreadSummary): ChatNavigationState = copy(
        handledSummaryMessageIds = handledSummaryMessageIds + summary.message.id
    )

    fun markReplied(summary: HomeUnreadThreadSummary): ChatNavigationState = markHandled(summary)

    fun visibleUnreadSummaries(
        summaries: List<HomeUnreadThreadSummary>
    ): List<HomeUnreadThreadSummary> {
        return summaries.filter { summary ->
            summary.message.id !in handledSummaryMessageIds &&
                (route != ChatNavigationRoute.SingleAccountUnread || summary.accountId == activeAccountId)
        }
    }

    fun isUnreadScopeEmpty(summaries: List<HomeUnreadThreadSummary>): Boolean {
        return visibleUnreadSummaries(summaries).isEmpty()
    }

}

internal fun syncChatNavigationState(
    current: ChatNavigationState,
    conversation: FloatingChatConversation,
    controllerAccountId: String,
    controllerThread: ChatThreadSelection
): ChatNavigationState {
    if (current.route != ChatNavigationRoute.Conversation) return current

    val accountId = controllerAccountId.takeIf { candidate ->
        conversation.accountContacts.any { account -> account.id == candidate }
    } ?: current.activeAccountId.takeIf { candidate ->
        conversation.accountContacts.any { account -> account.id == candidate }
    } ?: conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: ""
    val selectedThread = initialChatThreadSelection(
        conversation = accountScopedConversation(conversation, accountId),
        preferredSelection = controllerThread
    )
    val sourceStillValid = current.unreadSourceAccountId != null &&
        current.activeAccountId == accountId &&
        current.selectedThread == selectedThread &&
        controllerThread == current.selectedThread
    return current.copy(
        activeAccountId = accountId,
        selectedThread = selectedThread,
        unreadSourceAccountId = current.unreadSourceAccountId.takeIf { sourceStillValid }
    )
}

internal fun navigationStateAfterOutgoingMessage(
    current: ChatNavigationState,
    conversation: FloatingChatConversation,
    localMessages: List<FloatingChatMessage>,
    accountId: String,
    threadId: String
): ChatNavigationState {
    val displayConversation = conversation.copy(messages = conversation.messages + localMessages)
    val summary = homeUnreadThreadSummaries(accountScopedConversations(displayConversation))
        .firstOrNull { candidate ->
            candidate.accountId == accountId && candidate.threadId == threadId
        }
        ?: return current
    val contextUnchanged = current.activeAccountId == accountId && current.selectedThread == summary.selection
    return current.copy(
        activeAccountId = accountId,
        selectedThread = summary.selection,
        unreadSourceAccountId = current.unreadSourceAccountId.takeIf { contextUnchanged }
    ).markReplied(summary)
}
