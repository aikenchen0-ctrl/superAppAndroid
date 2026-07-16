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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactSummary

@Composable
internal fun ContactProfileScreen(
    state: ContactProfileUiState,
    onEvent: (ContactProfileUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.editing) {
        ContactEditorContent(state, onEvent, modifier)
    } else {
        ContactIntroContent(state, onEvent, modifier)
    }
}

@Composable
private fun ContactIntroContent(
    state: ContactProfileUiState,
    onEvent: (ContactProfileUiEvent) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ContactsPageBackground)
    ) {
        ContactIntroTopBar { onEvent(ContactProfileUiEvent.BackRequested) }
        if (state.contactId == null) {
            TextLabel(
                text = "请选择联系人",
                size = 13.sp,
                color = ContactsSecondaryText,
                modifier = Modifier.padding(18.dp),
                maxLines = 1
            )
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ContactsRowBackground)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ContactAvatar(state, 64.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TextLabel(state.displayName, 21.sp, color = ContactsPrimaryText, weight = FontWeight.Bold, maxLines = 1)
                TextLabel(
                    text = "微信号：${state.wechatId.ifBlank { "无" }}",
                    size = 13.sp,
                    color = ContactsSecondaryText,
                    maxLines = 1
                )
                TextLabel(
                    text = "地区：${state.region.ifBlank { "未知" }}",
                    size = 13.sp,
                    color = ContactsSecondaryText,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ContactIntroRow(
            title = "朋友资料",
            subtitle = "添加朋友的备注名、电话、标签、备忘、照片等，并设置朋友圈权限。",
            onClick = { onEvent(ContactProfileUiEvent.EditRequested) }
        )
        Spacer(Modifier.height(8.dp))
        ContactIntroRow("朋友圈", null) { onEvent(ContactProfileUiEvent.MomentsRequested) }
        Spacer(Modifier.height(8.dp))
        ContactIntroActionRow(Icons.Filled.Textsms, "发消息", !state.loading) {
            onEvent(ContactProfileUiEvent.MessageRequested)
        }
        ContactIntroActionRow(Icons.Filled.PhoneAndroid, "音视频通话", !state.loading) {
            onEvent(ContactProfileUiEvent.VideoCallRequested)
        }
    }
}

@Composable
private fun ContactEditorContent(
    state: ContactProfileUiState,
    onEvent: (ContactProfileUiEvent) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(ProfilePageBackground),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item { ProfileTopBar { onEvent(ContactProfileUiEvent.BackRequested) } }
        item { ProfileHeader(state) }
        item { FriendProfileSectionTitle("备注") }
        item {
            FriendProfileSection {
                FriendProfileEditableRow("备注名", state.remark, "填写备注名") {
                    onEvent(ContactProfileUiEvent.RemarkChanged(it))
                }
                FriendProfileDivider()
                FriendProfileInfoRow("电话", state.phone)
                FriendProfileDivider()
                FriendProfileEditableRow("标签", state.tags, "添加标签") {
                    onEvent(ContactProfileUiEvent.TagsChanged(it))
                }
                FriendProfileDivider()
                FriendProfileEditableRow("备注", state.memo, "添加描述", maxLines = 2, minHeight = 58.dp) {
                    onEvent(ContactProfileUiEvent.MemoChanged(it))
                }
                FriendProfileDivider()
                ProfilePhotosRow(state)
            }
        }
        item { FriendProfileSectionTitle("朋友权限") }
        item {
            FriendProfileSection {
                FriendProfileSwitchRow("朋友圈和状态", "允许他看我的朋友圈", state.friendCircleVisible) {
                    onEvent(ContactProfileUiEvent.FriendCircleVisibilityChanged(it))
                }
                FriendProfileDivider()
                FriendProfileSwitchRow("仅聊天", "开启后不看彼此朋友圈", state.onlyChat) {
                    onEvent(ContactProfileUiEvent.OnlyChatChanged(it))
                }
            }
        }
        item { FriendProfileSectionTitle("更多信息") }
        item {
            FriendProfileSection {
                FriendProfileInfoRow("我和他的共同群聊", "${state.commonGroupCount} 个群聊")
                FriendProfileDivider()
                FriendProfileInfoRow("来源", state.source)
                FriendProfileDivider()
                FriendProfileInfoRow("添加时间", state.addedTime)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileButton("删除") { onEvent(ContactProfileUiEvent.DeleteRequested) }
                ProfileButton("完成") { onEvent(ContactProfileUiEvent.DoneRequested) }
            }
        }
    }
}

@Composable
private fun ContactIntroTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ContactsRowBackground).padding(6.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ContactsPrimaryText)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.MoreHoriz, null, tint = ContactsPrimaryText, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun ContactIntroRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ContactsRowBackground).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextLabel(title, 15.sp, color = ContactsPrimaryText, weight = FontWeight.SemiBold, maxLines = 1)
            subtitle?.let { TextLabel(it, 11.sp, color = ContactsSecondaryText, maxLines = 2) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ContactsChevronText)
    }
}

