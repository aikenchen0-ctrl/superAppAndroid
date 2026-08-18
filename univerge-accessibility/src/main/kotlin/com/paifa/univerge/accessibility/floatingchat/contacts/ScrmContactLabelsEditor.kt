package com.paifa.univerge.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.scrm.ScrmContactLabel

/** 单联系人标签为完整替换；空集合保存前必须显示清空风险。 */
@Composable
internal fun ScrmContactLabelsEditor(
    availableLabels: List<ScrmContactLabel>,
    current: ContactLabelsDraft,
    saving: Boolean,
    error: String?,
    onConfirm: (ContactLabelsDraft) -> Unit,
    onCancel: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember(current) { mutableStateOf(current.selectedLabelIds) }
    val visibleLabels = availableLabels.filterNot { it.isDeleted }.filter { it.tagName.orEmpty().contains(query.trim(), true) }
    val draft = ContactLabelsDraft(selectedIds, availableLabels.filter { it.labelId in selectedIds }.mapNotNull { it.tagName }.toSet())
    val destructive = labelsChangeIsDestructive(current, draft)

    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F5))) {
        EditorHeader("管理联系人标签", onCancel)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp), label = { Text("搜索标签") }, singleLine = true)
        if (destructive) EditorMessage("当前操作会清空该联系人的完整标签集合，请确认。", Color(0xFFB44B4B))
        Text("已选择 ${selectedIds.size} 个标签", Modifier.padding(16.dp), fontSize = 12.sp, color = Color(0xFF747A82))
        LazyColumn(Modifier.weight(1f).background(Color.White)) {
            items(visibleLabels, key = { it.labelId }) { label ->
                val selected = label.labelId in selectedIds
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !saving) {
                        selectedIds = if (selected) selectedIds - label.labelId else selectedIds + label.labelId
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(selected, onCheckedChange = null)
                    Text(label.tagName ?: "未命名标签", Modifier.padding(start = 8.dp))
                }
            }
        }
        error?.let { EditorMessage(it, Color(0xFFB44B4B)) }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onConfirm(draft) }, enabled = draft != current && !saving) { Text(if (saving) "保存中" else "替换标签") }
        }
    }
}
