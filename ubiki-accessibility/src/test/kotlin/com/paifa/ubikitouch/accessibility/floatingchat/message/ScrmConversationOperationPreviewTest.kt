package com.paifa.ubikitouch.accessibility.floatingchat.message

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrmConversationOperationPreviewTest {
    @Test
    fun clearAllPreviewRequiresTypedConfirmation() {
        val input = ScrmConversationOperationPreviewInput(
            operation = ScrmConversationPreviewOperation.ClearAll,
            deviceUuid = "device-1",
            weChatId = "wxid-1",
            conversationId = "conversation-1",
            confirmationText = "清空全部"
        )

        assertEquals("清空全部本地聊天记录请求已组装，未发送", prepareConversationOperationPreview(input))
    }

    @Test
    fun messageIdSyncRejectsWindowsLongerThanTenMinutes() {
        val input = ScrmConversationOperationPreviewInput(
            operation = ScrmConversationPreviewOperation.SyncMessageIds,
            deviceUuid = "device-1",
            weChatId = "wxid-1",
            conversationId = "conversation-1",
            startTime = 0L,
            endTime = 600_001L
        )

        assertEquals("无法组装：消息 ID 同步时间窗不能超过 10 分钟", prepareConversationOperationPreview(input))
    }
}
