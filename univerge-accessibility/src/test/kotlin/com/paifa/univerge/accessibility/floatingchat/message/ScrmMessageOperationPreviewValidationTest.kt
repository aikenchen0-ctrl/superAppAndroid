package com.paifa.univerge.accessibility.floatingchat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmMessageOperationPreviewValidationTest {
    @Test
    fun forwardRequiresTargetConversation() {
        val result = validateScrmMessageOperationPreview(
            operation = "Forward",
            messageId = 100L,
            deviceUuid = "device-1",
            weChatId = "wxid-1",
            targetConversationId = "",
            mediaId = null
        )

        assertEquals("请先填写目标会话 ID", result.error)
    }

    @Test
    fun mediaDownloadRequiresMediaId() {
        val result = validateScrmMessageOperationPreview(
            operation = "DownloadMedia",
            messageId = 100L,
            deviceUuid = "device-1",
            weChatId = "wxid-1",
            targetConversationId = "",
            mediaId = null
        )

        assertEquals("请先填写媒体资源 ID", result.error)
    }

    @Test
    fun missingRouteIsReportedBeforeOperationSpecificInput() {
        val result = validateScrmMessageOperationPreview(
            operation = "Forward",
            messageId = 100L,
            deviceUuid = "",
            weChatId = "",
            targetConversationId = "",
            mediaId = null
        )

        assertEquals("缺少当前账号 SCRM 路由", result.error)
    }

    @Test
    fun validPreviewIsReadyToAssembleAndNeverSent() {
        val result = validateScrmMessageOperationPreview(
            operation = "Revoke",
            messageId = 100L,
            deviceUuid = "device-1",
            weChatId = "wxid-1",
            targetConversationId = "",
            mediaId = null
        )

        assertTrue(result.isValid)
        assertEquals("参数已校验，可组装请求；不会发送", result.summary)
    }
}
