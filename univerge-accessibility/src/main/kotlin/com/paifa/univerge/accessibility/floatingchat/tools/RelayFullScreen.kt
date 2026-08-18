package com.paifa.univerge.accessibility.floatingchat.tools

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.core.model.FloatingChatMessage
import kotlinx.coroutines.launch

internal data class RelayDraft(
    val title: String,
    val memo: String,
    val content: String,
    val msgSvrId: Long = 0L
)

/**
 * iOS 群接龙编辑器的 Android Material 3 全屏实现。
 *
 * UI 复用已有 accessibility 悬浮根视图，不创建 Dialog 或 Window，避免 BadTokenException。
 * 测试流程：在群聊点击右侧“接龙消息”，分别切换“新接龙/续接接龙”，提交后确认 API 任务结果；
 * 点击左上返回时，页面实体从顶部方向退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelayFullScreen(
    existingRelays: List<FloatingChatMessage>,
    onBack: () -> Unit,
    onSubmit: suspend (RelayDraft) -> String
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        FloatingWorkspaceTopAppBar(
            title = "群接龙",
            onBack = onBack
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            listOf("新接龙", "续接接龙").forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                RelayDraftPage(onSubmit = onSubmit)
            } else {
                RelayContinuationPage(existingRelays = existingRelays, onSubmit = onSubmit)
            }
        }
    }
}

@Composable
private fun RelayDraftPage(onSubmit: suspend (RelayDraft) -> String) {
    var title by remember { mutableStateOf("接龙") }
    var memo by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    RelayForm(
        title = title,
        onTitleChange = { title = it },
        memo = memo,
        onMemoChange = { memo = it },
        content = content,
        onContentChange = { content = it },
        submitLabel = "发起接龙",
        buildDraft = { RelayDraft(title.trim().ifBlank { "接龙" }, memo.trim(), content.trim()) },
        onSubmit = onSubmit
    )
}

@Composable
private fun RelayContinuationPage(
    existingRelays: List<FloatingChatMessage>,
    onSubmit: suspend (RelayDraft) -> String
) {
    var selectedRelay by remember { mutableStateOf<FloatingChatMessage?>(existingRelays.firstOrNull()) }
    var content by remember { mutableStateOf("") }
    var msgSvrIdText by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("选择已有接龙", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (existingRelays.isEmpty()) {
            item { Text("当前群聊没有可续接的接龙消息", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(existingRelays, key = { it.id }) { relay ->
                Card(Modifier.fillMaxWidth().clickable { selectedRelay = relay }) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.FormatListNumbered, contentDescription = null) },
                        headlineContent = { Text(relay.text.ifBlank { "接龙" }, fontWeight = FontWeight.Normal) },
                        supportingContent = { Text(relay.detail.orEmpty().ifBlank { "选择后续接" }, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = msgSvrIdText, onValueChange = { msgSvrIdText = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(), label = { Text("接龙消息服务端 ID") }, singleLine = true,
                supportingText = { Text("续接必须填写原接龙的 MsgSvrId") }, enabled = !submitting
            )
        }
        item {
            OutlinedTextField(
                value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("我的接龙信息") }, minLines = 3, enabled = !submitting
            )
        }
        item {
            Button(
                onClick = {
                    val msgSvrId = msgSvrIdText.toLongOrNull()
                    if (selectedRelay == null || msgSvrId == null || msgSvrId <= 0L || content.isBlank()) {
                        status = "请选择接龙，并填写有效服务端 ID 和接龙内容"
                        return@Button
                    }
                    scope.launch {
                        submitting = true
                        status = runCatching {
                            onSubmit(RelayDraft(selectedRelay?.text.orEmpty().ifBlank { "接龙" }, "", content.trim(), msgSvrId))
                        }.getOrElse { it.message ?: "接龙提交失败" }
                        submitting = false
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !submitting
            ) {
                if (submitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text(if (submitting) "正在提交" else "续接接龙", fontWeight = FontWeight.Normal)
            }
        }
        status?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) } }
    }
}

@Composable
private fun RelayForm(
    title: String,
    onTitleChange: (String) -> Unit,
    memo: String,
    onMemoChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    submitLabel: String,
    buildDraft: () -> RelayDraft,
    onSubmit: suspend (RelayDraft) -> String
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("发起群接龙", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp)) }
        item { OutlinedTextField(title, onTitleChange, Modifier.fillMaxWidth(), label = { Text("接龙标题") }, singleLine = true, enabled = !submitting) }
        item { OutlinedTextField(memo, onMemoChange, Modifier.fillMaxWidth(), label = { Text("接龙备注（可选）") }, minLines = 2, enabled = !submitting) }
        item { OutlinedTextField(content, onContentChange, Modifier.fillMaxWidth(), label = { Text("我的接龙信息") }, minLines = 3, enabled = !submitting) }
        item {
            Button(onClick = {
                if (content.isBlank()) { status = "请先输入接龙信息"; return@Button }
                scope.launch {
                    submitting = true
                    status = runCatching { onSubmit(buildDraft()) }.getOrElse { it.message ?: "接龙提交失败" }
                    submitting = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !submitting) {
                if (submitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text(if (submitting) "正在提交" else submitLabel, fontWeight = FontWeight.Normal)
            }
        }
        status?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) } }
    }
}
