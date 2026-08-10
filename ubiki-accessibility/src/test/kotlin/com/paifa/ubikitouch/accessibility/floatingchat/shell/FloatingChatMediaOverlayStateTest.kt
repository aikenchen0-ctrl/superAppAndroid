package com.paifa.ubikitouch.accessibility.floatingchat.shell

import com.paifa.ubikitouch.accessibility.floatingchat.media.MediaActionResult
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatMediaOverlayStateTest {
    @Test(timeout = 60_000L)
    fun openCloseAndResultKeepMediaOverlayStateInOneHolder() {
        val state = FloatingChatMediaOverlayState()
        val message = mediaMessage()

        assertNull(state.actionMessage)
        assertNull(state.actionStatus)
        assertTrue(state.favoriteMediaIds.isEmpty())

        state.openActions(message)
        state.favoriteMediaIds[message.id] = true
        state.applyActionResult(MediaActionResult("saved"))

        assertSame(message, state.actionMessage)
        assertEquals("saved", state.actionStatus)
        assertTrue(state.favoriteMediaIds[message.id] == true)

        state.clearStatus()
        state.closeActions()

        assertNull(state.actionMessage)
        assertNull(state.actionStatus)
        assertFalse(state.favoriteMediaIds.isEmpty())
    }

    private fun mediaMessage(id: String = "media-state-1"): FloatingChatMessage {
        return FloatingChatMessage(
            id = id,
            type = FloatingChatMessageType.ImageThumbnail,
            text = "图片",
            fromMe = true,
            senderName = "我",
            time = "10:00",
            presentation = FloatingChatMessagePresentation.MediaStandalone,
            thumbnailUrl = "content://media/external/images/media/1"
        )
    }
}
