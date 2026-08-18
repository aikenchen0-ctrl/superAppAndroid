package com.paifa.univerge.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScrmAvatarPaletteTest {
    @Test
    fun avatarPaletteHasEightDistinctColorsAndThenRepeats() {
        val firstCycle = (0 until 8).map(::scrmAvatarColorForPosition)

        assertEquals(8, firstCycle.distinct().size)
        assertEquals(firstCycle.first(), scrmAvatarColorForPosition(8))
    }

    @Test
    fun contactsUseSequentialFallbackColorsWhenStableIdsHaveMatchingHashes() {
        val conversation = scrmFloatingChatConversation(
            base = com.paifa.univerge.core.model.FloatingChatPrototype.sampleConversation(),
            contacts = listOf(
                ScrmContact(id = 1, wxid = "Aa", nickname = "First"),
                ScrmContact(id = 2, wxid = "BB", nickname = "Second")
            ),
            accounts = emptyList(),
            devices = emptyList(),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid-account"
        )

        assertEquals(2, conversation.contacts.size)
        assertNotEquals(conversation.contacts[0].avatarColor, conversation.contacts[1].avatarColor)
    }

    @Test
    fun contactAvatarColorDoesNotChangeWhenContactOrderChanges() {
        val first = listOf(
            ScrmContact(id = 11, wxid = "wx-first", nickname = "First"),
            ScrmContact(id = 12, wxid = "wx-second", nickname = "Second")
        )
        val forward = scrmFloatingChatConversation(
            base = com.paifa.univerge.core.model.FloatingChatPrototype.sampleConversation(),
            contacts = first,
            accounts = emptyList(),
            devices = emptyList(),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid-account"
        ).contacts.associateBy { it.id }
        val reversed = scrmFloatingChatConversation(
            base = com.paifa.univerge.core.model.FloatingChatPrototype.sampleConversation(),
            contacts = first.reversed(),
            accounts = emptyList(),
            devices = emptyList(),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid-account"
        ).contacts.associateBy { it.id }

        assertEquals(forward.mapValues { it.value.avatarColor }, reversed.mapValues { it.value.avatarColor })
    }
}
