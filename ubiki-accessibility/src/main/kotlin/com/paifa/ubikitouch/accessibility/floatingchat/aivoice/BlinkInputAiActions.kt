package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import com.paifa.ubikitouch.accessibility.FloatingChatAiClient
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkInputAiAction
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkInputStatusPhase
import com.paifa.ubikitouch.accessibility.buildFloatingChatAiDraftPrompt
import com.paifa.ubikitouch.accessibility.buildFloatingChatAiInputPolishPrompt
import com.paifa.ubikitouch.accessibility.blinkVoiceInputStatusMessageFor
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.visibleMessagesForThread
import com.paifa.ubikitouch.accessibility.isConfigured
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BlinkInputAiActions(
    private val coroutineScope: CoroutineScope,
    private val aiConfig: () -> FloatingChatAiConfig,
    private val blinkInputAiBusy: () -> Boolean,
    private val aiPredicting: () -> Boolean,
    private val inputText: () -> String,
    private val displayConversation: () -> FloatingChatConversation,
    private val selectedThread: () -> ChatThreadSelection,
    private val selectedAccountId: () -> String,
    private val selectedAccountName: () -> String,
    private val onAiConfigStatusChanged: (String?) -> Unit,
    private val onBlinkInputBusyChanged: (Boolean) -> Unit,
    private val onInputTextChanged: (String) -> Unit,
    private val onBlinkGeneratedInputClearableChanged: (Boolean) -> Unit,
    private val onShowBlinkInputStatus: (String, Boolean) -> Unit,
    private val onOpenAssistantPanel: () -> Unit,
    private val onShowToast: (String?) -> Unit
) {
    fun run(action: FloatingChatBlinkInputAiAction, eventType: String) {
        if (action == FloatingChatBlinkInputAiAction.None || blinkInputAiBusy() || aiPredicting()) return
        val configSnapshot = aiConfig()
        if (!configSnapshot.isConfigured) {
            onAiConfigStatusChanged("\u8bf7\u5148\u914d\u7f6e AI API")
            onShowBlinkInputStatus("\u8bf7\u5148\u914d\u7f6e AI API", true)
            onOpenAssistantPanel()
            onShowToast("\u8bf7\u5148\u914d\u7f6e AI API")
            return
        }
        val originalInput = inputText().trim()
        val prompt = when (action) {
            FloatingChatBlinkInputAiAction.PredictReply -> {
                val threadMessages = visibleMessagesForThread(
                    conversation = displayConversation(),
                    selection = selectedThread(),
                    selectedAccountId = selectedAccountId()
                )
                buildFloatingChatAiDraftPrompt(
                    messages = threadMessages,
                    selectedAccountName = selectedAccountName()
                )
            }
            FloatingChatBlinkInputAiAction.PolishInput -> {
                buildFloatingChatAiInputPolishPrompt(originalInput)
            }
            FloatingChatBlinkInputAiAction.None -> return
        }
        coroutineScope.launch {
            onBlinkInputBusyChanged(true)
            onAiConfigStatusChanged(
                when (action) {
                    FloatingChatBlinkInputAiAction.PredictReply -> "\u0041\u0049\u9884\u6d4b\u56de\u590d\u751f\u6210\u4e2d..."
                    FloatingChatBlinkInputAiAction.PolishInput -> "\u0041\u0049\u6b63\u5728\u6da6\u8272\u8f93\u5165\u5185\u5bb9..."
                    FloatingChatBlinkInputAiAction.None -> null
                }
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    FloatingChatAiClient().generateDraft(configSnapshot, prompt)
                }
            }.onSuccess { generatedText ->
                val nextText = generatedText.trim()
                when (action) {
                    FloatingChatBlinkInputAiAction.PredictReply -> {
                        if (inputText().isBlank()) {
                            onInputTextChanged(nextText)
                            onBlinkGeneratedInputClearableChanged(true)
                            onAiConfigStatusChanged("\u0041\u0049\u9884\u6d4b\u56de\u590d\u5df2\u751f\u6210")
                            onShowBlinkInputStatus(
                                blinkVoiceInputStatusMessageFor(
                                    eventType = eventType,
                                    action = action,
                                    phase = FloatingChatBlinkInputStatusPhase.Completed
                                ),
                                true
                            )
                        } else {
                            onShowBlinkInputStatus("\u8f93\u5165\u6846\u5df2\u6709\u5185\u5bb9\uff0c\u672a\u8986\u76d6\u9884\u6d4b\u56de\u590d", true)
                        }
                    }
                    FloatingChatBlinkInputAiAction.PolishInput -> {
                        if (inputText().trim() == originalInput) {
                            onInputTextChanged(nextText)
                            onBlinkGeneratedInputClearableChanged(true)
                            onAiConfigStatusChanged("\u0041\u0049\u6da6\u8272\u5df2\u5b8c\u6210")
                            onShowBlinkInputStatus(
                                blinkVoiceInputStatusMessageFor(
                                    eventType = eventType,
                                    action = action,
                                    phase = FloatingChatBlinkInputStatusPhase.Completed
                                ),
                                true
                            )
                        } else {
                            onShowBlinkInputStatus("\u8f93\u5165\u5185\u5bb9\u5df2\u53d8\u5316\uff0c\u672a\u8986\u76d6\u6da6\u8272\u7ed3\u679c", true)
                        }
                    }
                    FloatingChatBlinkInputAiAction.None -> Unit
                }
            }.onFailure { error ->
                val status = "\u0041\u0049\u5904\u7406\u5931\u8d25\uff1a${error.message?.take(80) ?: "\u672a\u77e5\u9519\u8bef"}"
                onAiConfigStatusChanged(status)
                onShowBlinkInputStatus(
                    blinkVoiceInputStatusMessageFor(
                        eventType = eventType,
                        action = action,
                        phase = FloatingChatBlinkInputStatusPhase.Failed
                    ),
                    true
                )
                onShowToast(status)
            }
            onBlinkInputBusyChanged(false)
        }
    }
}
