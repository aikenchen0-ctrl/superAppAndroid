package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.messageRecipientContact
import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarRole
import com.paifa.ubikitouch.accessibility.floatingchat.components.avatarRoleUsesSelectionHighlight
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageRecipientAvatarTest {
    private val account = contact("account", "Account")
    private val person = contact("person", "Person")
    private val group = contact("group", "Group")

    @Test
    fun recipientAvatarDoesNotReuseRailSelectionHighlight() {
        assertEquals(false, avatarRoleUsesSelectionHighlight(AvatarRole.Recipient))
        assertEquals(true, avatarRoleUsesSelectionHighlight(AvatarRole.Account))
    }

    @Test
    fun incomingMessageRecipientIsCurrentAccount() {
        assertEquals(
            account,
            messageRecipientContact(
                message = message(fromMe = false),
                selection = ChatThreadSelection.Private(person.id),
                selectedAccount = account,
                groups = listOf(group),
                contacts = listOf(person)
            )
        )
    }

    @Test
    fun outgoingPrivateMessageRecipientIsSelectedContact() {
        assertEquals(
            person,
            messageRecipientContact(
                message = message(fromMe = true),
                selection = ChatThreadSelection.Private(person.id),
                selectedAccount = account,
                groups = listOf(group),
                contacts = listOf(person)
            )
        )
    }

    @Test
    fun outgoingGroupMessageRecipientIsSelectedGroup() {
        assertEquals(
            group,
            messageRecipientContact(
                message = message(fromMe = true),
                selection = ChatThreadSelection.GroupChat(group.id),
                selectedAccount = account,
                groups = listOf(group),
                contacts = listOf(person)
            )
        )
    }

    @Test
    fun systemMessageHasNoRecipientAvatar() {
        assertNull(
            messageRecipientContact(
                message = message(
                    fromMe = false,
                    presentation = FloatingChatMessagePresentation.System
                ),
                selection = ChatThreadSelection.Private(person.id),
                selectedAccount = account,
                groups = listOf(group),
                contacts = listOf(person)
            )
        )
    }

    private fun message(
        fromMe: Boolean,
        presentation: FloatingChatMessagePresentation = FloatingChatMessagePresentation.Bubble
    ) = FloatingChatMessage(
        id = "message",
        type = FloatingChatMessageType.Text,
        text = "text",
        fromMe = fromMe,
        senderName = "Sender",
        time = "12:00",
        presentation = presentation
    )

    private fun contact(id: String, name: String) = FloatingChatContact(
        id = id,
        name = name,
        initials = name.take(1),
        description = name,
        avatarColor = 0xFF446688
    )
}
