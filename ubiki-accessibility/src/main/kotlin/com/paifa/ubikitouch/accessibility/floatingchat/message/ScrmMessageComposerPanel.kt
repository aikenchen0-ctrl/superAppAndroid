package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.scrm.ScrmCardTemplateItem
import com.paifa.ubikitouch.accessibility.scrm.ScrmCardTemplateQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessageOperationApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.toScrmContactsPanelMessage
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
 * UI entry: More panel -> SCRM message tool. Card templates use GET; every send action only assembles.
 * Manual test: choose a current SCRM conversation, fill the form, confirm the preview, and verify no POST task is emitted.
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
    var thumb by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    var status by remember(kind) { mutableStateOf<String?>(null) }
    var loading by remember(kind) { mutableStateOf(false) }
    var templates by remember(kind) { mutableStateOf<List<ScrmCardTemplateItem>>(emptyList()) }

    fun assemble(): String {
        val safeRoute = route ?: return "无法组装：缺少当前账号 SCRM 路由"
        val safeConversationId = conversationId?.takeIf(String::isNotBlank)
            ?: return "无法组装：请先进入一个 SCRM 会话"
        return runCatching {
            when (kind) {
                ScrmComposerKind.Emoji -> buildScrmEmojiSendRequest(safeRoute, safeConversationId, emojiMd5)
                ScrmComposerKind.WeAppCard -> buildScrmWeAppCardRequest(
                    safeRoute, safeConversationId, appId, title, pagePath, thumb
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
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(kind.title)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text("当前会话：${conversationId ?: "未选择 SCRM 会话"}")
        when (kind) {
            ScrmComposerKind.Emoji -> ComposerField(emojiMd5, { emojiMd5 = it }, "表情 MD5")
            ScrmComposerKind.WeAppCard -> {
                ComposerField(appId, { appId = it }, "App ID")
                ComposerField(title, { title = it }, "标题")
                ComposerField(pagePath, { pagePath = it }, "页面路径")
                ComposerField(thumb, { thumb = it }, "缩略图 URL")
            }
            ScrmComposerKind.BatchText -> {
                ComposerField(content, { content = it }, "消息内容")
                ComposerField(labels, { labels = it }, "标签名称，逗号分隔")
                Text("按筛选发送，预计影响 1 人，最大数量固定为 1。")
            }
            ScrmComposerKind.CardTemplates -> {
                Text("仅读取模板数据，不会发送消息。")
                Button(
                    enabled = !loading && route != null && !conversationId.isNullOrBlank(),
                    onClick = {
                        val safeRoute = route ?: return@Button
                        val safeConversationId = conversationId ?: return@Button
                        loading = true
                        status = "正在加载卡片模板"
                        // UI test: More -> Card templates -> Load. This is the read-only getCardTemplates call.
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
                                status = if (response.success) "已加载 ${response.items.size} 个模板" else "服务端未确认模板结果"
                            }.onFailure { error -> status = error.toScrmContactsPanelMessage() }
                            loading = false
                        }
                    }
                ) { Text(if (loading) "加载中" else "加载模板") }
                templates.forEach { item -> Text("${item.title ?: item.kind ?: "未命名模板"} ${item.description.orEmpty()}") }
            }
        }
        if (kind != ScrmComposerKind.CardTemplates) {
            Text("此操作会产生发送任务，确认仅组装参数，不会调用接口。")
            Button(onClick = { status = assemble() }) { Text("确认并组装") }
        }
        status?.let { value -> Text(value) }
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
