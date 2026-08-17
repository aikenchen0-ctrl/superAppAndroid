package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.launch

internal enum class ToolbarWorkspaceMode {
    Search,
    Scan,
    AddFriend
}

/**
 * 顶部搜索、扫一扫和添加好友的全屏悬浮工作区。
 *
 * 复用已经挂载的 accessibility overlay 根视图，不创建 Dialog 或新 Window，避免 BadTokenException。
 * 测试流程：在未读总览和单账号会话分别点击搜索、扫一扫；确认页面自下向上进入，左上返回自上向下退出；
 * 在添加好友页用手机号和微信号各提交一次，确认请求进入现有 SCRM contactApi 分支。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolbarWorkspaceFullScreen(
    mode: ToolbarWorkspaceMode,
    messages: List<FloatingChatMessage>,
    onBack: () -> Unit,
    onRequestScan: () -> Unit,
    onOpenAddFriend: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onSubmitFriend: suspend (account: String, message: String) -> String
) {
    // 页面只负责内容，进出场由 UI组件 同一聊天根的 AnimatedVisibility 执行。
    fun close(afterExit: () -> Unit = onBack) = afterExit()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = when (mode) {
                ToolbarWorkspaceMode.Search -> "搜索聊天记录"
                ToolbarWorkspaceMode.Scan -> "扫一扫"
                ToolbarWorkspaceMode.AddFriend -> "添加好友"
            },
            onBack = onBack
        )
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                ToolbarWorkspaceMode.Search -> ToolbarMessageSearchContent(messages)
                ToolbarWorkspaceMode.Scan -> ToolbarScanActions(
                    onRequestScan = { close(onRequestScan) },
                    onOpenAddFriend = onOpenAddFriend,
                    onOpenCreateGroup = onOpenCreateGroup
                )
                ToolbarWorkspaceMode.AddFriend -> ToolbarAddFriendContent(onSubmitFriend)
            }
        }
    }
}

@Composable
private fun ToolbarMessageSearchContent(messages: List<FloatingChatMessage>) {
    var query by remember { mutableStateOf("") }
    val matchingMessages = remember(messages, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) emptyList() else messages.filter { message ->
            message.text.contains(keyword, ignoreCase = true) ||
                message.senderName.contains(keyword, ignoreCase = true) ||
                message.detail.orEmpty().contains(keyword, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("关键词") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
        }
        if (query.isBlank()) {
            item {
                Text(
                    text = "输入关键词搜索当前聊天记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else if (matchingMessages.isEmpty()) {
            item {
                Text(
                    text = "没有匹配的聊天记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            items(matchingMessages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = {
                            Text(message.senderName.ifBlank { "消息" }, fontWeight = FontWeight.Normal)
                        },
                        supportingContent = {
                            Text(
                                text = message.text.ifBlank { message.detail.orEmpty() },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        overlineContent = { Text(message.time, fontWeight = FontWeight.Normal) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarScanActions(
    onRequestScan: () -> Unit,
    onOpenAddFriend: () -> Unit,
    onOpenCreateGroup: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "扫码与好友",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onRequestScan),
                    leadingContent = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                    headlineContent = { Text("扫一扫", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("扫描二维码或条码", fontWeight = FontWeight.Normal) }
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAddFriend),
                    leadingContent = { Icon(Icons.Filled.PersonAddAlt1, contentDescription = null) },
                    headlineContent = { Text("添加好友", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("通过微信号或手机号发送申请", fontWeight = FontWeight.Normal) }
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenCreateGroup),
                    leadingContent = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    headlineContent = { Text("创建群聊", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("选择多个好友发起群聊", fontWeight = FontWeight.Normal) }
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onRequestScan),
                    leadingContent = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    headlineContent = { Text("扫码加群", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("扫描群二维码加入群聊", fontWeight = FontWeight.Normal) }
                )
            }
        }
    }
}

@Composable
private fun ToolbarAddFriendContent(
    onSubmitFriend: suspend (account: String, message: String) -> String
) {
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf("") }
    var verifyMessage by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "好友申请",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("微信号或手机号") },
                singleLine = true,
                enabled = !submitting
            )
        }
        item {
            OutlinedTextField(
                value = verifyMessage,
                onValueChange = { verifyMessage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("验证消息（可选）") },
                minLines = 3,
                enabled = !submitting
            )
        }
        item {
            Button(
                onClick = {
                    if (account.isBlank()) {
                        status = "请输入微信号或手机号"
                        return@Button
                    }
                    scope.launch {
                        submitting = true
                        status = runCatching { onSubmitFriend(account, verifyMessage) }
                            .getOrElse { error -> error.message ?: "好友申请请求失败" }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (submitting) "正在提交" else "发送好友申请", fontWeight = FontWeight.Normal)
            }
        }
        status?.let { value ->
            item {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
