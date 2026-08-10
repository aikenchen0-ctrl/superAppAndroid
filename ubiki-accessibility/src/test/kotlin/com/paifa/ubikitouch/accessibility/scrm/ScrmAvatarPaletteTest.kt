package com.paifa.ubikitouch.accessibility.scrm

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
            base = com.paifa.ubikitouch.core.model.FloatingChatPrototype.sampleConversation(),
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
}
