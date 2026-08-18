package com.paifa.univerge.accessibility.floatingchat.group

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.components.rememberAsyncAvatarBitmap
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoMemberUiState
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoUiEvent
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GroupInfoGridColumns = 5
private val GroupInfoGridHeight = 264.dp
private val GroupInfoAvatarSize = 48.dp
private val GroupInfoMemberLabelWidth = 56.dp

private enum class GroupInfoDestination {
    Main,
    SearchMembers,
    QrCode
}

private data class GroupInfoEditor(
    val title: String,
    val label: String,
    val initialValue: String,
    val multiline: Boolean = false,
    val onValueChanged: (String) -> GroupInfoUiEvent,
    val submitEvent: GroupInfoUiEvent
)

/**
 * 群管理入口使用聊天根中的 M3 全屏工作区。根节点负责统一的纵向进出场动画，
 * 搜索成员和二维码在同一工作区内使用淡入淡出导航，避免创建新的 Window。
 */
@Composable
internal fun GroupInfoScreen(
    state: GroupInfoUiState,
    onEvent: (GroupInfoUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var destination by remember { mutableStateOf(GroupInfoDestination.Main) }
    var editor by remember { mutableStateOf<GroupInfoEditor?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    BackHandler {
        when {
            editor != null -> editor = null
            showExitConfirmation -> showExitConfirmation = false
            destination != GroupInfoDestination.Main -> destination = GroupInfoDestination.Main
            else -> onEvent(GroupInfoUiEvent.BackRequested)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "group-info-navigation"
        ) { currentDestination ->
            when (currentDestination) {
                GroupInfoDestination.Main -> GroupInfoMainScreen(
                    state = state,
                    onEvent = onEvent,
                    onSearchMembers = { destination = GroupInfoDestination.SearchMembers },
                    onOpenQrCode = {
                        destination = GroupInfoDestination.QrCode
                        onEvent(GroupInfoUiEvent.QrCodeRequested)
                    },
                    onEdit = { editor = it },
                    onExitRequested = { showExitConfirmation = true }
                )

                GroupInfoDestination.SearchMembers -> GroupMemberSearchScreen(
                    members = state.members,
                    onBack = { destination = GroupInfoDestination.Main },
                    onMemberClick = { member -> onEvent(GroupInfoUiEvent.MemberSelected(member.id)) }
                )

                GroupInfoDestination.QrCode -> GroupQrCodeScreen(
                    state = state,
                    onBack = { destination = GroupInfoDestination.Main }
                )
            }
        }

        editor?.let { currentEditor ->
            GroupInfoInputDialog(
                editor = currentEditor,
                enabled = !state.loading,
                onDismiss = { editor = null },
                onConfirm = { value ->
                    onEvent(currentEditor.onValueChanged(value))
                    onEvent(currentEditor.submitEvent)
                    editor = null
                }
            )
        }
        if (showExitConfirmation) {
            GroupInfoExitConfirmation(
                groupName = state.groupName,
                enabled = !state.loading,
                onDismiss = { showExitConfirmation = false },
                onConfirm = {
                    showExitConfirmation = false
                    onEvent(GroupInfoUiEvent.ExitGroupRequested)
                }
            )
        }
    }
}

@Composable
private fun GroupInfoMainScreen(
    state: GroupInfoUiState,
    onEvent: (GroupInfoUiEvent) -> Unit,
    onSearchMembers: () -> Unit,
    onOpenQrCode: () -> Unit,
    onEdit: (GroupInfoEditor) -> Unit,
    onExitRequested: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        FloatingWorkspaceTopAppBar(
            title = "聊天信息${state.memberCount}",
            onBack = { onEvent(GroupInfoUiEvent.BackRequested) },
            actions = {
                IconButton(onClick = onSearchMembers) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索成员")
                }
            }
        )
        GroupInfoStatus(state)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                GroupMemberPreviewGrid(
                    members = state.members,
                    enabled = !state.loading,
                    onMemberClick = { member -> onEvent(GroupInfoUiEvent.MemberSelected(member.id)) },
                    onMore = onSearchMembers,
                    onAdd = { onEvent(GroupInfoUiEvent.AddMemberRequested) }
                )
            }
            item {
                GroupInfoNavigationRow(
                    label = "群聊名称",
                    value = state.groupName,
                    onClick = {
                        onEdit(
                            GroupInfoEditor(
                                title = "修改群聊名称",
                                label = "群聊名称",
                                initialValue = state.groupName,
                                onValueChanged = GroupInfoUiEvent::GroupNameChanged,
                                submitEvent = GroupInfoUiEvent.RenameRequested
                            )
                        )
                    }
                )
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoNavigationRow(label = "群聊二维码", onClick = onOpenQrCode)
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoNavigationRow(
                    label = "群公告",
                    onClick = {
                        onEdit(
                            GroupInfoEditor(
                                title = "发布群公告",
                                label = "群公告",
                                initialValue = state.announcement,
                                multiline = true,
                                onValueChanged = GroupInfoUiEvent::AnnouncementChanged,
                                submitEvent = GroupInfoUiEvent.PublishAnnouncementRequested
                            )
                        )
                    }
                )
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoNavigationRow(
                    label = "备注",
                    value = state.remark,
                    onClick = {
                        onEdit(
                            GroupInfoEditor(
                                title = "修改群备注",
                                label = "备注",
                                initialValue = state.remark,
                                onValueChanged = GroupInfoUiEvent::RemarkChanged,
                                submitEvent = GroupInfoUiEvent.SaveRemarkRequested
                            )
                        )
                    }
                )
            }
            item { GroupInfoSectionGap() }
            item {
                GroupInfoSwitchRow(
                    label = "消息通知",
                    checked = !state.muted,
                    enabled = !state.loading,
                    onCheckedChange = { enabled -> onEvent(GroupInfoUiEvent.MutedChanged(!enabled)) }
                )
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoSwitchRow(
                    label = "置顶聊天",
                    checked = state.pinned,
                    enabled = !state.loading,
                    onCheckedChange = { onEvent(GroupInfoUiEvent.PinnedChanged(it)) }
                )
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoSwitchRow(
                    label = "保存到通讯录",
                    checked = state.savedToContacts,
                    enabled = !state.loading,
                    onCheckedChange = { onEvent(GroupInfoUiEvent.SavedToContactsChanged(it)) }
                )
            }
            item { GroupInfoSectionGap() }
            item {
                GroupInfoNavigationRow(
                    label = "我在群里的昵称",
                    value = state.myNickname,
                    onClick = {
                        onEdit(
                            GroupInfoEditor(
                                title = "修改群昵称",
                                label = "我在群里的昵称",
                                initialValue = state.myNickname,
                                onValueChanged = GroupInfoUiEvent::MyNicknameChanged,
                                submitEvent = GroupInfoUiEvent.SaveMyNicknameRequested
                            )
                        )
                    }
                )
            }
            item { GroupInfoDivider() }
            item {
                GroupInfoSwitchRow(
                    label = "显示群成员昵称",
                    checked = state.memberNicknamesVisible,
                    enabled = !state.loading,
                    onCheckedChange = { onEvent(GroupInfoUiEvent.MemberNicknamesVisibleChanged(it)) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Button(
                    onClick = onExitRequested,
                    enabled = !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("退出群聊", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMemberPreviewGrid(
    members: List<GroupInfoMemberUiState>,
    enabled: Boolean,
    onMemberClick: (GroupInfoMemberUiState) -> Unit,
    onMore: () -> Unit,
    onAdd: () -> Unit
) {
    val cells = remember(members.size) { groupInfoMemberPreviewCells(members.size) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GroupInfoGridColumns),
        modifier = Modifier
            .fillMaxWidth()
            .height(GroupInfoGridHeight)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(cells) { cell ->
            when (cell) {
                is GroupInfoMemberPreviewCell.Member -> members.getOrNull(cell.index)?.let { member ->
                    GroupMemberGridCard(member, enabled, onMemberClick)
                }
                GroupInfoMemberPreviewCell.Empty -> Spacer(Modifier.size(GroupInfoMemberLabelWidth, 80.dp))
                GroupInfoMemberPreviewCell.More -> GroupMemberActionCard(
                    label = "查看更多",
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                    enabled = enabled,
                    onClick = onMore
                )
                GroupInfoMemberPreviewCell.Add -> GroupMemberActionCard(
                    label = "邀请成员",
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    enabled = enabled,
                    onClick = onAdd
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMemberGridCard(
    member: GroupInfoMemberUiState,
    enabled: Boolean,
    onClick: (GroupInfoMemberUiState) -> Unit
) {
    val avatarBitmap = rememberAsyncAvatarBitmap(member.avatarUrl, member.id)
    Card(
        onClick = { onClick(member) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(GroupInfoAvatarSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(member.avatarColor)),
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
                    Text(member.initials.take(2), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = member.displayName,
                modifier = Modifier
                    .width(GroupInfoMemberLabelWidth)
                    .basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GroupMemberActionCard(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(GroupInfoAvatarSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(
                text = label,
                modifier = Modifier.width(GroupInfoMemberLabelWidth),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupInfoNavigationRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开$label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupInfoSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun GroupInfoDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun GroupInfoSectionGap() {
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun GroupInfoStatus(state: GroupInfoUiState) {
    val message = state.error ?: state.status ?: return
    val isError = state.error != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = message,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GroupMemberSearchScreen(
    members: List<GroupInfoMemberUiState>,
    onBack: () -> Unit,
    onMemberClick: (GroupInfoMemberUiState) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visibleMembers = remember(members, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) members else members.filter { it.displayName.contains(normalized, ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(title = "搜索成员", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("搜索成员") },
            singleLine = true
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (visibleMembers.isEmpty()) {
                item {
                    Text(
                        text = "未找到成员",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(visibleMembers, key = GroupInfoMemberUiState::id) { member ->
                GroupMemberSearchRow(member = member, onClick = { onMemberClick(member) })
                GroupInfoDivider()
            }
        }
    }
}

@Composable
private fun GroupMemberSearchRow(member: GroupInfoMemberUiState, onClick: () -> Unit) {
    val avatarBitmap = rememberAsyncAvatarBitmap(member.avatarUrl, member.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(member.avatarColor)),
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
                Text(member.initials.take(2), color = Color.White)
            }
        }
        Text(member.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun GroupQrCodeScreen(state: GroupInfoUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remoteBitmap = rememberAsyncAvatarBitmap(
        state.qrCodeImageUrl?.takeUnless { it.startsWith("data:image/") },
        "group-qr-${state.groupName}"
    )
    val dataBitmap = remember(state.qrCodeImageUrl) { decodeGroupQrDataImage(state.qrCodeImageUrl) }
    val contentBitmap = remember(state.qrCodeContent) {
        state.qrCodeContent?.takeIf(String::isNotBlank)?.let { content ->
            runCatching { createGroupQrCodeBitmap(content) }.getOrNull()
        }
    }
    val qrBitmap = remoteBitmap ?: dataBitmap ?: contentBitmap
    var saveStatus by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(title = "群聊二维码", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GroupQrAvatar(state)
            Text(state.groupName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "群聊二维码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(
                        state.error,
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text("暂无可展示的二维码", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.qrCodeImageUrl != null && remoteBitmap == null && contentBitmap != null) {
                Text(
                    "二维码图片暂不可用，当前根据接口返回内容生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            saveStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val bitmap = qrBitmap ?: return@Button
                    scope.launch {
                        saveError = null
                        saveStatus = null
                        runCatching {
                            withContext(Dispatchers.IO) {
                                saveGroupQrCodeBitmap(context.applicationContext, bitmap, state.groupName)
                            }
                        }.onSuccess { fileName ->
                            saveStatus = "已保存到相册：$fileName"
                        }.onFailure { error ->
                            saveError = error.message ?: "二维码保存失败"
                        }
                    }
                },
                enabled = qrBitmap != null && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存图片")
            }
        }
    }
}

@Composable
private fun GroupQrAvatar(state: GroupInfoUiState) {
    val avatarBitmap = rememberAsyncAvatarBitmap(state.groupAvatarUrl, "group-qr-avatar-${state.groupName}")
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(state.groupAvatarColor).takeIf { state.groupAvatarColor != 0 } ?: MaterialTheme.colorScheme.primaryContainer),
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
                state.groupAvatarInitials.ifBlank { state.groupName.take(2) },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GroupInfoInputDialog(
    editor: GroupInfoEditor,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(editor) { mutableStateOf(editor.initialValue) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(editor.title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(editor.label) },
                    singleLine = !editor.multiline,
                    minLines = if (editor.multiline) 4 else 1,
                    enabled = enabled
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
                    Button(
                        onClick = { onConfirm(value.trim()) },
                        enabled = enabled && value.isNotBlank()
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoExitConfirmation(
    groupName: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("退出群聊", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (groupName.isBlank()) "确定退出当前群聊吗？" else "确定退出“$groupName”吗？退出后将不再接收该群消息。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
                    Button(
                        onClick = onConfirm,
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) { Text("确认退出", color = Color.White) }
                }
            }
        }
    }
}
