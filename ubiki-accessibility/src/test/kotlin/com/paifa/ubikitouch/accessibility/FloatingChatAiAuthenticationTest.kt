package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingChatAiAuthenticationTest {
    @Test(timeout = 60_000)
    fun `authentication failures prompt the user to update the API key`() {
        assertEquals(
            "AI API Key 无效或已失效，请更新后重试",
            floatingChatAiFailureMessage(statusCode = 401, detail = "bad token")
        )
    }

    @Test(timeout = 60_000)
    fun `permission failures are not presented as retryable network errors`() {
        assertEquals(
            "当前 API Key 没有使用 AI 服务的权限",
            floatingChatAiFailureMessage(statusCode = 403, detail = "forbidden")
        )
    }
}
