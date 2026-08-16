package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.floatingchat.components.rememberAsyncAvatarBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.wechatStartGroupDoneLabel
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.core.model.FloatingChatContact

internal enum class GroupMemberPickerMode(val title: String) {
    Invite("添加成员"),
    Kick("移出成员")
}

/**
 * UI：邀请与移除成员在群信息根视图内以透明全屏工作区呈现，列表使用 [LazyColumn]。
 * 接口：选择结果仍回调给 [GroupInfoHost]，由其复用现有 SCRM 成员增删接口。
 * 测试流程：打开邀请或移除成员，筛选并勾选联系人，点击工具栏完成后检查请求状态与返回路径。
 */
@Composable
internal fun GroupMemberSelectionPanel(
    title: String,
    contacts: List<FloatingChatContact>,
    loading: Boolean,
    status: String?,
    error: String?,
    onBack: () -> Unit,
    onDone: (List<FloatingChatContact>) -> Unit
) {
    var searchText by remember(title) { mutableStateOf("") }
    val selectedIds = remember(title) { mutableStateMapOf<String, Boolean>() }
    val visibleContacts = remember(contacts, searchText) {
        val keyword = searchText.trim()
        if (keyword.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.name.contains(keyword, ignoreCase = true) ||
                    scrmFloatingContactConversationId(contact.id).orEmpty().contains(keyword, ignoreCase = true)
            }
        }
    }
    val selectedCount = selectedIds.values.count { selected -> selected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = title,
            onBack = onBack,
            actions = {
                GroupMemberSelectionButton(
                    label = wechatStartGroupDoneLabel(selectedCount),
                    enabled = selectedCount > 0 && !loading,
                    onClick = {
                        onDone(contacts.filter { contact -> selectedIds[contact.id] == true })
                    }
                )
            }
        )
        GroupMemberSelectionSearchField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        GroupMemberSelectionStatus(status = status, error = error)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleContacts.isEmpty()) {
                    item {
                        Text(
                            text = if (loading) "正在处理" else "暂无可选择联系人",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                itemsIndexed(
                    items = visibleContacts,
                    key = { _, contact -> contact.id }
                ) { _, contact ->
                    GroupMemberPickerRow(
                        contact = contact,
                        selected = selectedIds[contact.id] == true,
                        enabled = !loading,
                        onToggle = {
                            if (selectedIds[contact.id] == true) {
                                selectedIds.remove(contact.id)
                            } else {
                                selectedIds[contact.id] = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMemberSelectionStatus(status: String?, error: String?) {
    val message = error ?: status ?: return
    val isError = error != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Text(
            text = message,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun GroupMemberPickerRow(
    contact: FloatingChatContact,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val avatarBitmap = rememberAsyncAvatarBitmap(contact.avatarUrl)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(contact.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = contact.initials.take(2),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = contact.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = scrmFloatingContactConversationId(contact.id).orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun GroupMemberSelectionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("搜索成员") },
        singleLine = true
    )
}

@Composable
private fun GroupMemberSelectionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label)
    }
}
