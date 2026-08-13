package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig

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
    var candidate by remember(config) { mutableStateOf(config) }
    var temperatureText by remember(config) { mutableStateOf(config.temperature.toString()) }
    var maxTokensText by remember(config) { mutableStateOf(config.maxTokens.toString()) }
    val busy = testing || predicting

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AI自动回复")
        Text("后续收到的新消息会生成回复草稿，需由用户确认发送。")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("启用自动回复")
            Switch(
                checked = candidate.autoReplyEnabled,
                onCheckedChange = { candidate = candidate.copy(autoReplyEnabled = it) },
                enabled = !busy
            )
        }
        Text("回复机器人：Codex（OpenAI 格式）")
        OutlinedTextField(
            value = candidate.baseUrl,
            onValueChange = { candidate = candidate.copy(baseUrl = it) },
            label = { Text("访问地址，例如 https://cc2.cx/v1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy
        )
        OutlinedTextField(
            value = candidate.apiKey,
            onValueChange = { candidate = candidate.copy(apiKey = it) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = candidate.model,
            onValueChange = { candidate = candidate.copy(model = it) },
            label = { Text("模型，例如 gpt-5.5") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy
        )
        OutlinedTextField(
            value = candidate.systemPrompt,
            onValueChange = { candidate = candidate.copy(systemPrompt = it) },
            label = { Text("自动回复提示词") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = !busy
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = temperatureText,
                onValueChange = { value ->
                    temperatureText = value
                    value.toFloatOrNull()?.let { candidate = candidate.copy(temperature = it) }
                },
                label = { Text("温度") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { value ->
                    maxTokensText = value.filter(Char::isDigit)
                    maxTokensText.toIntOrNull()?.let { candidate = candidate.copy(maxTokens = it) }
                },
                label = { Text("最大 Token") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text(if (candidate.autoReplyEnabled) "状态：保存后开启，等待新消息。" else "状态：未开启。")
        status?.takeIf(String::isNotBlank)?.let { Text(it) }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onTest(candidate) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                if (testing) CircularProgressIndicator() else Text("测试连接")
            }
            Button(
                onClick = { onSave(candidate) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("保存配置") }
            OutlinedButton(
                onClick = onClose,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("关闭") }
        }
    }
}
