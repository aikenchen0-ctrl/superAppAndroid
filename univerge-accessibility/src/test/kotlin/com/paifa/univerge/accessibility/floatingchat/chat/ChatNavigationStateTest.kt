package com.paifa.univerge.accessibility.floatingchat.chat

import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.univerge.core.model.FloatingChatPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNavigationStateTest {
    @Test(timeout = 60_000)
    fun `conversation back opens all accounts unread`() {
        val state = ChatNavigationState(
            route = ChatNavigationRoute.Conversation,
            activeAccountId = "account-a",
            selectedThread = ChatThreadSelection.Private("contact-a")
        )

        val result = state.back()

        assertEquals(
            ChatNavigationBackResult.Navigate(state.openAllAccountsUnread()),
            result
        )
    }

    @Test(timeout = 60_000)
    fun `all accounts unread opens one account directory`() {
        val next = ChatNavigationState().openAllAccountsUnread().openSingleAccountUnread("account-a")

        assertEquals(ChatNavigationRoute.SingleAccountUnread, next.route)
        assertEquals("account-a", next.activeAccountId)
        assertEquals(null, next.unreadSourceAccountId)
    }

    @Test(timeout = 60_000)
    fun `single account opens private conversation and backs through directory levels`() {
        val directory = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread("account-a")
        val conversation = directory.openConversation(ChatThreadSelection.Private("contact-a"))

        assertEquals(ChatNavigationRoute.Conversation, conversation.route)
        assertEquals("account-a", conversation.unreadSourceAccountId)
        val backToDirectory = (conversation.back() as ChatNavigationBackResult.Navigate).state
        assertEquals(ChatNavigationRoute.SingleAccountUnread, backToDirectory.route)
        assertEquals("account-a", backToDirectory.activeAccountId)
        assertEquals(null, backToDirectory.unreadSourceAccountId)
        assertEquals(
            ChatNavigationBackResult.Navigate(directory.openAllAccountsUnread()),
            directory.back()
        )
    }

    @Test(timeout = 60_000)
    fun `single account opens group conversation and backs to its directory`() {
        val directory = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread("account-b")
        val conversation = directory.openConversation(ChatThreadSelection.GroupChat("group-b"))

        assertEquals(ChatThreadSelection.GroupChat("group-b"), conversation.selectedThread)
        val backToDirectory = (conversation.back() as ChatNavigationBackResult.Navigate).state
        assertEquals(ChatNavigationRoute.SingleAccountUnread, backToDirectory.route)
        assertEquals("account-b", backToDirectory.activeAccountId)
        assertEquals(null, backToDirectory.unreadSourceAccountId)
    }

    @Test(timeout = 60_000)
    fun `switching account inside unread directory keeps the directory route`() {
        val next = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread("account-a")
            .switchUnreadAccount("account-b")

        assertEquals(ChatNavigationRoute.SingleAccountUnread, next.route)
        assertEquals("account-b", next.activeAccountId)
    }

    @Test(timeout = 60_000)
    fun `handled summary is filtered and makes current scope empty`() {
        val first = summary("account-a", "contact-a", "summary-a-v1")
        val second = summary("account-b", "contact-b", "summary-b-v1")
        val state = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread("account-a")
            .markHandled(first)

        assertEquals(emptyList<HomeUnreadThreadSummary>(), state.visibleUnreadSummaries(listOf(first, second)))
        assertTrue(state.isUnreadScopeEmpty(listOf(first, second)))
    }

    @Test(timeout = 60_000)
    fun `new summary version for handled thread becomes visible again`() {
        val handled = summary("account-a", "contact-a", "summary-a-v1")
        val newInbound = summary("account-a", "contact-a", "summary-a-v2")
        val state = ChatNavigationState().markHandled(handled)

        assertEquals(listOf(newInbound), state.visibleUnreadSummaries(listOf(handled, newInbound)))
        assertFalse(state.isUnreadScopeEmpty(listOf(handled, newInbound)))
    }

    @Test(timeout = 60_000)
    fun `controller outgoing message handles the current account thread summary version`() {
        val conversation = unreadConversation("account-a")
        val summary = homeUnreadThreadSummaries(accountScopedConversations(conversation)).single()
        val current = ChatNavigationState(
            route = ChatNavigationRoute.Conversation,
            activeAccountId = summary.accountId,
            selectedThread = summary.selection
        )

        val replied = navigationStateAfterOutgoingMessage(
            current = current,
            conversation = conversation,
            localMessages = emptyList(),
            accountId = summary.accountId,
            threadId = summary.threadId
        )

        assertEquals(setOf(summary.message.id), replied.handledSummaryMessageIds)
    }

    @Test(timeout = 60_000)
    fun `controller outgoing message initializes cold runtime navigation before handling summary`() {
        val conversation = unreadConversation("account-a")
        val summary = homeUnreadThreadSummaries(accountScopedConversations(conversation)).single()

        val replied = navigationStateAfterOutgoingMessage(
            current = ChatNavigationState(),
            conversation = conversation,
            localMessages = emptyList(),
            accountId = summary.accountId,
            threadId = summary.threadId
        )

        assertEquals(summary.accountId, replied.activeAccountId)
        assertEquals(summary.selection, replied.selectedThread)
        assertEquals(setOf(summary.message.id), replied.handledSummaryMessageIds)
    }

    @Test(timeout = 60_000)
    fun `controller outgoing message does not handle another account summary`() {
        val conversation = unreadConversation("account-a")
        val summary = homeUnreadThreadSummaries(accountScopedConversations(conversation)).single()
        val current = ChatNavigationState(
            route = ChatNavigationRoute.Conversation,
            activeAccountId = "account-b",
            selectedThread = summary.selection
        )

        val unchanged = navigationStateAfterOutgoingMessage(
            current = current,
            conversation = conversation,
            localMessages = emptyList(),
            accountId = "account-b",
            threadId = summary.threadId
        )

        assertTrue(unchanged.handledSummaryMessageIds.isEmpty())
    }

    @Test(timeout = 60_000)
    fun `controller refresh updates ordinary conversation account and thread`() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val accountId = conversation.accountContacts[1].id
        val scoped = accountScopedConversation(conversation, accountId)
        val controllerThread = ChatThreadSelection.Private(scoped.contacts.first().id)
        val current = ChatNavigationState(
            route = ChatNavigationRoute.Conversation,
            activeAccountId = conversation.accountContacts.first().id,
            selectedThread = ChatThreadSelection.Group
        )

        val synced = syncChatNavigationState(current, conversation, accountId, controllerThread)

        assertEquals(ChatNavigationRoute.Conversation, synced.route)
        assertEquals(accountId, synced.activeAccountId)
        assertEquals(controllerThread, synced.selectedThread)
        assertEquals(null, synced.unreadSourceAccountId)
    }

    @Test(timeout = 60_000)
    fun `controller refresh preserves all accounts unread directory`() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val current = ChatNavigationState(
            route = ChatNavigationRoute.AllAccountsUnread,
            activeAccountId = conversation.accountContacts.first().id,
            selectedThread = ChatThreadSelection.Group,
            handledSummaryMessageIds = setOf("summary-v1")
        )

        val synced = syncChatNavigationState(
            current,
            conversation,
            conversation.accountContacts[1].id,
            ChatThreadSelection.Private("controller-thread")
        )

        assertEquals(current, synced)
    }

    @Test(timeout = 60_000)
    fun `controller refresh preserves single account unread filter`() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val current = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread(conversation.accountContacts.first().id)

        val synced = syncChatNavigationState(
            current,
            conversation,
            conversation.accountContacts[1].id,
            ChatThreadSelection.Private("controller-thread")
        )

        assertEquals(current, synced)
    }

    @Test(timeout = 60_000)
    fun `controller refresh keeps unread source only while account and thread stay valid`() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val accountId = conversation.accountContacts.first().id
        val scoped = accountScopedConversation(conversation, accountId)
        val thread = ChatThreadSelection.Private(scoped.contacts.first().id)
        val sourced = ChatNavigationState()
            .openAllAccountsUnread()
            .openSingleAccountUnread(accountId)
            .openConversation(thread)

        val unchanged = syncChatNavigationState(sourced, conversation, accountId, thread)
        val changed = syncChatNavigationState(
            sourced,
            conversation,
            accountId,
            ChatThreadSelection.Private(scoped.contacts[1].id)
        )

        assertEquals(accountId, unchanged.unreadSourceAccountId)
        assertEquals(null, changed.unreadSourceAccountId)
    }

    @Test(timeout = 60_000)
    fun `controller update atomically synchronizes persisted navigation state`() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val sourceAccountId = conversation.accountContacts.first().id
        val sourceThread = ChatThreadSelection.Private(
            accountScopedConversation(conversation, sourceAccountId).contacts.first().id
        )
        val targetAccountId = conversation.accountContacts[1].id
        val targetThread = ChatThreadSelection.Private(
            accountScopedConversation(conversation, targetAccountId).contacts.first().id
        )
        val runtimeState = FloatingChatOverlayRuntimeState().apply {
            chatNavigationState = ChatNavigationState()
                .openAllAccountsUnread()
                .openSingleAccountUnread(sourceAccountId)
                .openConversation(sourceThread)
        }

        runtimeState.deliverConversationUpdate(
            conversation = conversation,
            selectedAccountId = targetAccountId,
            selectedThread = targetThread
        )

        assertEquals(ChatNavigationRoute.Conversation, runtimeState.chatNavigationState.route)
        assertEquals(targetAccountId, runtimeState.chatNavigationState.activeAccountId)
        assertEquals(targetThread, runtimeState.chatNavigationState.selectedThread)
        assertEquals(null, runtimeState.chatNavigationState.unreadSourceAccountId)
    }

    @Test(timeout = 60_000)
    fun `opening unread navigates and clears indicator without marking handled`() {
        val summary = summary("account-a", "contact-a", "summary-a-v1")
        var state = ChatNavigationState().openAllAccountsUnread()
        val unreadIndicators = mutableMapOf(summary.threadId to true)
        val actions = ChatNavigationActions(
            unreadThreadIds = unreadIndicators,
            state = { state },
            onStateChanged = { next -> state = next }
        )

        actions.openHomeUnread(summary)

        assertEquals(ChatNavigationRoute.Conversation, state.route)
        assertEquals(summary.selection, state.selectedThread)
        assertTrue(state.handledSummaryMessageIds.isEmpty())
        assertFalse(unreadIndicators.containsKey(summary.threadId))
    }

    private fun summary(
        accountId: String,
        contactId: String,
        messageId: String,
        group: Boolean = false
    ): HomeUnreadThreadSummary {
        val selection = if (group) {
            ChatThreadSelection.GroupChat(contactId)
        } else {
            ChatThreadSelection.Private(contactId)
        }
        return HomeUnreadThreadSummary(
            accountId = accountId,
            threadId = selection.toLocalThreadId(),
            selection = selection,
            message = FloatingChatMessage(
                id = messageId,
                type = FloatingChatMessageType.Text,
                text = "unreplied",
                fromMe = false,
                senderName = "sender",
                time = "10:00",
                connectionTargetId = contactId,
                threadContactId = contactId
            ),
            unreadCount = 1,
            avatarContact = FloatingChatContact(
                id = contactId,
                name = "Contact",
                initials = "C",
                description = "",
                avatarColor = 0L
            )
        )
    }

    private fun unreadConversation(accountId: String): FloatingChatConversation {
        val contactId = "${accountId}__contact-a"
        val contact = FloatingChatContact(
            id = contactId,
            name = "Contact",
            initials = "C",
            description = "",
            avatarColor = 0L
        )
        return FloatingChatConversation(
            peerName = contact.name,
            accountName = accountId,
            contacts = listOf(contact),
            accountContacts = listOf(
                FloatingChatContact(
                    id = accountId,
                    name = accountId,
                    initials = "A",
                    description = "",
                    avatarColor = 0L,
                    selected = true
                )
            ),
            messages = listOf(
                FloatingChatMessage(
                    id = "inbound-v1",
                    type = FloatingChatMessageType.Text,
                    text = "unreplied",
                    fromMe = false,
                    senderName = contact.name,
                    time = "10:00",
                    connectionTargetId = contact.id,
                    threadContactId = contact.id
                )
            ),
            toolActions = emptyList()
        )
    }
}
