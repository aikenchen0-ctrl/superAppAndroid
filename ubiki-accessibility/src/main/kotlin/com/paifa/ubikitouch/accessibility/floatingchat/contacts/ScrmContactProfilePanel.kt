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
import androidx.compose.material3.Switch
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
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactLabel
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactDetail
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile
import com.paifa.ubikitouch.accessibility.scrm.ScrmSaveCustomerProfileRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSetFriendPermissionRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmModifyFriendProfileRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSaveContactLabelRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmDeleteContactLabelRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountMutationRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmRefreshFriendInfoRequest

/** UI 对接：GET 联系人详情；写操作仅组装 request 并由人工确认，不调用网络。 */
private enum class ContactEditorPage { CustomerProfile, Labels, FriendPermissions }

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
    onWritePreview: (String) -> Unit,
    saving: Boolean = false,
    availableLabels: List<ScrmContactLabel> = emptyList(),
    onSaveCustomerProfile: (CustomerProfileDraft) -> Unit = {},
    onSaveContactLabels: (ContactLabelsDraft) -> Unit = {},
    onSaveFriendPermissions: (FriendPermissionsDraft) -> Unit = {}
) {
    var editorPage by remember(contact.id) { mutableStateOf<ContactEditorPage?>(null) }
    var showProfileEditor by remember(contact.id) { mutableStateOf(false) }
    var showBatchLabels by remember(contact.id) { mutableStateOf(false) }
    var showFriendEditor by remember(contact.id) { mutableStateOf(false) }
    var showLabelEditor by remember(contact.id) { mutableStateOf(false) }
    var onlyChat by remember(contact.id) { mutableStateOf(false) }
    var notSeeMoments by remember(contact.id) { mutableStateOf(false) }
    var notLetSeeMoments by remember(contact.id) { mutableStateOf(false) }
    val profile = detail?.customerProfile ?: customerProfile
    var draft by remember(profile) {
        mutableStateOf(
            ProfileDraft(
                level = profile?.customerLevel.orEmpty(),
                source = profile?.sourceChannel.orEmpty(),
                sourceDetail = profile?.sourceDetail.orEmpty(),
                profileKey = profile?.profileKey.orEmpty(),
                socialAccounts = profile?.socialAccounts.orEmpty(),
                purchaseHistory = profile?.purchaseHistory.orEmpty(),
                notes = profile?.notes.orEmpty()
            )
        )
    }
    val labels = detail?.labels.orEmpty().filterNot { it.isDeleted }

    if (editorPage != null) {
        when (editorPage) {
            ContactEditorPage.CustomerProfile -> ScrmCustomerProfileEditor(
                snapshot = profile,
                saving = saving,
                error = error,
                verificationMessage = status,
                onPreview = { onWritePreview("客户画像变更待确认：${it.customerLevel ?: "未设置"}") },
                onConfirm = onSaveCustomerProfile,
                onCancel = { editorPage = null }
            )
            ContactEditorPage.Labels -> ScrmContactLabelsEditor(
                availableLabels = (availableLabels + labels).distinctBy { it.labelId },
                current = ContactLabelsDraft(
                    selectedLabelIds = labels.map { it.labelId }.filter { it > 0 }.toSet(),
                    selectedLabelNames = labels.mapNotNull { it.tagName }.toSet()
                ),
                saving = saving,
                error = error,
                onConfirm = onSaveContactLabels,
                onCancel = { editorPage = null }
            )
            ContactEditorPage.FriendPermissions -> ScrmFriendPermissionsEditor(
                current = permissionDraftFromContact(detail?.contact ?: contact),
                saving = saving,
                error = error,
                onConfirm = onSaveFriendPermissions,
                onCancel = { editorPage = null }
            )
            null -> Unit
        }
        return
    }

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
                        TextButton(onClick = { editorPage = ContactEditorPage.CustomerProfile }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("编辑画像")
                        }
                    }
                }
            }
            item {
                ProfileSectionTitle("好友资料")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    Text("微信侧资料变更需要单独确认", color = Color(0xFF777E86), fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editorPage = ContactEditorPage.FriendPermissions }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("编辑资料")
                        }
                        TextButton(onClick = {
                            val request = ScrmRefreshFriendInfoRequest(contactId = contact.id, friendId = contact.wxid)
                            onWritePreview("好友资料刷新请求已组装，未发送（${request::class.simpleName}）")
                        }) { Text("预览刷新") }
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
                        TextButton(onClick = { showLabelEditor = true }) { Text("管理标签") }
                        TextButton(onClick = { editorPage = ContactEditorPage.Labels }) { Text("编辑标签") }
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
                ProfileSectionTitle("朋友圈权限")
                Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionSwitchRow("仅聊天", "限制双方朋友圈可见", onlyChat) { onlyChat = it }
                    PermissionSwitchRow("不看他的朋友圈", "隐藏该联系人的朋友圈", notSeeMoments) { notSeeMoments = it }
                    PermissionSwitchRow("不让他看我的朋友圈", "限制该联系人查看我的朋友圈", notLetSeeMoments) { notLetSeeMoments = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            val request = ScrmSetFriendPermissionRequest(
                                contactId = contact.id,
                                friendId = contact.wxid,
                                onlyChat = onlyChat,
                                notSeeFriendMoments = notSeeMoments,
                                notLetFriendSeeMyMoments = notLetSeeMoments
                            )
                            onWritePreview("朋友圈权限请求已组装，未发送（${request::class.simpleName}）")
                        }) { Text("预览权限变更") }
                    }
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
                    PreviewBanner("仅 UI 预览", "保存后只组装 SCRM 请求，不会修改服务端数据")
                    Text("基础信息", color = Color(0xFF858C94), fontSize = 11.sp)
                    OutlinedTextField(draft.level, { draft = draft.copy(level = it) }, label = { Text("客户等级") }, singleLine = true)
                    OutlinedTextField(draft.source, { draft = draft.copy(source = it) }, label = { Text("来源渠道") }, singleLine = true)
                    OutlinedTextField(draft.sourceDetail, { draft = draft.copy(sourceDetail = it) }, label = { Text("来源详情") }, singleLine = true)
                    Text("运营信息", color = Color(0xFF858C94), fontSize = 11.sp)
                    OutlinedTextField(draft.profileKey, { draft = draft.copy(profileKey = it) }, label = { Text("画像标识") }, singleLine = true)
                    OutlinedTextField(draft.socialAccounts, { draft = draft.copy(socialAccounts = it) }, label = { Text("社交账号") }, singleLine = true)
                    OutlinedTextField(draft.purchaseHistory, { draft = draft.copy(purchaseHistory = it) }, label = { Text("购买记录") }, minLines = 2)
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
                        sourceDetail = draft.sourceDetail.trim().takeIf { it.isNotEmpty() },
                        profileKey = draft.profileKey.trim().takeIf { it.isNotEmpty() },
                        socialAccounts = draft.socialAccounts.trim().takeIf { it.isNotEmpty() },
                        purchaseHistory = draft.purchaseHistory.trim().takeIf { it.isNotEmpty() },
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
    if (showFriendEditor) {
        var memo by remember(contact.id) { mutableStateOf("") }
        var description by remember(contact.id) { mutableStateOf("") }
        var phone by remember(contact.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFriendEditor = false },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("编辑好友资料", modifier = Modifier.weight(1f))
                    FloatingDialogCloseButton(onClose = { showFriendEditor = false })
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewBanner("仅 UI 预览", "备注、描述和电话会修改微信侧资料，当前只组装请求")
                    OutlinedTextField(memo, { memo = it }, label = { Text("备注") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("描述") }, minLines = 2)
                    OutlinedTextField(phone, { phone = it }, label = { Text("电话") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val request = ScrmModifyFriendProfileRequest(
                        contactId = contact.id,
                        friendId = contact.wxid,
                        memo = memo.trim().takeIf { it.isNotEmpty() },
                        desc = description.trim().takeIf { it.isNotEmpty() },
                        phone = phone.trim().takeIf { it.isNotEmpty() }
                    )
                    onWritePreview("好友资料请求已组装，未发送（${request::class.simpleName}）")
                    showFriendEditor = false
                }) { Text("生成请求预览") }
            },
            dismissButton = { TextButton(onClick = { showFriendEditor = false }) { Text("取消") } }
        )
    }
    if (showLabelEditor) {
        var labelName by remember(contact.id) { mutableStateOf("") }
        var labelId by remember(contact.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLabelEditor = false },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("管理标签", modifier = Modifier.weight(1f))
                    FloatingDialogCloseButton(onClose = { showLabelEditor = false })
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewBanner("仅 UI 预览", "不填写标签 ID 时创建标签，填写已有 ID 时用于重命名")
                    OutlinedTextField(labelName, { labelName = it }, label = { Text("标签名称") }, singleLine = true)
                    OutlinedTextField(labelId, { labelId = it.filter(Char::isDigit) }, label = { Text("标签 ID，可选") }, singleLine = true)
                    if (labels.isNotEmpty()) Text("现有标签：${labels.joinToString { it.tagName ?: "未命名" }}", color = Color(0xFF777E86), fontSize = 11.sp)
                    TextButton(onClick = {
                        val request = ScrmAccountMutationRequest(weChatId = contact.wxid)
                        onWritePreview("同步标签请求已组装，未发送（${request::class.simpleName}）")
                    }) { Text("预览同步当前账号标签") }
                    if (labelId.toIntOrNull()?.let { it > 0 } == true) {
                        TextButton(onClick = {
                            val request = ScrmDeleteContactLabelRequest(labelId = labelId.toInt())
                            onWritePreview("删除标签请求已组装，未发送；该操作不可撤销（${request::class.simpleName}）")
                            showLabelEditor = false
                        }) { Text("预览删除此标签", color = Color(0xFFB44B4B)) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val request = ScrmSaveContactLabelRequest(
                        labelName = labelName.trim(),
                        labelId = labelId.toIntOrNull() ?: 0
                    )
                    onWritePreview("标签请求已组装，未发送（${request::class.simpleName}）")
                    showLabelEditor = false
                }, enabled = labelName.isNotBlank()) { Text("生成请求预览") }
            },
            dismissButton = { TextButton(onClick = { showLabelEditor = false }) { Text("取消") } }
        )
    }
    if (showBatchLabels) {
        var labelDraft by remember(contact.id) { mutableStateOf(labels.joinToString { it.tagName ?: "" }) }
        AlertDialog(
            onDismissRequest = { showBatchLabels = false },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("批量标签", modifier = Modifier.weight(1f))
                    FloatingDialogCloseButton(onClose = { showBatchLabels = false })
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewBanner("仅 UI 预览", "默认只影响当前联系人，接入后再开放批量范围")
                    Text("目标联系人：1 人", color = Color(0xFF4B5158), fontSize = 13.sp)
                    OutlinedTextField(
                        value = labelDraft,
                        onValueChange = { labelDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标签名称，用逗号分隔") },
                        singleLine = true
                    )
                    Text("合并模式：mergeExisting=true · 最大数量：maxCount=1", color = Color(0xFF777E86), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    // UI 对接：默认 mergeExisting=true、maxCount=1，仅组装请求，不发送。
                    val request = ScrmBatchSetContactLabelsByFilterRequest(
                        labelIds = labels.map { it.labelId }.filter { it > 0 },
                        labelNames = labelDraft.split(',').map { it.trim() }.filter { it.isNotEmpty() },
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

private data class ProfileDraft(
    val level: String,
    val source: String,
    val sourceDetail: String,
    val profileKey: String,
    val socialAccounts: String,
    val purchaseHistory: String,
    val notes: String
)

@Composable
private fun PreviewBanner(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F6EC), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, color = Color(0xFF4E7A55), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = Color(0xFF6B766E), fontSize = 10.sp)
    }
}

@Composable private fun ProfileSectionTitle(text: String) { Text(text, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp), color = Color(0xFF858C94), fontSize = 12.sp) }
@Composable private fun ProfileValueRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, Modifier.width(80.dp), color = Color(0xFF4B5158), fontSize = 13.sp); Text(value.ifBlank { "未设置" }, color = Color(0xFF777E86), fontSize = 13.sp) } }

@Composable
private fun PermissionSwitchRow(label: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFF4B5158), fontSize = 13.sp)
            Text(detail, color = Color(0xFF8B929A), fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
