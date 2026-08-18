package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
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

    /** 测试流程：在小程序卡片页填写真实链接后生成请求，确认接口 payload 不会遗漏 url。 */
    @Test
    fun buildsWeAppCardRequestWithTheRequiredUrl() {
        val request = buildScrmWeAppCardRequest(
            route = route,
            conversationId = "wxid_friend",
            appId = "wx1234567890abcdee",
            title = "服务首页",
            pagePath = "pages/home/index",
            url = "https://service.example.cn/mini/home",
            thumb = "https://cdn.example.cn/mini-thumb.png"
        )

        assertEquals("https://service.example.cn/mini/home", request.url)
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
