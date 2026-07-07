package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatThreadSelectionTest {
    @Test
    fun groupSelectionShowsSharedConversationMessagesFromMembers() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val selectedAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Group
        )

        val messages = visibleMessagesForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Group,
            selectedAccountId = selectedAccount.id
        )

        val userTargets = messages
            .filter { it.connectionTarget == FloatingChatConnectionTarget.User }
            .mapNotNull { it.connectionTargetId }
            .toSet()

        assertTrue(messages.isNotEmpty())
        assertTrue(messages.all { it.threadContactId == null })
        assertTrue(userTargets.contains("li-si"))
        assertTrue(userTargets.contains("xiao-chen"))
    }

    @Test
    fun namedGroupSelectionShowsOnlyThatGroupMessages() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val selection = ChatThreadSelection.GroupChat("group-ops")
        val selectedAccount = selectedAccountForThread(
            conversation = conversation,
            selection = selection
        )

        val messages = visibleMessagesForThread(
            conversation = conversation,
            selection = selection,
            selectedAccountId = selectedAccount.id
        )

        assertTrue(messages.isNotEmpty())
        assertTrue(messages.all { it.threadContactId == "group-ops" })
        assertTrue(messages.any {
            it.connectionTarget == FloatingChatConnectionTarget.User &&
                it.connectionTargetId == "sun-lin"
        })
        assertTrue(messages.none { it.threadContactId == "group-ai" })
    }

    @Test
    fun privateSelectionShowsOnlySelectedContactThread() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val selection = ChatThreadSelection.Private("li-si")
        val selectedAccount = selectedAccountForThread(
            conversation = conversation,
            selection = selection
        )

        val messages = visibleMessagesForThread(
            conversation = conversation,
            selection = selection,
            selectedAccountId = selectedAccount.id
        )

        assertTrue(messages.isNotEmpty())
        assertTrue(messages.all { it.threadContactId == "li-si" })
        assertTrue(messages.any {
            it.connectionTarget == FloatingChatConnectionTarget.User &&
                it.connectionTargetId == "li-si"
        })
        assertTrue(messages.any {
            it.connectionTarget == FloatingChatConnectionTarget.Account &&
                it.connectionTargetId == selectedAccount.id
        })
    }

    @Test
    fun contactRemarkUpdatesVisibleContactNameAndMessageSenderName() {
        val conversation = accountScopedConversation(
            conversation = FloatingChatPrototype.sampleConversation(),
            activeAccountId = "account-main"
        )
        val contact = conversation.contacts.first()
        val profiledConversation = applyContactProfilesToConversation(
            conversation = conversation,
            profiles = listOf(
                LocalContactProfile(
                    accountId = "account-main",
                    contactId = contact.id,
                    remark = "老沈",
                    updatedAt = 1L
                )
            )
        )

        val messages = visibleMessagesForThread(
            conversation = profiledConversation,
            selection = ChatThreadSelection.Private(contact.id),
            selectedAccountId = "account-main"
        )

        assertEquals("老沈", profiledConversation.contacts.first { it.id == contact.id }.name)
        assertTrue(messages.any { message ->
            message.connectionTarget == FloatingChatConnectionTarget.User &&
                message.connectionTargetId == contact.id &&
            message.senderName == "老沈"
        })
    }

    @Test
    fun forwardTargetConversationUsesContactRemarkNames() {
        val conversation = accountScopedConversation(
            conversation = FloatingChatPrototype.sampleConversation(),
            activeAccountId = "account-main"
        )
        val contact = conversation.contacts.first()
        val forwardConversation = forwardTargetConversationFor(
            conversation = conversation,
            profiles = listOf(
                LocalContactProfile(
                    accountId = "account-main",
                    contactId = contact.id,
                    remark = "转发目标名",
                    updatedAt = 1L
                )
            )
        )

        assertEquals("转发目标名", forwardConversation.contacts.first { it.id == contact.id }.name)
    }

    @Test
    fun defaultSelectionStartsOnGroupThread() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(ChatThreadSelection.GroupChat("group-product"), defaultChatThreadSelection(conversation))
    }

    @Test
    fun missingPreferredGroupFallsBackToDefaultGroup() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(
            ChatThreadSelection.GroupChat("group-product"),
            initialChatThreadSelection(
                conversation = conversation,
                preferredSelection = ChatThreadSelection.GroupChat("missing-group")
            )
        )
    }
}
