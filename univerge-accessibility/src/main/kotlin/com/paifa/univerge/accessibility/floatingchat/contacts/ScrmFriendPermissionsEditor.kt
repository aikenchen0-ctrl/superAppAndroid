package com.paifa.univerge.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp

@Composable
internal fun ScrmFriendPermissionsEditor(
    current: FriendPermissionsDraft,
    saving: Boolean,
    error: String?,
    onConfirm: (FriendPermissionsDraft) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember(current) { mutableStateOf(current) }
    val canSave = permissionRequestValuesOrNull(draft) != null && draft != current && !saving
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F5))) {
        EditorHeader("朋友圈权限", onCancel)
        EditorSection("权限状态") {
            PermissionChoiceRow("仅聊天", draft.onlyChat, enabled = !saving) { value ->
                draft = if (value == PermissionChoice.Enabled) draft.copy(onlyChat = value, notSeeFriendMoments = PermissionChoice.Disabled, notLetFriendSeeMyMoments = PermissionChoice.Disabled) else draft.copy(onlyChat = value)
            }
            PermissionChoiceRow("不看对方朋友圈", draft.notSeeFriendMoments, enabled = !saving && draft.onlyChat != PermissionChoice.Enabled) { draft = draft.copy(notSeeFriendMoments = it) }
            PermissionChoiceRow("不让对方看我的朋友圈", draft.notLetFriendSeeMyMoments, enabled = !saving && draft.onlyChat != PermissionChoice.Enabled) { draft = draft.copy(notLetFriendSeeMyMoments = it) }
        }
        if (draft.onlyChat == PermissionChoice.Enabled) EditorMessage("“仅聊天”会覆盖另外两项权限。", Color(0xFF747A82))
        if (permissionRequestValuesOrNull(draft) == null) EditorMessage("状态尚未同步，请刷新联系人详情或明确设置全部选项。", Color(0xFFB44B4B))
        error?.let { EditorMessage(it, Color(0xFFB44B4B)) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onConfirm(draft) }, enabled = canSave) { Text(if (saving) "保存中" else "确认保存") }
        }
    }
}

@Composable
private fun PermissionChoiceRow(label: String, choice: PermissionChoice, enabled: Boolean, onSelect: (PermissionChoice) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        PermissionChoice.entries.forEach { item ->
            TextButton(onClick = { onSelect(item) }, enabled = enabled, modifier = Modifier.padding(start = 2.dp)) {
                Text(if (choice == item) when (item) { PermissionChoice.Unknown -> "未同步"; PermissionChoice.Enabled -> "开启"; PermissionChoice.Disabled -> "关闭" } else when (item) { PermissionChoice.Unknown -> "未知"; PermissionChoice.Enabled -> "开"; PermissionChoice.Disabled -> "关" })
            }
        }
    }
}
