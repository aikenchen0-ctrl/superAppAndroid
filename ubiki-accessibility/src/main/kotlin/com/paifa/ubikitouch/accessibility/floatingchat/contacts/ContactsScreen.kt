package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsShortcut
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiState

@Composable
internal fun ContactsScreen(
    state: ContactsUiState,
    onEvent: (ContactsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ContactsPageBackground)
    ) {
        ContactsTopBar(
            onCloseClick = { onEvent(ContactsUiEvent.CloseRequested) },
            onSearchClick = {
                onEvent(ContactsUiEvent.SearchVisibilityChanged(!state.searchVisible))
            },
            onPlusClick = { onEvent(ContactsUiEvent.PlusMenuRequested) }
        )
        if (state.searchVisible) {
            ContactsSearchRow(state = state, onEvent = onEvent)
        }
        ContactsStatusLine(state = state, onEvent = onEvent)
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp)
                    .padding(end = 16.dp)
            ) {
                item(key = "new-friends") {
                    ContactShortcutRow(
                        icon = Icons.Filled.PersonAdd,
                        iconColor = Color(0xFFFF9F2F),
                        title = "新的朋友",
                        subtitle = state.friendRequests.size.takeIf { it > 0 }
                            ?.let { "$it 条待处理申请" },
                        onClick = {
                            onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.NewFriends))
                        }
                    )
                }
                item(key = "groups") {
                    ContactShortcutRow(
                        icon = Icons.Filled.Groups,
                        iconColor = Color(0xFF16C26D),
                        title = "群聊",
                        onClick = { onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.Groups)) }
                    )
                }
                item(key = "tags") {
                    ContactShortcutRow(
                        icon = Icons.Filled.LocalOffer,
                        iconColor = Color(0xFF2E8BEF),
                        title = "标签",
                        onClick = { onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.Tags)) }
                    )
                }
                item(key = "official") {
                    ContactShortcutRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconColor = Color(0xFF287BEA),
                        title = "公众号",
                        onClick = {
                            onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.OfficialAccounts))
                        }
                    )
                }
                item(key = "enterprise-header") {
                    ContactSectionHeader("我的企业及企业联系人")
                }
                item(key = "wecom") {
                    ContactShortcutRow(
                        icon = Icons.Filled.Contacts,
                        iconColor = Color(0xFF2F93E8),
                        title = "企业微信联系人",
                        onClick = {
                            onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.WeComContacts))
                        }
                    )
                }
                item(key = "assistant") {
                    ContactShortcutRow(
                        icon = Icons.Filled.Home,
                        iconColor = Color(0xFF5BC0F0),
                        title = "血饮智参",
                        onClick = {
                            onEvent(ContactsUiEvent.ShortcutSelected(ContactsShortcut.Assistant))
                        }
                    )
                }
                if (state.groups.isEmpty()) {
                    item(key = "empty") {
                        TextLabel(
                            text = if (state.loading) "正在加载..." else "暂无联系人",
                            size = 12.sp,
                            color = ContactsSecondaryText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            maxLines = 1
                        )
                    }
                }
                state.groups.forEach { group ->
                    item(key = "section-${group.title}") {
                        ContactSectionHeader(group.title)
                    }
                    items(
                        items = group.contacts,
                        key = { contact -> "contact-${contact.id}" }
                    ) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = {
                                onEvent(ContactsUiEvent.ContactSelected(contact.id))
                            }
                        )
                    }
                }
            }
            ContactIndexRail(modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun ContactsTopBar(
    onCloseClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPlusClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ContactsHeaderBackground)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭通讯录",
                tint = ContactsPrimaryText,
                modifier = Modifier.size(21.dp)
            )
        }
        TextLabel(
            text = "通讯录",
            size = 16.sp,
            weight = FontWeight.Bold,
            color = ContactsPrimaryText,
            maxLines = 1
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onSearchClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = ContactsPrimaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPlusClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = ContactsPrimaryText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactsSearchRow(
    state: ContactsUiState,
    onEvent: (ContactsUiEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContactsHeaderBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactSearchField(
            value = state.query,
            onValueChange = { value -> onEvent(ContactsUiEvent.QueryChanged(value)) },
            onSearch = { onEvent(ContactsUiEvent.SearchSubmitted) },
            modifier = Modifier.weight(1f)
        )
        ContactsActionButton(
            label = "搜索",
            enabled = !state.loading,
            accent = true,
            onClick = { onEvent(ContactsUiEvent.SearchSubmitted) }
        )
    }
}

@Composable
private fun ContactsStatusLine(
    state: ContactsUiState,
    onEvent: (ContactsUiEvent) -> Unit
) {
    val message = state.error?.takeIf { it.isNotBlank() }
        ?: state.status?.takeIf { it.isNotBlank() }
        ?: "正在处理".takeIf { state.loading }
    if (message == null && !state.loading) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContactsPageBackground)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextLabel(
            text = message.orEmpty(),
            size = 10.sp,
            color = if (state.error != null) Color(0xFFE45858) else ContactsSecondaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        ContactsActionButton(
            label = "同步",
            enabled = !state.loading,
            onClick = { onEvent(ContactsUiEvent.SyncRequested) }
        )
    }
}

@Composable
private fun ContactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ContactsSearchBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = ContactsHintText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            textStyle = TextStyle.Default.copy(
                color = ContactsPrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.weight(1f)
        ) { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    TextLabel(
                        text = "搜索联系人",
                        size = 14.sp,
                        color = ContactsHintText,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    }
}

@Composable
private fun ContactsActionButton(
    label: String,
    enabled: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) OverlayTokens.accent else OverlayTokens.resourcePanel,
            contentColor = if (accent) Color.White else OverlayTokens.panelPrimaryText,
            disabledContainerColor = OverlayTokens.resourcePanel.copy(alpha = 0.55f),
            disabledContentColor = OverlayTokens.panelSecondaryText
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    ) {
        TextLabel(
            text = label,
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = if (enabled && accent) Color.White else OverlayTokens.panelPrimaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ContactShortcutRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContactsRowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextLabel(
                text = title,
                size = 15.sp,
                color = ContactsPrimaryText,
                maxLines = 1
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                TextLabel(
                    text = value,
                    size = 10.sp,
                    color = ContactsSecondaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ContactSectionHeader(title: String) {
    TextLabel(
        text = title,
        size = 11.sp,
        color = ContactsSecondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .background(ContactsPageBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        maxLines = 1
    )
}

@Composable
private fun ContactRow(
    contact: ContactSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ContactsRowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ContactAvatar(contact = contact)
        TextLabel(
            text = contact.displayName,
            size = 15.sp,
            color = ContactsPrimaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ContactIndexRail(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(end = 2.dp)
            .width(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        contactIndexLabels().forEach { label ->
            TextLabel(
                text = label,
                size = 8.sp,
                color = ContactsIndexText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun contactIndexLabels(): List<String> {
    return listOf("☆") + ('A'..'Z').map(Char::toString) + "#"
}

private val ContactsHeaderBackground = Color(0xFFF1F1F1)
private val ContactsPageBackground = Color(0xFFEDEDED)
private val ContactsRowBackground = Color(0xFFFCFCFC)
private val ContactsSearchBackground = Color(0xFFE9E9E9)
private val ContactsPrimaryText = Color(0xFF202020)
private val ContactsSecondaryText = Color(0xFF8B8B8B)
private val ContactsHintText = Color(0xFFAAAAAA)
private val ContactsIndexText = Color(0xFF333333)
