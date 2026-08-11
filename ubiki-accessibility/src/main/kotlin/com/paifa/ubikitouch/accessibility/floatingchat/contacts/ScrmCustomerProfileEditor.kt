package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile

/** 客户画像编辑页只维护草稿；保存请求由 Contacts Host 执行。 */
@Composable
internal fun ScrmCustomerProfileEditor(
    snapshot: ScrmCustomerProfile?,
    saving: Boolean,
    error: String?,
    verificationMessage: String?,
    onPreview: (CustomerProfileDraft) -> Unit,
    onConfirm: (CustomerProfileDraft) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember(snapshot) { mutableStateOf(snapshot.toCustomerProfileDraft()) }
    val normalized = normalizeCustomerProfileDraft(draft)
    val hasChanges = profileHasChanges(snapshot, normalized)

    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F5))) {
        EditorHeader("编辑客户画像", onCancel)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                EditorSection("基础画像") {
                    ProfileTextField("客户等级", draft.customerLevel) { draft = draft.copy(customerLevel = it) }
                    ProfileTextField("来源渠道", draft.sourceChannel) { draft = draft.copy(sourceChannel = it) }
                    ProfileTextField("来源详情", draft.sourceDetail) { draft = draft.copy(sourceDetail = it) }
                    ProfileTextField("画像标识", draft.profileKey) { draft = draft.copy(profileKey = it) }
                }
            }
            item {
                EditorSection("运营记录") {
                    ProfileTextField("社交账号", draft.socialAccounts) { draft = draft.copy(socialAccounts = it) }
                    ProfileTextField("购买记录", draft.purchaseHistory, minLines = 2) { draft = draft.copy(purchaseHistory = it) }
                    ProfileTextField("备注", draft.notes, minLines = 2) { draft = draft.copy(notes = it) }
                }
            }
            error?.let { message -> item { EditorMessage(message, Color(0xFFB44B4B)) } }
            verificationMessage?.let { message -> item { EditorMessage(message, Color(0xFF4E7A55)) } }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("取消") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onPreview(normalized) }, enabled = hasChanges && !saving) { Text("核对变更") }
            Button(onClick = { onConfirm(normalized) }, enabled = hasChanges && !saving) {
                Text(if (saving) "保存中" else "确认保存")
            }
        }
    }
}

private fun ScrmCustomerProfile?.toCustomerProfileDraft() = CustomerProfileDraft(
    customerLevel = this?.customerLevel,
    sourceChannel = this?.sourceChannel,
    sourceDetail = this?.sourceDetail,
    profileKey = this?.profileKey,
    purchaseHistory = this?.purchaseHistory,
    socialAccounts = this?.socialAccounts,
    faceImageUrl = this?.faceImageUrl,
    notes = this?.notes,
    mappedLabelIds = this?.mappedLabelIds.orEmpty(),
    mappedLabelNames = this?.mappedLabelNames.orEmpty()
)

@Composable
private fun ProfileTextField(label: String, value: String?, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value.orEmpty(), onChange, Modifier.fillMaxWidth(), label = { Text(label) }, minLines = minLines)
}

@Composable
internal fun EditorHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, color = Color(0xFF747A82))
        content()
    }
}

@Composable
internal fun EditorMessage(message: String, color: Color) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = color)
}
