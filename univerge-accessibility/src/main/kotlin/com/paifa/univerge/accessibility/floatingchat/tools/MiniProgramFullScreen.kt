package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.message.buildScrmWeAppCardRequest
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmMessageOperationApi
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.toScrmContactsPanelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MiniProgramStatusBarHeightDp = 30

internal enum class MiniProgramFullScreenTab(val label: String) {
    Configure("配置"),
    Preview("预览")
}

/**
 * 对应 iOS MiniProgramShareCardView 的发送工作区，使用安卓 M3 组件而不是 iOS 卡片样式。
 * 测试流程：打开右侧微信小程序，填写四个字段，切换预览，点击发送并检查任务提交状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiniProgramFullScreen(
    route: ScrmFloatingAccountRoute?,
    conversationId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { MiniProgramFullScreenTab.entries.size })
    var appId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var pagePath by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var thumb by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submissionStatus by remember { mutableStateOf<String?>(null) }
    /** 对接 POST messages/weapp-card，只有用户点击发送按钮才会执行写操作。 */
    fun submitMiniProgramCard() {
        val safeRoute = route ?: run {
            submissionStatus = "无法发送：缺少当前账号 SCRM 路由"
            return
        }
        val safeConversationId = conversationId?.takeIf(String::isNotBlank) ?: run {
            submissionStatus = "无法发送：请先进入一个 SCRM 会话"
            return
        }
        val request = runCatching {
            buildScrmWeAppCardRequest(
                route = safeRoute,
                conversationId = safeConversationId,
                appId = appId.trim(),
                title = title.trim(),
                pagePath = pagePath.trim(),
                url = url.trim(),
                thumb = thumb.trim()
            )
        }.getOrElse { error ->
            submissionStatus = error.message ?: "小程序卡片参数不完整"
            return
        }
        submitting = true
        submissionStatus = "正在提交小程序卡片"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = ScrmSettingsManager(context.applicationContext)
                        .loadSelectedSessionOrBootstrap()
                    val operationApi = session.messageApi as? ScrmMessageOperationApi
                        ?: error("当前 SCRM 客户端不支持小程序卡片发送")
                    operationApi.sendWeAppCard(request)
                }
            }.onSuccess { result ->
                submissionStatus = if (result.success) {
                    "已提交小程序卡片任务：${result.taskId}"
                } else {
                    result.message ?: "服务端未确认小程序卡片任务"
                }
            }.onFailure { error ->
                submissionStatus = error.toScrmContactsPanelMessage()
            }
            submitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 共享 AppBar 提供 30dp 顶部安全区，防止小程序表单额外占据高度。
        FloatingWorkspaceTopAppBar(title = "微信小程序", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            MiniProgramFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (MiniProgramFullScreenTab.entries[page]) {
                MiniProgramFullScreenTab.Configure -> MiniProgramConfigurePage(
                    route = route,
                    conversationId = conversationId,
                    appId = appId,
                    onAppIdChanged = { appId = it },
                    title = title,
                    onTitleChanged = { title = it },
                    pagePath = pagePath,
                    onPagePathChanged = { pagePath = it },
                    url = url,
                    onUrlChanged = { url = it },
                    thumb = thumb,
                    onThumbChanged = { thumb = it },
                    submitting = submitting,
                    submissionStatus = submissionStatus,
                    onSubmit = ::submitMiniProgramCard
                )
                MiniProgramFullScreenTab.Preview -> MiniProgramPreviewPage(
                    appId = appId,
                    title = title,
                    pagePath = pagePath,
                    url = url,
                    thumb = thumb,
                    submissionStatus = submissionStatus
                )
            }
        }
    }
}

/** M3 配置列表对应 iOS 小程序卡片的 appId、标题、页面路径、真实链接和缩略图字段。 */
@Composable
private fun MiniProgramConfigurePage(
    route: ScrmFloatingAccountRoute?,
    conversationId: String?,
    appId: String,
    onAppIdChanged: (String) -> Unit,
    title: String,
    onTitleChanged: (String) -> Unit,
    pagePath: String,
    onPagePathChanged: (String) -> Unit,
    url: String,
    onUrlChanged: (String) -> Unit,
    thumb: String,
    onThumbChanged: (String) -> Unit,
    submitting: Boolean,
    submissionStatus: String?,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("发送目标", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    Text("账号：${route?.weChatId ?: "未选择"}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("会话：${conversationId ?: "未选择"}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("小程序卡片", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    OutlinedTextField(value = appId, onValueChange = onAppIdChanged, modifier = Modifier.fillMaxWidth(), label = { Text("App ID") }, singleLine = true)
                    OutlinedTextField(value = title, onValueChange = onTitleChanged, modifier = Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
                    OutlinedTextField(value = pagePath, onValueChange = onPagePathChanged, modifier = Modifier.fillMaxWidth(), label = { Text("页面路径") }, singleLine = true)
                    OutlinedTextField(value = url, onValueChange = onUrlChanged, modifier = Modifier.fillMaxWidth(), label = { Text("真实链接 URL") }, singleLine = true)
                    OutlinedTextField(value = thumb, onValueChange = onThumbChanged, modifier = Modifier.fillMaxWidth(), label = { Text("缩略图 URL") }, singleLine = true)
                    Button(onClick = onSubmit, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (submitting) "正在发送" else "发送小程序卡片")
                    }
                }
            }
        }
        submissionStatus?.let { status ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** M3 预览列表对应 iOS MiniProgramShareCardView 的标题、应用标识、页面路径、真实链接和缩略图信息。 */
@Composable
private fun MiniProgramPreviewPage(
    appId: String,
    title: String,
    pagePath: String,
    url: String,
    thumb: String,
    submissionStatus: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(title.ifBlank { "小程序标题" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    }
                    Text("App ID：${appId.ifBlank { "待填写" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("页面：${pagePath.ifBlank { "待填写" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("链接：${url.ifBlank { "待填写" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("缩略图：${thumb.ifBlank { "待填写" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        submissionStatus?.let { status -> item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
