package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.MessageAsideRequestTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStandardActionsTest {
    @Test
    fun primaryActionsFollowTheEightActionProductOrder() {
        assertEquals(
            listOf(
                MessageLongPressAction.Listen,
                MessageLongPressAction.Copy,
                MessageLongPressAction.Forward,
                MessageLongPressAction.Favorite,
                MessageLongPressAction.MultiSelect,
                MessageLongPressAction.Quote,
                MessageLongPressAction.Zoom,
                MessageLongPressAction.Delete
            ),
            messageLongPressPrimaryActions()
        )
    }

    @Test
    fun asideAnalysisRequiresEmotionStanceAndSubtext() {
        assertEquals(
            MessageAsideAnalysis(
                emotion = "克制但不耐烦",
                stance = "暂不承诺，等待对方给出条件",
                subtext = "对方愿意继续谈，但不接受当前方案"
            ),
            parseMessageAsideAnalysis(
                """
                情绪：克制但不耐烦
                立场：暂不承诺，等待对方给出条件
                话外音：对方愿意继续谈，但不接受当前方案
                """.trimIndent()
            )
        )

        assertThrows(IllegalStateException::class.java) {
            parseMessageAsideAnalysis("情绪：平静\n话外音：还在考虑")
        }
    }

    @Test
    fun zoomRoutesTextMediaAndUnsupportedMessageTypes() {
        assertEquals(MessageZoomMode.Text, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.Text))
        assertEquals(MessageZoomMode.Text, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.MixedText))
        assertEquals(MessageZoomMode.Media, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.ImageThumbnail))
        assertEquals(MessageZoomMode.Media, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.CapturedPhoto))
        assertEquals(MessageZoomMode.Unsupported, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.Voice))
    }

    @Test
    fun dismissInvalidatesAnInFlightAsideAnalysisRequest() {
        val tracker = MessageAsideRequestTracker()
        val request = tracker.begin()

        assertTrue(tracker.isActive(request))

        tracker.cancel()

        assertEquals(false, tracker.isActive(request))
    }

    @Test
    fun multiForwardModesExposeSeparateAndCombinedForwarding() {
        assertEquals(2, multiForwardModeLabels().size)
        assertTrue(multiForwardModeLabels().contains(MultiForwardMode.Combined.label))
    }
}
