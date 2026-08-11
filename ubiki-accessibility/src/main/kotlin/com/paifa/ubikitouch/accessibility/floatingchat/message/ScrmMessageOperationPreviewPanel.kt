package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingDialogCloseButton
import com.paifa.ubikitouch.accessibility.scrm.ScrmForwardMessageRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessageDetailPullRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessageMediaDownloadRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessageOperationRequest
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountRouteForContactId
import com.paifa.ubikitouch.core.model.FloatingChatMessage

private enum class ScrmMessagePreviewOperation(val title: String) {
    Forward("转发"),
    Revoke("撤回"),
    Transcribe("语音转文字"),
    PullDetail("拉取消息详情"),
    DownloadMedia("下载媒体"),
    PullOriginal("补拉原文"),
    PullEmojiDetail("收藏表情详情")
}

/** Accessibility overlay cannot open a platform Dialog window with an application token. */
internal fun messageOperationPreviewUsesInTreeOverlay(): Boolean = true

/**
 * UI 对接：消息长按 -> 更多。
 * 人工测试：选择任意操作，核对目标消息 ID 和账号，点击“组装请求”后应只显示待人工发送状态。
 * 禁止在此组件中调用 messageOperationApi 的写任务方法。
 */
@Composable
internal fun ScrmMessageOperationPreviewPanel(
    message: FloatingChatMessage,
    selectedAccountId: String,
    onDismiss: () -> Unit
) {
    val route = remember(selectedAccountId) { scrmFloatingAccountRouteForContactId(selectedAccountId) }
    val messageId = remember(message.remoteMessageServerId) { message.remoteMessageServerId?.toLongOrNull() }
    var operation by remember(message.id) { mutableStateOf<ScrmMessagePreviewOperation?>(null) }
    var targetConversationId by remember(message.id) { mutableStateOf("") }
    var mediaIdText by remember(message.id) { mutableStateOf("") }
    var includeOriginal by remember(message.id) { mutableStateOf(false) }
    var status by remember(message.id) { mutableStateOf<String?>(null) }
    var confirmed by remember(message.id) { mutableStateOf(false) }
    val previewValidation = operation?.let {
        validateScrmMessageOperationPreview(
            operation = it.name,
            messageId = messageId,
            deviceUuid = route?.deviceUuid,
            weChatId = route?.weChatId,
            targetConversationId = targetConversationId,
            mediaId = mediaIdText.toIntOrNull()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFDFDFE),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(operation?.title ?: "消息操作", modifier = Modifier.weight(1f))
                FloatingDialogCloseButton(onClose = onDismiss)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("仅 UI 预览", color = Color(0xFF4E7A55), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("请求只会在后续接口接入后发送", color = Color(0xFF6B766E), fontSize = 10.sp)
                }
                Text("目标消息：${messageId?.toString() ?: "缺少远端消息 ID"}")
                Text("当前账号：${route?.weChatId ?: "缺少 SCRM 路由"}")
                if (operation == null) {
                    MessageOperationSection("消息处理", listOf(ScrmMessagePreviewOperation.Forward, ScrmMessagePreviewOperation.Revoke, ScrmMessagePreviewOperation.PullDetail, ScrmMessagePreviewOperation.PullOriginal)) { operation = it }
                    MessageOperationSection("内容与媒体", listOf(ScrmMessagePreviewOperation.Transcribe, ScrmMessagePreviewOperation.DownloadMedia, ScrmMessagePreviewOperation.PullEmojiDetail)) { operation = it }
                } else {
                    if (operation == ScrmMessagePreviewOperation.Forward) {
                        OutlinedTextField(
                            value = targetConversationId,
                            onValueChange = { targetConversationId = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("目标会话 ID") },
                            singleLine = true
                        )
                    }
                    if (operation == ScrmMessagePreviewOperation.DownloadMedia) {
                        OutlinedTextField(
                            value = mediaIdText,
                            onValueChange = { mediaIdText = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("媒体资源 ID") },
                            supportingText = { Text("媒体 ID 与扩展 ID 二选一，当前先填写媒体 ID") },
                            singleLine = true
                        )
                    }
                    if (operation == ScrmMessagePreviewOperation.PullDetail) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeOriginal, onCheckedChange = { includeOriginal = it })
                            Text("同时补拉原文", fontSize = 11.sp, color = Color(0xFF5F666D))
                        }
                    }
                    previewValidation?.let { validation ->
                        Text(
                            text = validation.error ?: validation.summary,
                            color = if (validation.isValid) Color(0xFF4E7A55) else Color(0xFFB3261E),
                            fontSize = 11.sp
                        )
                    }
                    Text("该操作会产生服务端任务。本页面仅组装参数，不会发送。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("我已确认目标消息和账号", fontSize = 11.sp, color = Color(0xFF5F666D))
                    }
                    status?.let { Text(it) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    if (operation == null) onDismiss() else {
                        operation = null
                        confirmed = false
                    }
                }) { Text(if (operation == null) "取消" else "返回") }
                if (operation == null) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                } else {
                    Button(enabled = confirmed, onClick = {
                        status = prepareMessageOperationRequest(
                            operation = operation ?: return@Button,
                            messageId = messageId,
                            routeDeviceUuid = route?.deviceUuid,
                    routeWechatId = route?.weChatId,
                    targetConversationId = targetConversationId,
                    mediaId = mediaIdText.toIntOrNull(),
                    includeOriginal = includeOriginal
                        )
                    }) { Text("组装请求") }
                }
            }
            }
        }
    }
}

