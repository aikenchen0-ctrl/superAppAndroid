package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import com.paifa.ubikitouch.accessibility.FloatingChatAiClient
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.buildFloatingChatAiDraftPrompt
import com.paifa.ubikitouch.accessibility.createFloatingChatAiDraftMessage
import com.paifa.ubikitouch.accessibility.createFloatingChatAiLoadingDraftMessage
import com.paifa.ubikitouch.accessibility.floatingChatAiDraftLoadingText
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.visibleMessagesForThread
import com.paifa.ubikitouch.accessibility.isConfigured
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AiDraftGenerationActions(
    private val coroutineScope: CoroutineScope,
    private val aiConfig: () -> FloatingChatAiConfig,
    private val aiPredicting: () -> Boolean,
    private val effectiveConversation: () -> FloatingChatConversation,
    private val displayConversation: () -> FloatingChatConversation,
    private val selectedThread: () -> ChatThreadSelection,
    private val selectedAccountId: () -> String,
    private val selectedAccountName: () -> String,
    private val nextSequence: () -> Int,
    private val sentAiDraftMessageIds: MutableMap<String, Boolean>,
    private val aiDraftMessageActions: AiDraftMessageActions,
    private val onAiPredictingChanged: (Boolean) -> Unit,
    private val onAiConfigStatusChanged: (String?) -> Unit,
    private val onOpenAssistantPanel: () -> Unit,
    private val onCloseAssistantPanel: () -> Unit,
    private val onShowToast: (String?) -> Unit
) {
    fun generate(replaceMessage: FloatingChatMessage? = null) {
        if (aiPredicting()) return
        val configSnapshot = aiConfig()
        if (!configSnapshot.isConfigured) {
            onAiConfigStatusChanged("请先配置 AI API")
            onOpenAssistantPanel()
            return
        }
        val threadMessages = visibleMessagesForThread(
            conversation = displayConversation(),
            selection = selectedThread(),
            selectedAccountId = selectedAccountId()
        ).filterNot { message -> message.id == replaceMessage?.id }
        val prompt = buildFloatingChatAiDraftPrompt(
            messages = threadMessages,
            selectedAccountName = selectedAccountName()
        )
        onAiPredictingChanged(true)
        onAiConfigStatusChanged("AI 正在分析聊天并生成回复...")
        val sequence = nextSequence()
        val loadingDraft = if (replaceMessage != null) {
            editedAiDraftMessage(replaceMessage, floatingChatAiDraftLoadingText())
        } else {
            createFloatingChatAiLoadingDraftMessage(
                conversation = effectiveConversation(),
                selection = selectedThread(),
                accountId = selectedAccountId(),
                sequence = sequence
            )
        }
        sentAiDraftMessageIds.remove(loadingDraft.id)
        aiDraftMessageActions.upsertDraftMessage(loadingDraft, replaceMessage?.id)
        coroutineScope.launch {
            onAiPredictingChanged(true)
            onAiConfigStatusChanged("AI 正在生成回复...")
            runCatching {
                withContext(Dispatchers.IO) {
                    FloatingChatAiClient().generateDraft(configSnapshot, prompt)
                }
            }.onSuccess { draftText ->
                val draft = createFloatingChatAiDraftMessage(
                    conversation = effectiveConversation(),
                    selection = selectedThread(),
                    accountId = selectedAccountId(),
                    text = draftText,
                    sequence = sequence
                ).copy(id = loadingDraft.id)
                aiDraftMessageActions.upsertDraftMessage(draft, loadingDraft.id)
                onAiConfigStatusChanged("AI 回复已生成")
                onCloseAssistantPanel()
            }.onFailure { error ->
                val status = "AI 生成失败：${error.message?.take(80) ?: "未知错误"}"
                onAiConfigStatusChanged(status)
                val failedDraft = editedAiDraftMessage(
                    loadingDraft,
                    "AI生成失败，请检查配置后重试"
                )
                aiDraftMessageActions.upsertDraftMessage(failedDraft, loadingDraft.id)
                onShowToast(status)
                onOpenAssistantPanel()
            }
            onAiPredictingChanged(false)
        }
    }
}
