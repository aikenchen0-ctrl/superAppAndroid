package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmMessageComposerRequestFactoryTest {

    private val route = ScrmFloatingAccountRoute(
        deviceUuid = "device-1",
        weChatId = "wxid_account"
    )

    @Test
    fun buildsEmojiSendRequestFromTheCurrentConversation() {
        val request = buildScrmEmojiSendRequest(route, "wxid_friend", "emoji-md5")

        assertEquals("device-1", request.deviceUuid)
        assertEquals("wxid_friend", request.conversationId)
        assertEquals("emoji-md5", request.md5)
    }

    @Test
    fun buildsFilterBatchPreviewWithAConservativeDefaultLimit() {
        val request = buildScrmBatchTextPreview(
            route = route,
            content = "hello",
            labelNames = listOf("new-customer")
        )

        assertEquals("text", request.messageType)
        assertEquals(1, request.maxCount)
        assertTrue(request.filterLabelNames.contains("new-customer"))
    }
}
