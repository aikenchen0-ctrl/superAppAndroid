package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeUnreadDerivationPerformanceTest {
    @Test
    fun homeOverviewGroupingDoesNotCopyTheAccumulatedListForEveryMessage() {
        val source = sourceFile("floatingchat/chat/ChatThreadState.kt").readText()

        assertFalse(source.contains("existing.messages + message"))
    }

    @Test
    fun homeOverviewGroupingPreservesFirstGroupOrderAndMessageOrder() {
        val messages = listOf(
            message("a-main-1", targetId = "a"),
            message("b-main-1", targetId = "b"),
            message("a-main-2", targetId = "a"),
            message("a-work-1", targetId = "a"),
            message("none-main-1", targetId = null)
        )
        val accountIds = mapOf(
            "a-main-1" to "main",
            "b-main-1" to "main",
            "a-main-2" to "main",
            "a-work-1" to "work",
            "none-main-1" to "main"
        )

        val groups = homeOverviewMessageGroups(messages, accountIds)

        assertEquals(
            listOf(
                listOf("a-main-1", "a-main-2"),
                listOf("b-main-1"),
                listOf("a-work-1"),
                listOf("none-main-1")
            ),
            groups.map { group -> group.messages.map(FloatingChatMessage::id) }
        )
        assertEquals(listOf("a-main-1", "b-main-1", "a-work-1", "none-main-1"), groups.map { it.key })
        assertEquals(
            listOf(
                "home-group:main:a:a-main-1",
                "home-group:main:b:b-main-1",
                "home-group:work:a:a-work-1",
                "home-group:main:null:none-main-1"
            ),
            groups.map { it.connectorId }
        )
    }

    @Test
    fun homeUnreadSummariesReadLargeSourceHistoryAtMostTwice() {
        val threadCount = 200
        val messagesPerThread = 100
        val contacts = List(threadCount) { index -> contact("contact-$index") }
        val sourceMessages = List(threadCount * messagesPerThread) { index ->
            val contact = contacts[index % contacts.size]
            message(
                id = "message-$index",
                targetId = contact.id,
                threadId = contact.id
            )
        }
        val countedMessages = CountingList(sourceMessages)
        val conversation = conversation(
            contacts = contacts,
            messages = countedMessages
        )

        val summaries = homeUnreadThreadSummaries(conversation)

        assertEquals(threadCount, summaries.size)
        assertTrue(
            "Unread summary derivation read ${countedMessages.elementReads} elements for ${countedMessages.size} messages",
            countedMessages.elementReads <= countedMessages.size * 2
        )
    }

    @Test
    fun homeUnreadSummariesPreserveExplicitPrivatePriorityAndNullThreadFallbackOrder() {
        val alice = contact("alice", name = "Alice")
        val bob = contact("bob", name = "Bob")
        val account = contact("account-a", name = "Account A", selected = true)
        val otherAccount = contact("account-b", name = "Account B")
        val conversation = conversation(
            contacts = listOf(alice, bob),
            accounts = listOf(account, otherAccount),
            messages = listOf(
                message("alice-old", targetId = alice.id),
                message(
                    id = "alice-account-reply",
                    targetId = account.id,
                    fromMe = true,
                    target = FloatingChatConnectionTarget.Account
                ),
                message(
                    id = "alice-neutral-after-reply",
                    targetId = null,
                    target = FloatingChatConnectionTarget.None
                ),
                message("alice-new-1", targetId = alice.id),
                message(
                    id = "wrong-account-reply",
                    targetId = otherAccount.id,
                    fromMe = true,
                    target = FloatingChatConnectionTarget.Account
                ),
                message("alice-new-2", targetId = alice.id),
                message("bob-fallback", targetId = bob.id),
                message("bob-direct-old", targetId = bob.id, threadId = bob.id),
                message(
                    id = "bob-direct-reply",
                    targetId = account.id,
                    threadId = bob.id,
                    fromMe = true,
                    target = FloatingChatConnectionTarget.Account
                ),
                message("bob-direct-new", targetId = bob.id, threadId = bob.id)
            )
        )

        val summaries = homeUnreadThreadSummaries(conversation).associateBy { it.selection }
        val aliceSummary = summaries.getValue(ChatThreadSelection.Private(alice.id))
        val bobSummary = summaries.getValue(ChatThreadSelection.Private(bob.id))

        assertEquals(2, aliceSummary.unreadCount)
        assertTrue(aliceSummary.message.id.endsWith("-alice-new-2"))
        assertEquals(alice, aliceSummary.avatarContact)
        assertEquals("Alice - Account A", aliceSummary.message.senderName)
        assertEquals(1, bobSummary.unreadCount)
        assertTrue(bobSummary.message.id.endsWith("-bob-direct-new"))
        assertEquals(bob, bobSummary.avatarContact)
        assertEquals("Bob - Account A", bobSummary.message.senderName)
    }

    @Test
    fun homeUnreadSummariesPreserveDefaultGroupNullThreadMessagesInSourceOrder() {
        val member = contact("member", name = "Member")
        val account = contact("account-a", name = "Account A", selected = true)
        val defaultGroup = contact("group-default", name = "Default Group", selected = true)
        val secondaryGroup = contact("group-secondary", name = "Secondary Group")
        val conversation = conversation(
            contacts = listOf(member),
            accounts = listOf(account),
            groups = listOf(defaultGroup, secondaryGroup),
            messages = listOf(
                message("default-null-old", targetId = member.id),
                message(
                    id = "default-direct-reply",
                    targetId = account.id,
                    threadId = defaultGroup.id,
                    fromMe = true,
                    target = FloatingChatConnectionTarget.Account
                ),
                message("default-null-new", targetId = member.id),
                message("secondary-direct", targetId = member.id, threadId = secondaryGroup.id),
                message("default-direct-new", targetId = member.id, threadId = defaultGroup.id)
            )
        )

        val summaries = homeUnreadThreadSummaries(conversation).associateBy { it.selection }
        val defaultSummary = summaries.getValue(ChatThreadSelection.GroupChat(defaultGroup.id))
        val secondarySummary = summaries.getValue(ChatThreadSelection.GroupChat(secondaryGroup.id))

        assertEquals(2, defaultSummary.unreadCount)
        assertTrue(defaultSummary.message.id.endsWith("-default-direct-new"))
        assertEquals(member, defaultSummary.avatarContact)
        assertEquals("Default Group · Member - Account A", defaultSummary.message.senderName)
        assertEquals(1, secondarySummary.unreadCount)
        assertTrue(secondarySummary.message.id.endsWith("-secondary-direct"))
        assertEquals("Secondary Group · Member - Account A", secondarySummary.message.senderName)
    }

    private fun conversation(
        contacts: List<FloatingChatContact>,
        messages: List<FloatingChatMessage>,
        accounts: List<FloatingChatContact> = listOf(contact("account-a", name = "Account A", selected = true)),
        groups: List<FloatingChatContact> = emptyList()
    ): FloatingChatConversation = FloatingChatConversation(
        peerName = "Unread test",
        accountName = accounts.first().name,
        contacts = contacts,
        accountContacts = accounts,
        messages = messages,
        toolActions = emptyList(),
        groupContacts = groups
    )

    private fun message(
        id: String,
        targetId: String?,
        threadId: String? = null,
        fromMe: Boolean = false,
        target: FloatingChatConnectionTarget = if (fromMe) {
            FloatingChatConnectionTarget.Account
        } else {
            FloatingChatConnectionTarget.User
        }
    ): FloatingChatMessage = FloatingChatMessage(
        id = id,
        type = FloatingChatMessageType.Text,
        text = id,
        fromMe = fromMe,
        senderName = id,
        time = "10:00",
        connectionTarget = target,
        connectionTargetId = targetId,
        threadContactId = threadId
    )

    private fun contact(
        id: String,
        name: String = id,
        selected: Boolean = false
    ): FloatingChatContact = FloatingChatContact(
        id = id,
        name = name,
        initials = name.take(2),
        description = id,
        avatarColor = 0xFF607D8B,
        selected = selected
    )

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
        if (moduleRelative.isFile) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
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