@Composable
private fun ContactIntroActionRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ContactsRowBackground).clickable(enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = ProfileActionText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        TextLabel(label, 16.sp, color = ProfileActionText, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(46.dp).background(ProfilePageBackground)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ProfilePrimaryText)
        }
        TextLabel(
            text = "朋友资料",
            size = 14.sp,
            modifier = Modifier.align(Alignment.Center),
            color = ProfilePrimaryText,
            weight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileHeader(state: ContactProfileUiState) {
    Row(
        Modifier.fillMaxWidth().background(ProfileCardBackground).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(state, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextLabel(
                state.remark.ifBlank { state.displayName },
                17.sp,
                color = ProfilePrimaryText,
                weight = FontWeight.SemiBold,
                maxLines = 1
            )
            TextLabel("昵称：${state.originalName}", 11.sp, color = ProfileSecondaryText, maxLines = 1)
            TextLabel("微信号：${state.wechatId}", 11.sp, color = ProfileSecondaryText, maxLines = 1)
            TextLabel(state.description, 11.sp, color = ProfileSecondaryText, maxLines = 1)
        }
    }
}

@Composable
private fun ContactAvatar(state: ContactProfileUiState, size: Dp) {
    val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
    if (avatarUrl != null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.scale(size.value / ContactAvatarBaseSize.value)) {
                ContactAvatar(
                    ContactSummary(
                        id = state.contactId.orEmpty(),
                        displayName = state.displayName,
                        avatarUrl = avatarUrl,
                        avatarColor = state.avatarColor
                    )
                )
            }
        }
        return
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(state.avatarColor)),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(
            state.initials.ifBlank { state.displayName.take(2) },
            14.sp,
            color = Color.White,
            weight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private val ContactAvatarBaseSize = 42.dp

@Composable
private fun FriendProfileSectionTitle(title: String) {
    TextLabel(title, 11.sp, color = ProfileSectionText, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp), maxLines = 1)
}

@Composable
private fun FriendProfileSection(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ProfileCardBackground)) { content() }
}

@Composable
private fun FriendProfileInfoRow(
    label: String,
    value: String,
    showArrow: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(label, 13.sp, color = ProfilePrimaryText, modifier = Modifier.width(112.dp), maxLines = 1)
        TextLabel(value, 12.sp, color = ProfileSecondaryText, modifier = Modifier.weight(1f), maxLines = 1, textAlign = TextAlign.End)
        if (showArrow) {
            Spacer(Modifier.width(6.dp))
            FriendProfileChevron()
        }
    }
}

@Composable
private fun FriendProfileEditableRow(
    label: String,
    value: String,
    placeholder: String,
    maxLines: Int = 1,
    minHeight: Dp = 44.dp,
    onValueChange: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(label, 13.sp, color = ProfilePrimaryText, modifier = Modifier.width(70.dp), maxLines = 1)
        BasicTextField(
            value, onValueChange, singleLine = maxLines == 1, maxLines = maxLines,
            textStyle = TextStyle(color = ProfileSecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
            cursorBrush = SolidColor(OverlayTokens.accent), modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isBlank()) TextLabel(placeholder, 12.sp, color = ProfilePlaceholderText, maxLines = 1)
                    field()
                }
            }
        )
        Spacer(Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun FriendProfileSwitchRow(label: String, value: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(label, 13.sp, color = ProfilePrimaryText, maxLines = 1)
            TextLabel(value, 10.sp, color = ProfileSecondaryText, maxLines = 1)
        }
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun ProfilePhotosRow(state: ContactProfileUiState) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel("照片", 13.sp, color = ProfilePrimaryText, modifier = Modifier.width(70.dp), maxLines = 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            listOf(0.86f, 0.68f, 0.52f).forEach { alpha ->
                Box(Modifier.padding(start = 5.dp).size(38.dp).clip(RoundedCornerShape(5.dp)).background(Color(state.avatarColor).copy(alpha = alpha)))
            }
        }
        Spacer(Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun FriendProfileDivider() {
    Spacer(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(ProfileDividerColor))
}

@Composable
private fun FriendProfileChevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ProfileChevronText, modifier = Modifier.size(20.dp))
}

@Composable
private fun ProfileButton(label: String, onClick: () -> Unit) {
    Button(
        onClick, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OverlayTokens.resourcePanel),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        TextLabel(
            label,
            11.sp,
            color = OverlayTokens.panelPrimaryText,
            weight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private val ContactsPageBackground = Color(0xFFEDEDED)
private val ContactsRowBackground = Color(0xFFFCFCFC)
private val ContactsPrimaryText = Color(0xFF202020)
private val ContactsSecondaryText = Color(0xFF8B8B8B)
private val ContactsChevronText = Color(0xFFC0C0C0)
private val ProfilePageBackground = Color(0xFFF2F3F5)
private val ProfileCardBackground = Color.White
private val ProfilePrimaryText = Color(0xFF111111)
private val ProfileSecondaryText = Color(0xFF656A70)
private val ProfileSectionText = Color(0xFF8C939A)
private val ProfilePlaceholderText = Color(0xFFB2B8BE)
private val ProfileChevronText = Color(0xFFB4BBC2)
private val ProfileDividerColor = Color(0xFFE8ECEF)
private val ProfileActionText = Color(0xFF49679E)
