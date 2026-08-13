package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAvatarSwitchPolicyTest {
    @Test
    fun repeatedOrInvalidAccountClicksDoNotStartAnotherSwitch() {
        assertFalse(shouldHandleAccountAvatarClick("account-a", "account-a"))
        assertFalse(shouldHandleAccountAvatarClick("account-a", ""))
        assertTrue(shouldHandleAccountAvatarClick("account-a", "account-b"))
    }

    @Test
    fun scopedConversationIsReusedWhenSwitchingBackToAnAccount() {
        val source = FloatingChatPrototype.sampleConversation()
        val accountId = source.accountContacts.first().id
        val cache = AccountScopedConversationCache(source)

        assertSame(cache.conversationFor(accountId), cache.conversationFor(accountId))
    }

    @Test
    fun accountSwitchIndexesLargeMessageHistoryOnlyOnce() {
        val source = largeConversation(messageCount = 20_000)
        val countedMessages = CountingList(source.messages)
        val countedSource = source.copy(messages = countedMessages)
        val cache = AccountScopedConversationCache(countedSource)

        countedSource.accountContacts.forEach { account ->
            cache.conversationFor(account.id)
        }

        assertEquals(
            "Account switching read ${countedMessages.elementReads} message elements",
            countedMessages.size,
            countedMessages.elementReads
        )
    }

    @Test
    fun privateFallbackKeepsLatestSixMatchingMessagesInSourceOrder() {
        val source = fallbackConversation(
            List(8) { index -> fallbackMessage(index, targetId = "contact-a") }
        )

        val scoped = accountScopedConversation(source, activeAccountId = "account-a")

        assertEquals((2..7).map { index -> "message $index" }, scoped.messages.map { it.text })
    }

    @Test
    fun privateFallbackUsesFirstFourConnectedMessagesWhenTargetHasNoMatch() {
        val source = fallbackConversation(
            List(6) { index -> fallbackMessage(index, targetId = "unrelated-contact") }
        )

        val scoped = accountScopedConversation(source, activeAccountId = "account-a")

        assertEquals((0..3).map { index -> "message $index" }, scoped.messages.map { it.text })
    }

    private fun largeConversation(messageCount: Int): FloatingChatConversation {
        val accounts = listOf(contact("account-a"), contact("account-b"))
        val contacts = List(10) { index -> contact("contact-$index") }
        val groups = List(4) { index -> contact("group-$index") }
        val threadIds = (contacts + groups).map { contact -> contact.id }
        val messages = List(messageCount) { index ->
            val threadId = threadIds[index % threadIds.size]
            FloatingChatMessage(
                id = "message-$index",
                type = FloatingChatMessageType.Text,
                text = "message $index",
                fromMe = index % 2 == 0,
                senderName = "sender",
                time = "10:00",
                connectionTarget = if (index % 2 == 0) {
                    FloatingChatConnectionTarget.Account
                } else {
                    FloatingChatConnectionTarget.User
                },
                connectionTargetId = if (index % 2 == 0) accounts[index % accounts.size].id else threadId,
                threadContactId = threadId
            )
        }
        return FloatingChatConversation(
            peerName = "Large history",
            accountName = accounts.first().name,
            contacts = contacts,
            accountContacts = accounts,
            messages = messages,
            toolActions = emptyList(),
            groupContacts = groups
        )
    }

    private fun contact(id: String): FloatingChatContact = FloatingChatContact(
        id = id,
        name = id,
        initials = id.take(2),
        description = id,
        avatarColor = 0xFF607D8BL
    )

    private fun fallbackConversation(messages: List<FloatingChatMessage>): FloatingChatConversation {
        val account = contact("account-a")
        return FloatingChatConversation(
            peerName = "Fallback",
            accountName = account.name,
            contacts = listOf(contact("contact-a")),
            accountContacts = listOf(account),
            messages = messages,
            toolActions = emptyList()
        )
    }

    private fun fallbackMessage(index: Int, targetId: String): FloatingChatMessage {
        return FloatingChatMessage(
            id = "fallback-$index",
            type = FloatingChatMessageType.Text,
            text = "message $index",
            fromMe = false,
            senderName = "sender",
            time = "10:00",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = targetId,
            threadContactId = null
        )
    }

    private class CountingList<T>(private val values: List<T>) : AbstractList<T>() {
        var elementReads: Int = 0
            private set

        override val size: Int
            get() = values.size

        override fun get(index: Int): T {
            elementReads += 1
            return values[index]
        }
    }
}
