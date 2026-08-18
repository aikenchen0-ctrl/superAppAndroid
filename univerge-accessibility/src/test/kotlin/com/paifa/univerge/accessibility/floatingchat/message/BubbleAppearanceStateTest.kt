package com.paifa.univerge.accessibility.floatingchat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessagePresentation
import com.paifa.univerge.core.model.FloatingChatMessageType

class BubbleAppearanceStateTest {
    @Test
    fun toggleAndButtonLabelStayInSync() {
        assertEquals("2D气泡", bubbleAppearanceButtonLabel(BubbleAppearance.TwoD))
        assertEquals(BubbleAppearance.ThreeD, BubbleAppearance.TwoD.toggle())
        assertEquals("3D气泡", bubbleAppearanceButtonLabel(BubbleAppearance.ThreeD))
        assertEquals(BubbleAppearance.TwoD, BubbleAppearance.ThreeD.toggle())
    }

    @Test
    fun threeDimensionalAppearanceOnlyAppliesToOrdinaryMessageBubbles() {
        val bubble = FloatingChatMessage(
            id = "bubble",
            type = FloatingChatMessageType.Text,
            text = "message",
            fromMe = false,
            senderName = "sender",
            time = "10:00"
        )

        assertTrue(messageUsesThreeDimensionalBubble(bubble, BubbleAppearance.ThreeD))
        assertFalse(messageUsesThreeDimensionalBubble(bubble, BubbleAppearance.TwoD))
        assertFalse(
            messageUsesThreeDimensionalBubble(
                bubble.copy(presentation = FloatingChatMessagePresentation.System),
                BubbleAppearance.ThreeD
            )
        )
        assertFalse(
            messageUsesThreeDimensionalBubble(
                bubble.copy(presentation = FloatingChatMessagePresentation.MediaStandalone),
                BubbleAppearance.ThreeD
            )
        )
    }
}
