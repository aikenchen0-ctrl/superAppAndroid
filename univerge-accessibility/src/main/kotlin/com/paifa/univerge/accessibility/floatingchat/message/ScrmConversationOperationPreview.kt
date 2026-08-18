package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.scrm.ScrmClearAllChatMessagesRequest
import com.paifa.univerge.accessibility.scrm.ScrmConversationMessageStateRequest
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmMessageOperationRequest
import com.paifa.univerge.accessibility.scrm.ScrmSyncHistoryMessagesRequest
import com.paifa.univerge.accessibility.scrm.ScrmSyncMessageIdsRequest

internal enum class ScrmConversationPreviewOperation(val title: String) {
    SyncConversationUnread("同步当前会话未读数"),
    SyncHistory("同步会话历史"),
    SyncMessageIds("同步消息 ID"),
    SyncUnreadList("同步未读列表"),
    ClearAll("清空全部本地聊天记录")
}

internal data class ScrmConversationOperationPreviewInput(
    val operation: ScrmConversationPreviewOperation,
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val historyCount: Int = 50,
    val confirmationText: String = ""
)

@Composable
internal fun ScrmConversationOperationPreview(
    route: ScrmFloatingAccountRoute?,
    conversationId: String?
) {
    var operation by remember { mutableStateOf(ScrmConversationPreviewOperation.SyncConversationUnread) }
    var startTime by remember { mutableStateOf("0") }
    var endTime by remember { mutableStateOf("0") }
    var historyCount by remember { mutableStateOf("50") }
    var confirmationText by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val isClearAll = operation == ScrmConversationPreviewOperation.ClearAll

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("会话同步与本地记录", color = OverlayTokens.panelPrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "当前会话：${conversationId?.takeIf(String::isNotBlank) ?: "缺少会话路由"}",
            color = OverlayTokens.panelSecondaryText,
            fontSize = 10.sp
        )
        ScrmConversationPreviewOperation.values().forEach { item ->
            TextButton(
                onClick = { operation = item; confirmed = false; status = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(item.title, modifier = Modifier.weight(1f), color = if (item == operation) OverlayTokens.accent else OverlayTokens.panelPrimaryText, fontSize = 11.sp)
                Text(if (item == operation) "已选择" else "预览", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
            }
        }
        if (operation == ScrmConversationPreviewOperation.SyncHistory) {
            OutlinedTextField(historyCount, { historyCount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("历史消息数量（1-200）") }, singleLine = true)
        }
        if (operation == ScrmConversationPreviewOperation.SyncMessageIds) {
            OutlinedTextField(startTime, { startTime = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("开始时间戳（毫秒）") }, singleLine = true)
            OutlinedTextField(endTime, { endTime = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("结束时间戳（最多 10 分钟）") }, singleLine = true)
        }
        if (isClearAll) {
            Text("该操作影响当前账号微信本地的全部聊天记录，且不可撤销。", color = Color(0xFFB3261E), fontSize = 10.sp)
            OutlinedTextField(confirmationText, { confirmationText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("输入“清空全部”确认") }, singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
            Text("我已确认账号、会话和影响范围", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        }
        Button(
            onClick = {
                status = prepareConversationOperationPreview(
                    ScrmConversationOperationPreviewInput(
                        operation = operation,
                        deviceUuid = route?.deviceUuid.orEmpty(),
                        weChatId = route?.weChatId.orEmpty(),
                        conversationId = conversationId.orEmpty(),
                        startTime = startTime.toLongOrNull() ?: -1L,
                        endTime = endTime.toLongOrNull() ?: -1L,
                        historyCount = historyCount.toIntOrNull() ?: 0,
                        confirmationText = confirmationText
                    )
                )
            },
            enabled = confirmed && (!isClearAll || confirmationText == "清空全部"),
            modifier = Modifier.fillMaxWidth()
        ) { Text("生成请求预览") }
        status?.let { Text(it, color = OverlayTokens.panelSecondaryText, fontSize = 10.sp) }
    }
}

internal fun prepareConversationOperationPreview(input: ScrmConversationOperationPreviewInput): String {
    if (input.deviceUuid.isBlank() || input.weChatId.isBlank()) return "无法组装：缺少当前账号 SCRM 路由"
    return when (input.operation) {
        ScrmConversationPreviewOperation.SyncConversationUnread -> {
            if (input.conversationId.isBlank()) return "无法组装：缺少当前会话 ID"
            ScrmConversationMessageStateRequest(input.deviceUuid, input.weChatId, input.conversationId)
            "会话未读同步请求已组装，未发送"
        }
        ScrmConversationPreviewOperation.SyncHistory -> {
            if (input.conversationId.isBlank()) return "无法组装：缺少当前会话 ID"
            if (input.historyCount !in 1..200) return "无法组装：历史消息数量必须为 1-200"
            ScrmSyncHistoryMessagesRequest(input.deviceUuid, input.weChatId, input.conversationId, count = input.historyCount)
            "会话历史同步请求已组装，未发送"
        }
        ScrmConversationPreviewOperation.SyncMessageIds -> {
            if (input.startTime < 0L || input.endTime < input.startTime) return "无法组装：请输入有效的起止时间"
            if (input.endTime - input.startTime > 10 * 60 * 1_000L) return "无法组装：消息 ID 同步时间窗不能超过 10 分钟"
            ScrmSyncMessageIdsRequest(input.deviceUuid, input.weChatId, input.startTime, input.endTime)
            "消息 ID 同步请求已组装，未发送"
        }
        ScrmConversationPreviewOperation.SyncUnreadList -> {
            ScrmMessageOperationRequest(input.deviceUuid, input.weChatId)
            "未读列表同步请求已组装，未发送"
        }
        ScrmConversationPreviewOperation.ClearAll -> {
            if (input.confirmationText != "清空全部") return "无法组装：请输入“清空全部”确认"
            ScrmClearAllChatMessagesRequest(input.deviceUuid, input.weChatId, confirmClearAllChatMessages = true)
            "清空全部本地聊天记录请求已组装，未发送"
        }
    }
}
