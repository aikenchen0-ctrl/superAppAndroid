package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.scrm.ScrmCardTemplateItem
import com.paifa.univerge.accessibility.scrm.ScrmCardTemplateQuery
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmMessageOperationApi
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.toScrmContactsPanelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ScrmComposerKind(val title: String) {
    Emoji("收藏表情发送"),
    WeAppCard("小程序卡片"),
    CardTemplates("卡片模板"),
    BatchText("批量发送")
}

/**
 * UI：More 菜单的 SCRM 表情、小程序卡片、卡片模板和批量发送统一使用全屏悬浮工作区。
 * 接口：卡片模板保留只读 GET；其余发送项仍只组装预览请求，不改变既有业务调用。
 * 测试流程：从 More 依次打开四个页面，确认顶部 30dp 由共享工具栏处理，左上返回回到 More，模板加载与请求预览行为保持原样。
 */
@Composable
internal fun ScrmMessageComposerPanel(
    kind: ScrmComposerKind,
    route: ScrmFloatingAccountRoute?,
    conversationId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var emojiMd5 by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var pagePath by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var thumb by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    var status by remember(kind) { mutableStateOf<String?>(null) }
    var loading by remember(kind) { mutableStateOf(false) }
    var templates by remember(kind) { mutableStateOf<List<ScrmCardTemplateItem>>(emptyList()) }
    var confirmed by remember(kind) { mutableStateOf(false) }

    fun assemble(): String {
        val safeRoute = route ?: return "无法组装：缺少当前账号 SCRM 路由"
        val safeConversationId = conversationId?.takeIf(String::isNotBlank)
            ?: return "无法组装：请先进入一个 SCRM 会话"
        return runCatching {
            when (kind) {
                ScrmComposerKind.Emoji -> buildScrmEmojiSendRequest(safeRoute, safeConversationId, emojiMd5)
                ScrmComposerKind.WeAppCard -> buildScrmWeAppCardRequest(
                    safeRoute, safeConversationId, appId, title, pagePath, url, thumb
                )
                ScrmComposerKind.BatchText -> buildScrmBatchTextPreview(
                    safeRoute, content, labels.split(',').map(String::trim)
                )
                ScrmComposerKind.CardTemplates -> return "卡片模板为只读加载，不需要组装发送请求"
            }
            "请求已组装，未发送"
        }.getOrElse { error -> "无法组装：${error.message ?: "参数不完整"}" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(title = kind.title, onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("当前账号：${route?.weChatId ?: "未选择账号"}") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("当前会话：${conversationId ?: "未选择 SCRM 会话"}")
                                Text(
                                    text = if (kind == ScrmComposerKind.CardTemplates) {
                                        "只读加载，不会发送消息"
                                    } else {
                                        "仅生成请求预览，不会发起发送"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
            when (kind) {
                ScrmComposerKind.Emoji -> item {
                    ComposerField(emojiMd5, { emojiMd5 = it }, "表情 MD5")
                }
                ScrmComposerKind.WeAppCard -> {
                    item { ComposerField(appId, { appId = it }, "App ID") }
                    item { ComposerField(title, { title = it }, "标题") }
                    item { ComposerField(pagePath, { pagePath = it }, "页面路径") }
                    item { ComposerField(url, { url = it }, "真实链接 URL") }
                    item { ComposerField(thumb, { thumb = it }, "缩略图 URL") }
                }
                ScrmComposerKind.BatchText -> {
                    item { ComposerField(content, { content = it }, "消息内容") }
                    item { ComposerField(labels, { labels = it }, "标签名称，逗号分隔") }
                    item {
                        Text(
                            text = "按筛选发送，预计影响 1 人，最大数量固定为 1。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ScrmComposerKind.CardTemplates -> {
                    item {
                        Text(
                            text = "仅读取模板数据，不会发送消息。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Button(
                            enabled = !loading && route != null && !conversationId.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val safeRoute = route ?: return@Button
                                val safeConversationId = conversationId ?: return@Button
                                loading = true
                                status = "正在加载卡片模板"
                                // 接口：More -> 卡片模板 -> 加载，只调用只读 getCardTemplates。
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val session = ScrmSettingsManager(context.applicationContext)
                                                .loadSelectedSessionOrBootstrap()
                                            val operationApi = session.messageApi as? ScrmMessageOperationApi
                                                ?: error("当前 SCRM 客户端不支持卡片模板读取")
                                            operationApi.getCardTemplates(
                                                ScrmCardTemplateQuery(
                                                    deviceUuid = safeRoute.deviceUuid,
                                                    weChatId = safeRoute.weChatId,
                                                    conversationId = safeConversationId
                                                )
                                            )
                                        }
                                    }.onSuccess { response ->
                                        templates = response.items
                                        status = if (response.success) {
                                            "已加载 ${response.items.size} 个模板"
                                        } else {
                                            "服务端未确认模板结果"
                                        }
                                    }.onFailure { error -> status = error.toScrmContactsPanelMessage() }
                                    loading = false
                                }
                            }
                        ) { Text(if (loading) "加载中" else "加载模板") }
                    }
                    items(templates) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text(item.title ?: item.kind ?: "未命名模板") },
                                supportingContent = item.description
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { description -> { Text(description) } }
                            )
                        }
                    }
                }
            }
            if (kind != ScrmComposerKind.CardTemplates) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text(
                            text = "我已确认发送目标和内容",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Button(
                        enabled = confirmed,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { status = assemble() }
                    ) { Text("生成请求预览") }
                }
            }
            status?.let { value ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(value) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}
