package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
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
    var confirmed by remember(kind) { mutableStateOf(false) }

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
            Text(kind.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Column(
            Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(8.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("当前账号：${route?.weChatId ?: "未选择账号"}", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp)
            Text("当前会话：${conversationId ?: "未选择 SCRM 会话"}", color = OverlayTokens.panelSecondaryText, fontSize = 11.sp)
            Text(if (kind == ScrmComposerKind.CardTemplates) "只读加载，不会发送消息" else "仅 UI 预览，发送接口尚未接入", color = Color(0xFF4E7A55), fontSize = 10.sp)
        }
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
                templates.forEach { item ->
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFFF7F8F9), RoundedCornerShape(6.dp)).padding(9.dp)
                    ) {
                        Text(item.title ?: item.kind ?: "未命名模板", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        item.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 10.sp, color = OverlayTokens.panelSecondaryText) }
                    }
                }
            }
        }
        if (kind != ScrmComposerKind.CardTemplates) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text("我已确认发送目标和内容", fontSize = 11.sp, color = OverlayTokens.panelSecondaryText)
            }
            Button(enabled = confirmed, onClick = { status = assemble() }) { Text("生成请求预览") }
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
