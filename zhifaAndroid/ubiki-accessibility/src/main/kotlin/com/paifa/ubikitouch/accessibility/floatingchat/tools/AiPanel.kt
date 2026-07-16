package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.floatingchat.contract.AiUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.AiUiState

@Composable
internal fun AiPanel(
    state: AiUiState,
    onEvent: (AiUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var baseUrl by remember(state) { mutableStateOf(state.baseUrl) }
    var apiKey by remember(state) { mutableStateOf(state.apiKey) }
    var model by remember(state) { mutableStateOf(state.model) }
    var prompt by remember(state) { mutableStateOf(state.systemPrompt) }
    var temperature by remember(state) { mutableStateOf(state.temperature) }
    var maxTokens by remember(state) { mutableStateOf(state.maxTokens) }
    Column(modifier = modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextLabel("AI 配置", 13.sp, maxLines = 1)
        AiField("API 地址", baseUrl) { baseUrl = it; onEvent(AiUiEvent.BaseUrlChanged(it)) }
        AiField("API Key", apiKey) { apiKey = it; onEvent(AiUiEvent.ApiKeyChanged(it)) }
        AiField("模型", model) { model = it; onEvent(AiUiEvent.ModelChanged(it)) }
        AiField("系统提示词", prompt) { prompt = it; onEvent(AiUiEvent.PromptChanged(it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiField("温度", temperature, Modifier.weight(1f)) { temperature = it; onEvent(AiUiEvent.TemperatureChanged(it)) }
            AiField("最大 Token", maxTokens, Modifier.weight(1f)) { maxTokens = it; onEvent(AiUiEvent.MaxTokensChanged(it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("测试", modifier = Modifier.padding(8.dp).clickable { onEvent(AiUiEvent.TestRequested) })
            Text("保存", modifier = Modifier.padding(8.dp).clickable { onEvent(AiUiEvent.SaveRequested) })
            Text("关闭", modifier = Modifier.padding(8.dp).clickable { onEvent(AiUiEvent.CloseRequested) })
        }
    }
}

@Composable
internal fun AiConfigPanel(
    config: FloatingChatAiConfig,
    status: String?,
    predicting: Boolean,
    testing: Boolean,
    onSave: (FloatingChatAiConfig) -> Unit,
    onTest: (FloatingChatAiConfig) -> Unit,
    onClose: () -> Unit
) {
    val state = AiUiState(
        baseUrl = config.baseUrl,
        apiKey = config.apiKey,
        model = config.model,
        systemPrompt = config.systemPrompt,
        temperature = config.temperature.toString(),
        maxTokens = config.maxTokens.toString(),
        testing = testing,
        error = status
    )
    AiPanel(state = state, onEvent = { event ->
        when (event) {
            AiUiEvent.SaveRequested -> onSave(config)
            AiUiEvent.TestRequested -> onTest(config)
            AiUiEvent.CloseRequested -> onClose()
            else -> Unit
        }
    })
}

@Composable
private fun AiField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = true)
}
