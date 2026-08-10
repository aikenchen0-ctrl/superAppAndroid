package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaActionHandlerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test(timeout = 60_000L)
    fun favoriteActionTogglesStateAndNotifiesConsumer() {
        val message = mediaMessage()
        val favoriteMediaIds = mutableMapOf<String, Boolean>()
        val changes = mutableListOf<Pair<String, Boolean>>()

        val first = performMediaAction(
            context = context,
            message = message,
            action = MediaActionContract.Favorite,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { error("Favorite must not open the action sheet") },
            onFavoriteChanged = { changedMessage, favorite ->
                changes += changedMessage.id to favorite
            }
        )
        val second = performMediaAction(
            context = context,
            message = message,
            action = MediaActionContract.Favorite,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { error("Favorite must not open the action sheet") },
            onFavoriteChanged = { changedMessage, favorite ->
                changes += changedMessage.id to favorite
            }
        )

        assertTrue(first.toast)
        assertTrue(favoriteMediaIds[message.id] == false)
        assertTrue(second.toast)
        assertEquals(listOf(message.id to true, message.id to false), changes)
    }

    @Test(timeout = 60_000L)
    fun moreActionOpensActionSheetWithoutToast() {
        val favoriteMediaIds = mutableMapOf<String, Boolean>()
        var openCount = 0

        val result = performMediaAction(
            context = context,
            message = mediaMessage(),
            action = MediaActionContract.More,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { openCount += 1 }
        )

        assertEquals(1, openCount)
        assertFalse(result.toast)
        assertTrue(favoriteMediaIds.isEmpty())
    }

    private fun mediaMessage(id: String = "media-1"): FloatingChatMessage {
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
