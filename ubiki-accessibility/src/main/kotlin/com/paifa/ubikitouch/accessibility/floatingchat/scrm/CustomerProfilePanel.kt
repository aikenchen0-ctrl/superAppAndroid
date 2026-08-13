package com.paifa.ubikitouch.accessibility.floatingchat.scrm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactManagementApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile
import com.paifa.ubikitouch.accessibility.scrm.ScrmSaveCustomerProfileRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CustomerProfileDraft(
    val customerLevel: String = "",
    val sourceChannel: String = "",
    val sourceDetail: String = "",
    val profileKey: String = "",
    val purchaseHistory: String = "",
    val socialAccounts: String = "",
    val notes: String = "",
    val mappedLabelNames: String = ""
)

@Composable
internal fun CustomerProfilePanel(manager: ScrmSettingsManager, onClose: () -> Unit) {
    var contacts by remember { mutableStateOf<List<ScrmContact>>(emptyList()) }
    var selectedContact by remember { mutableStateOf<ScrmContact?>(null) }
    var profile by remember { mutableStateOf<ScrmCustomerProfile?>(null) }
    var draft by remember { mutableStateOf(CustomerProfileDraft()) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadProfile(contact: ScrmContact) {
        scope.launch {
            loading = true
            status = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.contactApi.getCustomerProfile(contact.id, session.weChatId)
                }
            }.onSuccess { loaded ->
                selectedContact = contact
                profile = loaded
                draft = loaded.toDraft()
                editing = false
            }.onFailure { error -> status = error.message ?: "客户档案读取失败" }
            loading = false
        }
    }

    fun saveProfile() {
        val contact = selectedContact ?: return
        scope.launch {
            loading = true
            status = "正在保存客户档案"
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.contactApi as? ScrmContactManagementApi
                        ?: error("当前 SCRM 客户端不支持客户档案保存")
                    api.saveCustomerProfile(contact.id, draft.toRequest(session.weChatId))
                    session.contactApi.getCustomerProfile(contact.id, session.weChatId)
                }
            }.onSuccess { persisted ->
                profile = persisted
                draft = persisted.toDraft()
                editing = false
                status = "客户档案已保存并完成回读"
            }.onFailure { error -> status = error.message ?: "客户档案保存失败" }
            loading = false
        }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        status = null
        runCatching {
            withContext(Dispatchers.IO) {
                val session = manager.loadSelectedSessionOrBootstrap()
                session.contactApi.getContacts(
                    ScrmContactQuery(weChatId = session.weChatId, page = 1, pageSize = 50, onlyFriends = true)
                ).items
            }
        }.onSuccess { loaded ->
            contacts = loaded
            val current = selectedContact?.let { selected -> loaded.firstOrNull { it.id == selected.id } } ?: loaded.firstOrNull()
            if (current != null) loadProfile(current)
            else status = "暂无已同步联系人，请先同步好友资料"
        }.onFailure { error -> status = error.message ?: "联系人加载失败" }
        loading = false
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountBox, contentDescription = null, tint = OverlayTokens.accent)
            Spacer(Modifier.width(8.dp))
            TextLabel("客户档案", 17.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refreshKey += 1 }, enabled = !loading) { Icon(Icons.Filled.Refresh, "刷新客户档案", tint = OverlayTokens.toolIcon) }
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "关闭客户档案", tint = OverlayTokens.toolIcon) }
        }
        TextLabel(if (loading) "正在同步客户档案" else "选择客户后读取并编辑真实 SCRM 档案", 12.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        status?.let { TextLabel(it, 12.sp, color = OverlayTokens.alertCore, maxLines = 3, modifier = Modifier.padding(top = 6.dp)) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { ContactSelector(contacts, selectedContact, !loading, ::loadProfile) }
            if (selectedContact != null) {
                item { ProfileHeader(selectedContact!!, profile) { editing = !editing } }
                if (editing) {
                    item { ProfileEditor(draft, onDraftChange = { draft = it }, onSave = ::saveProfile, saving = loading) }
                } else {
                    item { ProfileOverview(profile) }
                    item { ProfileTags(profile?.mappedLabelNames.orEmpty()) }
                    item { ProfileTimeline(profile) }
                }
            }
        }
    }
}

@Composable private fun ContactSelector(contacts: List<ScrmContact>, selected: ScrmContact?, enabled: Boolean, onSelect: (ScrmContact) -> Unit) {
    ProfileCard {
        TextLabel("当前客户", 13.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1)
        if (contacts.isEmpty()) TextLabel("暂无可用联系人", 12.sp, color = OverlayTokens.panelSecondaryText)
        else contacts.take(6).forEach { contact ->
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect(contact) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(28.dp), CircleShape, color = if (selected?.id == contact.id) OverlayTokens.accent else OverlayTokens.toolIcon) {
                    TextLabel(contact.displayName.take(1), 12.sp, color = Color.White, weight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, start = 9.dp))
                }
                Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { TextLabel(contact.displayName, 12.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.SemiBold, maxLines = 1); TextLabel(contact.wxid.orEmpty(), 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1) }
            }
        }
    }
}

