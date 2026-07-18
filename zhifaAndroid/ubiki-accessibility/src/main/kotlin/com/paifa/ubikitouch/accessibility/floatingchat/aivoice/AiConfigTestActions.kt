package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import com.paifa.ubikitouch.accessibility.FloatingChatAiClient
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.isConfigured
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AiConfigTestActions(
    private val coroutineScope: CoroutineScope,
    private val aiConfigTesting: () -> Boolean,
    private val onAiConfigTestingChanged: (Boolean) -> Unit,
    private val onAiConfigStatusChanged: (String?) -> Unit
) {
    fun test(candidate: FloatingChatAiConfig) {
        if (aiConfigTesting()) return
        if (!candidate.isConfigured) {
            onAiConfigStatusChanged("请填写 API 地址、API Key 和模型")
            return
        }
        coroutineScope.launch {
            onAiConfigTestingChanged(true)
            onAiConfigStatusChanged("正在测试 AI 连接…")
            runCatching {
                withContext(Dispatchers.IO) { FloatingChatAiClient().testConfig(candidate) }
            }.onSuccess { reply ->
                onAiConfigStatusChanged("AI 连接测试成功：${reply.take(40)}")
            }.onFailure { error ->
                onAiConfigStatusChanged("AI 连接测试失败：${error.message?.take(80) ?: "未知错误"}")
            }
            onAiConfigTestingChanged(false)
        }
    }
}
