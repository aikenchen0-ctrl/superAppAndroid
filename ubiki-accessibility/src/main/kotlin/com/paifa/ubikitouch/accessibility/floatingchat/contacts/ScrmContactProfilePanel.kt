package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingDialogCloseButton
import com.paifa.ubikitouch.accessibility.scrm.ScrmBatchSetContactLabelsByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactDetail
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile
import com.paifa.ubikitouch.accessibility.scrm.ScrmSaveCustomerProfileRequest

/** UI 对接：GET 联系人详情；写操作仅组装 request 并由人工确认，不调用网络。 */
@Composable
internal fun ScrmContactProfilePanel(
    contact: ScrmContact,
    detail: ScrmContactDetail?,
    customerProfile: ScrmCustomerProfile?,
    loading: Boolean,
    status: String?,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenChat: () -> Unit,
    onWritePreview: (String) -> Unit
) {
    var showProfileEditor by remember(contact.id) { mutableStateOf(false) }
    var showBatchLabels by remember(contact.id) { mutableStateOf(false) }
    val profile = detail?.customerProfile ?: customerProfile
    var draft by remember(profile) {
        mutableStateOf(
            ProfileDraft(
                level = profile?.customerLevel.orEmpty(),
                source = profile?.sourceChannel.orEmpty(),
                notes = profile?.notes.orEmpty()
            )
        )
    }
    val labels = detail?.labels.orEmpty().filterNot { it.isDeleted }

    Column(Modifier.fillMaxWidth().background(Color(0xFFF2F3F5))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回联系人")
            }
            Text(contact.displayName, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新客户画像")
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(18.dp)) {
                    Text(contact.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(contact.wxid ?: contact.friendNo.orEmpty(), color = Color(0xFF747A82), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) { Text("发消息") }
                }
            }
            item {
                ProfileSectionTitle("客户画像")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    ProfileValueRow("客户等级", profile?.customerLevel ?: "未设置")
                    ProfileValueRow("来源渠道", profile?.sourceChannel ?: "未设置")
                    ProfileValueRow("备注", profile?.notes ?: "未设置")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showProfileEditor = true }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("编辑画像")
                        }
                    }
                }
            }
            item {
                ProfileSectionTitle("标签（${labels.size}）")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    if (labels.isEmpty()) Text("暂无标签", color = Color(0xFF8B929A), fontSize = 13.sp)
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.take(6).forEach { label ->
                            AssistChip(onClick = {}, label = { Text(label.tagName ?: "未命名") }, leadingIcon = { Icon(Icons.Filled.Label, null) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showBatchLabels = true }) { Text("编辑标签") }
                    }
                }
            }
            item {
                ProfileSectionTitle("共同群聊（${detail?.commonChatRooms?.size ?: 0}）")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    detail?.commonChatRooms.orEmpty().take(5).forEach { room ->
                        ProfileValueRow(room.name ?: room.chatRoomId ?: "未命名群聊", "${room.memberCount} 人")
                    }
                    if (detail?.commonChatRooms.isNullOrEmpty()) Text("暂无共同群聊", color = Color(0xFF8B929A), fontSize = 13.sp)
                }
            }
            item {
                ProfileSectionTitle("关系动态")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    detail?.relationLogs.orEmpty().take(5).forEach { log ->
                        ProfileValueRow(log.actionText ?: log.changeType ?: "关系变更", log.createdAt ?: "")
                    }
                    if (detail?.relationLogs.isNullOrEmpty()) Text("暂无关系动态", color = Color(0xFF8B929A), fontSize = 13.sp)
                }
            }
            if (loading) item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(22.dp)) } }
            status?.let { message -> item { TextLabel(message, 12.sp, color = Color(0xFF4E7A55), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 2) } }
            error?.let { message -> item { TextLabel(message, 12.sp, color = Color(0xFFB44B4B), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 3) } }
        }
    }

    if (showProfileEditor) {
        AlertDialog(
            onDismissRequest = { showProfileEditor = false },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("编辑客户画像", modifier = Modifier.weight(1f))
                    FloatingDialogCloseButton(onClose = { showProfileEditor = false })
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft.level, { draft = draft.copy(level = it) }, label = { Text("客户等级") }, singleLine = true)
                    OutlinedTextField(draft.source, { draft = draft.copy(source = it) }, label = { Text("来源渠道") }, singleLine = true)
                    OutlinedTextField(draft.notes, { draft = draft.copy(notes = it) }, label = { Text("备注") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(onClick = {
                    // UI 对接：组装 ScrmSaveCustomerProfileRequest，人工联调时再接入写任务。
                    val request = ScrmSaveCustomerProfileRequest(
                        weChatId = contact.wxid,
                        customerLevel = draft.level.trim().takeIf { it.isNotEmpty() },
                        sourceChannel = draft.source.trim().takeIf { it.isNotEmpty() },
                        notes = draft.notes.trim().takeIf { it.isNotEmpty() },
                        mappedLabelIds = labels.map { it.labelId }.filter { it > 0 },
                        mappedLabelNames = labels.mapNotNull { it.tagName }
                    )
                    onWritePreview("客户画像请求已组装，未发送（${request::class.simpleName}）")
                    showProfileEditor = false
                }) { Text("组装请求") }
            },
            dismissButton = { TextButton(onClick = { showProfileEditor = false }) { Text("取消") } }
        )
    }
    if (showBatchLabels) {
        AlertDialog(
            onDismissRequest = { showBatchLabels = false },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("批量标签", modifier = Modifier.weight(1f))
                    FloatingDialogCloseButton(onClose = { showBatchLabels = false })
                }
            },
            text = { Text("目标联系人：1 人\n标签集合：${labels.joinToString { it.tagName ?: "未命名" }}\n合并模式：mergeExisting=true\n最大数量：maxCount=1") },
            confirmButton = {
                Button(onClick = {
                    // UI 对接：默认 mergeExisting=true、maxCount=1，仅组装请求，不发送。
                    val request = ScrmBatchSetContactLabelsByFilterRequest(
                        labelIds = labels.map { it.labelId }.filter { it > 0 },
                        labelNames = labels.mapNotNull { it.tagName },
                        search = contact.displayName,
                        mergeExisting = true,
                        maxCount = 1
                    )
                    onWritePreview("批量标签请求已组装，未发送（${request::class.simpleName}）")
                    showBatchLabels = false
                }) { Text("组装请求") }
            },
            dismissButton = { TextButton(onClick = { showBatchLabels = false }) { Text("取消") } }
        )
    }
}

private data class ProfileDraft(val level: String, val source: String, val notes: String)

@Composable private fun ProfileSectionTitle(text: String) { Text(text, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp), color = Color(0xFF858C94), fontSize = 12.sp) }
@Composable private fun ProfileValueRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, Modifier.width(80.dp), color = Color(0xFF4B5158), fontSize = 13.sp); Text(value.ifBlank { "未设置" }, color = Color(0xFF777E86), fontSize = 13.sp) } }