@Composable private fun ProfileHeader(contact: ScrmContact, profile: ScrmCustomerProfile?, onEdit: () -> Unit) {
    ProfileCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), CircleShape, color = OverlayTokens.accent) { TextLabel(contact.displayName.take(1), 22.sp, color = Color.White, weight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp, start = 16.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { TextLabel(contact.displayName, 18.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1); TextLabel("WXID：${contact.wxid.orEmpty()}", 11.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1) }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑客户档案", tint = OverlayTokens.accent) }
        }
        TextLabel(profile?.customerLevel?.takeIf { it.isNotBlank() }?.let { "$it 级客户" } ?: "未分级客户", 11.sp, color = OverlayTokens.accent, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable private fun ProfileOverview(profile: ScrmCustomerProfile?) = ProfileCard {
    TextLabel("客户概览", 14.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(profile?.sourceChannel, "来源渠道", Modifier.weight(1f)); Metric(profile?.profileKey, "画像标识", Modifier.weight(1f)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(profile?.phone, "联系电话", Modifier.weight(1f)); Metric(profile?.sourceDetail, "来源详情", Modifier.weight(1f)) }
}

@Composable private fun Metric(value: String?, label: String, modifier: Modifier) = Surface(modifier, RoundedCornerShape(7.dp), color = OverlayTokens.resourcePanel) { Column(Modifier.height(62.dp).padding(8.dp)) { TextLabel(value.orEmpty().ifBlank { "未设置" }, 12.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1); TextLabel(label, 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1) } }

@Composable private fun ProfileTags(tags: List<String>) = ProfileCard { TextLabel("画像标签映射（非微信联系人标签）", 13.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold); TextLabel(tags.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "暂无标签", 12.sp, color = OverlayTokens.accent, maxLines = 3, modifier = Modifier.padding(top = 8.dp)) }

@Composable private fun ProfileTimeline(profile: ScrmCustomerProfile?) = ProfileCard { TextLabel("最近动态", 14.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold); TimelineRow("购买记录", profile?.purchaseHistory); TimelineRow("社交帐号", profile?.socialAccounts); TimelineRow("档案备注", profile?.notes) }
@Composable private fun TimelineRow(title: String, detail: String?) { Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Filled.AccountBox, null, tint = OverlayTokens.accent, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Column { TextLabel(title, 12.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.SemiBold); TextLabel(detail.orEmpty().ifBlank { "暂无${title}" }, 11.sp, color = OverlayTokens.panelSecondaryText, maxLines = 2) } } }

@Composable private fun ProfileEditor(draft: CustomerProfileDraft, onDraftChange: (CustomerProfileDraft) -> Unit, onSave: () -> Unit, saving: Boolean) = ProfileCard {
    TextLabel("编辑客户档案", 14.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold)
    ProfileInput("客户等级", draft.customerLevel) { onDraftChange(draft.copy(customerLevel = it)) }; ProfileInput("来源渠道", draft.sourceChannel) { onDraftChange(draft.copy(sourceChannel = it)) }; ProfileInput("来源详情", draft.sourceDetail) { onDraftChange(draft.copy(sourceDetail = it)) }; ProfileInput("画像标识", draft.profileKey) { onDraftChange(draft.copy(profileKey = it)) }; ProfileInput("购买记录", draft.purchaseHistory) { onDraftChange(draft.copy(purchaseHistory = it)) }; ProfileInput("社交帐号", draft.socialAccounts) { onDraftChange(draft.copy(socialAccounts = it)) }; ProfileInput("档案备注", draft.notes) { onDraftChange(draft.copy(notes = it)) }; ProfileInput("画像标签（逗号分隔）", draft.mappedLabelNames) { onDraftChange(draft.copy(mappedLabelNames = it)) }
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(enabled = !saving, onClick = onSave), RoundedCornerShape(7.dp), color = OverlayTokens.accent) { TextLabel(if (saving) "正在保存" else "保存并回读确认", 13.sp, color = Color.White, weight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
}
@Composable private fun ProfileInput(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(top = 7.dp), label = { TextLabel(label, 11.sp, color = OverlayTokens.panelSecondaryText) }, singleLine = true) }
@Composable private fun ProfileCard(content: @Composable () -> Unit) = Surface(Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), color = OverlayTokens.control, border = BorderStroke(1.dp, OverlayTokens.hairline)) { Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { content() } }

private fun ScrmCustomerProfile.toDraft() = CustomerProfileDraft(customerLevel.orEmpty(), sourceChannel.orEmpty(), sourceDetail.orEmpty(), profileKey.orEmpty(), purchaseHistory.orEmpty(), socialAccounts.orEmpty(), notes.orEmpty(), mappedLabelNames.joinToString(", "))
private fun CustomerProfileDraft.toRequest(weChatId: String) = ScrmSaveCustomerProfileRequest(weChatId = weChatId, customerLevel = customerLevel.blankToNull(), sourceChannel = sourceChannel.blankToNull(), sourceDetail = sourceDetail.blankToNull(), profileKey = profileKey.blankToNull(), purchaseHistory = purchaseHistory.blankToNull(), socialAccounts = socialAccounts.blankToNull(), notes = notes.blankToNull(), mappedLabelNames = mappedLabelNames.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct())
private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
