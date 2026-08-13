package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import com.paifa.ubikitouch.accessibility.FloatingChatAiClient
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.buildFloatingChatMessageAsidePrompt
import com.paifa.ubikitouch.accessibility.floatingChatMessageAsideConfig
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.visibleMessagesForThread
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageAsideAnalysis
import com.paifa.ubikitouch.accessibility.floatingchat.message.parseMessageAsideAnalysis
import com.paifa.ubikitouch.accessibility.isConfigured
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

internal class MessageAsideRequestTracker {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun cancel() {
        generation.incrementAndGet()
    }

    fun isActive(request: Long): Boolean = generation.get() == request
}

internal sealed interface MessageAsideAnalysisState {
    val message: FloatingChatMessage

    data class Loading(override val message: FloatingChatMessage) : MessageAsideAnalysisState
    data class Ready(
        override val message: FloatingChatMessage,
        val analysis: MessageAsideAnalysis
    ) : MessageAsideAnalysisState
    data class Failed(
        override val message: FloatingChatMessage,
        val reason: String
    ) : MessageAsideAnalysisState
}

internal class MessageAsideAnalysisActions(
    private val coroutineScope: CoroutineScope,
    private val aiConfig: () -> FloatingChatAiConfig,
    private val displayConversation: () -> FloatingChatConversation,
    private val selectedThread: () -> ChatThreadSelection,
    private val selectedAccountId: () -> String,
    private val selectedAccountName: () -> String,
    private val onStateChanged: (MessageAsideAnalysisState?) -> Unit,
    private val onOpenAssistantPanel: () -> Unit
) {
    private val requestTracker = MessageAsideRequestTracker()

    fun analyze(message: FloatingChatMessage) {
        val request = requestTracker.begin()
        val configSnapshot = aiConfig()
        if (!configSnapshot.isConfigured) {
            onStateChanged(
                MessageAsideAnalysisState.Failed(message, "请先配置 AI API 后重试")
            )
            onOpenAssistantPanel()
            return
        }
        val threadMessages = visibleMessagesForThread(
            conversation = displayConversation(),
            selection = selectedThread(),
            selectedAccountId = selectedAccountId()
        )
        val prompt = buildFloatingChatMessageAsidePrompt(
            messages = threadMessages,
            targetMessage = message,
            selectedAccountName = selectedAccountName()
        )
        onStateChanged(MessageAsideAnalysisState.Loading(message))
        coroutineScope.launch {
            runCatching {
                val response = withContext(Dispatchers.IO) {
                    FloatingChatAiClient().generateDraft(
                        floatingChatMessageAsideConfig(configSnapshot),
                        prompt
                    )
                }
                parseMessageAsideAnalysis(response)
            }.onSuccess { analysis ->
                if (requestTracker.isActive(request)) {
                    onStateChanged(MessageAsideAnalysisState.Ready(message, analysis))
                }
            }.onFailure { error ->
                val reason = error.message?.take(120) ?: "未知错误"
                if (requestTracker.isActive(request)) {
                    onStateChanged(MessageAsideAnalysisState.Failed(message, "AI 分析失败：$reason"))
                }
            }
        }
    }

    fun dismiss() {
        requestTracker.cancel()
        onStateChanged(null)
    }
}