@Composable
private fun MessageOperationSection(
    title: String,
    operations: List<ScrmMessagePreviewOperation>,
    onSelect: (ScrmMessagePreviewOperation) -> Unit
) {
    Text(title, color = Color(0xFF858C94), fontSize = 11.sp)
    operations.forEach { item ->
        TextButton(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
            Text(item.title, modifier = Modifier.weight(1f), color = Color(0xFF30343A))
            Text("预览", color = Color(0xFFB26A00), fontSize = 10.sp)
        }
    }
}

private fun prepareMessageOperationRequest(
    operation: ScrmMessagePreviewOperation,
    messageId: Long?,
    routeDeviceUuid: String?,
    routeWechatId: String?,
    targetConversationId: String,
    mediaId: Int?,
    includeOriginal: Boolean
): String {
    val validation = validateScrmMessageOperationPreview(
        operation = operation.name,
        messageId = messageId,
        deviceUuid = routeDeviceUuid,
        weChatId = routeWechatId,
        targetConversationId = targetConversationId,
        mediaId = mediaId
    )
    validation.error?.let { return "无法组装：$it" }
    requireNotNull(messageId)
    requireNotNull(routeDeviceUuid)
    requireNotNull(routeWechatId)
    val baseRequest = ScrmMessageOperationRequest(routeDeviceUuid, routeWechatId)
    return when (operation) {
        ScrmMessagePreviewOperation.Forward -> {
            if (targetConversationId.isBlank()) return "无法组装：请输入目标会话 ID"
            // Manual test: confirm target conversation in UI, then a human may call forwardMessage.
            ScrmForwardMessageRequest(routeDeviceUuid, routeWechatId, targetConversationId)
            "转发请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.Revoke -> {
            // Manual test: verify message ID and account, then a human may call revokeMessage.
            baseRequest
            "撤回请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.Transcribe -> {
            // Manual test: use a real voice message only; a human may call transcribeVoiceMessage.
            baseRequest
            "语音转文字请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.PullDetail -> {
            // Manual test: a human may call pullMessageDetail and inspect taskId/result.
            ScrmMessageDetailPullRequest(routeDeviceUuid, routeWechatId, getOriginal = includeOriginal)
            "消息详情请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.DownloadMedia -> {
            // Manual test: provide a media or extension ID before calling downloadMessageMedia.
            ScrmMessageMediaDownloadRequest(routeDeviceUuid, routeWechatId, mediaId = mediaId)
            "媒体下载请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.PullOriginal -> {
            // Manual test: a human may call pullMessageOriginal after validating target message ID.
            baseRequest
            "补拉原文请求已组装，未发送"
        }
        ScrmMessagePreviewOperation.PullEmojiDetail -> {
            // Manual test: a human may call pullEmojiDetail for a known emoji message ID.
            baseRequest
            "表情详情请求已组装，未发送"
        }
    }
}
