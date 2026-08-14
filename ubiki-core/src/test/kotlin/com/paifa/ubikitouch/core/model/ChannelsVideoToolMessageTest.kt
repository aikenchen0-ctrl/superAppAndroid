package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsVideoToolMessageTest {
    @Test
    fun channelsVideoToolCreatesTheIosEquivalentChannelVideoCard() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")

        val message = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.ChannelsVideo,
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 1
        )

        assertEquals(FloatingChatMessageType.ChannelsVideo, message.type)
        assertEquals(FloatingChatMessagePresentation.MediaStandalone, message.presentation)
        assertEquals("group-product", message.threadContactId)
        assertTrue(message.thumbnailUrl?.startsWith("https://") == true)
        assertTrue(message.resourceUrl?.endsWith(".mp4") == true)
    }
}
