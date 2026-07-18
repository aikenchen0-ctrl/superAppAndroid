package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.components.rememberAsyncAvatarBitmap
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.wechatStartGroupDoneLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
internal enum class GroupMemberPickerMode(val title: String) {
    Invite("添加成员"),
    Kick("移出成员")
}

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
            .fillMaxWidth()
            .background(WechatContactsPageBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WechatContactsHeaderBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = WechatContactsPrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextLabel(
                text = title,
                size = 14.sp,
                weight = FontWeight.SemiBold,
                color = WechatContactsPrimaryText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            GroupMemberSelectionButton(
                label = wechatStartGroupDoneLabel(selectedCount),
                enabled = selectedCount > 0 && !loading,
                accent = true,
                onClick = {
                    onDone(contacts.filter { contact -> selectedIds[contact.id] == true })
                }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WechatContactsHeaderBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupMemberSelectionSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "鎼滅储",
                onSearch = {},
                modifier = Modifier.weight(1f)
            )
        }
        status?.let { message ->
            TextLabel(
                text = message,
                size = 10.sp,
                color = WechatContactsSecondaryText,
                maxLines = 2,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
        error?.let { message ->
            TextLabel(
                text = message,
                size = 10.sp,
                color = Color(0xFFE45858),
                maxLines = 2,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 520.dp)
        ) {
            if (visibleContacts.isEmpty()) {
                item {
                    TextLabel(
                        text = if (loading) "正在处理..." else "暂无可选择联系人",
                        size = 12.sp,
                        color = WechatContactsSecondaryText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        maxLines = 1
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

@Composable
private fun GroupMemberPickerRow(
    contact: FloatingChatContact,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val avatarBitmap = rememberAsyncAvatarBitmap(contact.avatarUrl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsRowBackground)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextLabel(
            text = if (selected) "●" else "○",
            size = 22.sp,
            color = if (selected) Color(0xFF1AAD19) else WechatContactsSecondaryText,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(7.dp))
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
                TextLabel(
                    text = contact.initials.take(2),
                    size = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = contact.name,
                size = 13.sp,
                color = WechatContactsPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = scrmFloatingContactConversationId(contact.id).orEmpty(),
                size = 10.sp,
                color = WechatContactsSecondaryText,
                maxLines = 1
            )
        }
    }
    GroupMemberSelectionDivider()
}
@Composable
private fun GroupMemberSelectionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle.Default.copy(
            color = WechatContactsPrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(OverlayTokens.accent),
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WechatContactsSearchBackground)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    TextLabel(
                        text = placeholder,
                        size = 12.sp,
                        color = WechatContactsHintText,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun GroupMemberSelectionButton(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                when {
                    !enabled -> Color(0xFFE1E5E8)
                    accent -> OverlayTokens.accent
                    else -> Color(0xFFFFFFFF)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(
            text = label,
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = when {
                !enabled -> WechatContactsSecondaryText
                accent -> Color.White
                else -> WechatContactsPrimaryText
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GroupMemberSelectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WechatContactsDividerColor)
    )
}

private val WechatContactsHeaderBackground = Color(0xFFF1F1F1)
private val WechatContactsPageBackground = Color(0xFFEDEDED)
private val WechatContactsRowBackground = Color(0xFFFCFCFC)
private val WechatContactsSearchBackground = Color(0xFFE9E9E9)
private val WechatContactsPrimaryText = Color(0xFF202020)
private val WechatContactsSecondaryText = Color(0xFF8B8B8B)
private val WechatContactsHintText = Color(0xFFAAAAAA)
private val WechatContactsDividerColor = Color(0xFFE8ECEF)
