package com.paifa.ubikitouch.accessibility.floatingchat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStandardActionsTest {
    @Test
    fun primaryActionsIncludeListenZoomAndStandardCommands() {
        val actions = messageLongPressPrimaryActions()

        assertTrue(actions.contains(MessageLongPressAction.Listen))
        assertTrue(actions.contains(MessageLongPressAction.Zoom))
        assertTrue(actions.contains(MessageLongPressAction.Copy))
        assertTrue(actions.contains(MessageLongPressAction.Forward))
        assertTrue(actions.contains(MessageLongPressAction.Favorite))
        assertTrue(actions.contains(MessageLongPressAction.MultiSelect))
        assertTrue(actions.contains(MessageLongPressAction.Quote))
        assertTrue(actions.contains(MessageLongPressAction.Delete))
    }

    @Test
    fun multiForwardModesExposeSeparateAndCombinedForwarding() {
        assertEquals(2, multiForwardModeLabels().size)
        assertTrue(multiForwardModeLabels().contains(MultiForwardMode.Combined.label))
    }
}
