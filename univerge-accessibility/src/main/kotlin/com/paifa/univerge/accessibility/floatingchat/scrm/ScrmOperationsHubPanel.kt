package com.paifa.univerge.accessibility.floatingchat.scrm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.ScrmTaskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ScrmHubTab(val label: String, val icon: ImageVector) {
    Messages("消息", Icons.Filled.ChatBubbleOutline),
    Contacts("联系人", Icons.Filled.PersonSearch),
    Groups("群管理", Icons.Filled.Groups),
    Moments("朋友圈", Icons.Filled.Visibility)
}

private enum class ScrmUiStatus(val label: String, val color: Color, val icon: ImageVector) {
    Ready("已接入", Color(0xFF2B8A3E), Icons.Filled.CheckCircle),
    Preview("预览", Color(0xFFB26A00), Icons.Filled.Visibility),
    Planned("待接入", OverlayTokens.panelSecondaryText, Icons.Filled.Schedule)
}

private data class ScrmHubAction(
    val title: String,
    val detail: String,
    val status: ScrmUiStatus,
    val key: String
)

@Composable
internal fun ScrmOperationsHubPanel(
    route: ScrmFloatingAccountRoute?,
    conversationId: String?,
    onOpenPanel: (BottomPanelMode) -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableStateOf(ScrmHubTab.Messages) }
    var selectedAction by remember { mutableStateOf<ScrmHubAction?>(null) }
    val actions = remember(tab) { scrmHubActions(tab) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
    ) {
        // UI：复用右侧全屏工作区工具栏，30dp 状态区由 AppBar 内边距承载。
        // 测试流程：从功能栏打开 SCRM运营，点击左上返回并确认根动画结束后回到聊天页。
        FloatingWorkspaceTopAppBar(title = "SCRM运营", onBack = onClose)
        Text(
            text = route?.weChatId ?: "未选择账号",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(if (route == null) Color(0xFFFFF4E5) else Color(0xFFF0F6EC), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (route == null) "当前没有可用 SCRM 路由，请先在设置中选择设备和微信账号" else "当前路由可用，写操作仍需人工确认",
                color = if (route == null) Color(0xFF9A6200) else Color(0xFF4E7A55),
                fontSize = 10.sp
            )
        }

        ScrmAcceptanceSummary()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(Color(0xFFE9EFF1), RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ScrmHubTab.values().forEach { item ->
                val selected = item == tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(if (selected) OverlayTokens.bottomComposerSurface else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            tab = item
                            selectedAction = null
                        }
                        .semantics { contentDescription = "切换到${item.label}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (selected) OverlayTokens.accent else OverlayTokens.panelSecondaryText)
                    Spacer(Modifier.size(4.dp))
                    Text(item.label, fontSize = 11.sp, color = if (selected) OverlayTokens.panelPrimaryText else OverlayTokens.panelSecondaryText, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item {
                Text(
                    text = "当前会话${conversationId?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    color = OverlayTokens.panelSecondaryText,
                    fontSize = 11.sp
                )
            }
            items(actions, key = { it.key }) { action ->
                ScrmHubActionRow(
                    action = action,
                    expanded = selectedAction?.key == action.key,
                    route = route,
                    conversationId = conversationId,
                    onOpenPanel = onOpenPanel,
                    canOpenPanel = route != null && !conversationId.isNullOrBlank()
                ) {
                    selectedAction = if (selectedAction?.key == action.key) null else action
                }
            }
        }
    }
}

@Composable
private fun ScrmAcceptanceSummary() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color(0xFFF5F7F6),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("接口验收状态", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("本地测试通过 ≠ 真实服务已验收", color = Color(0xFF9A6200), fontSize = 10.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AcceptanceChip("本地测试", "已通过", Color(0xFF2B8A3E), Modifier.weight(1f))
                AcceptanceChip("真实服务", "待验收", Color(0xFFB26A00), Modifier.weight(1f))
                AcceptanceChip("写操作 UI", "仅预览", Color(0xFF6B7280), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AcceptanceChip(label: String, state: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(color.copy(alpha = 0.10f), RoundedCornerShape(6.dp)).padding(6.dp)) {
        Text(label, color = OverlayTokens.panelSecondaryText, fontSize = 9.sp, maxLines = 1)
        Text(state, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ScrmHubActionRow(
    action: ScrmHubAction,
    expanded: Boolean,
    route: ScrmFloatingAccountRoute?,
    conversationId: String?,
    onOpenPanel: (BottomPanelMode) -> Unit,
    canOpenPanel: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OverlayTokens.bottomComposerSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(action.title, color = OverlayTokens.panelPrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(action.detail, color = OverlayTokens.panelSecondaryText, fontSize = 11.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(action.status.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = action.status.color)
                Spacer(Modifier.size(4.dp))
                Text(action.status.label, color = action.status.color, fontSize = 10.sp)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(12.dp), tint = OverlayTokens.panelSecondaryText)
            }
        }
        if (expanded) {
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFFF1F5F3), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ScrmHubActionPreview(action, onOpenPanel, canOpenPanel, route, conversationId)
                }
            }
        }
    }
}

@Composable
private fun ScrmHubActionPreview(
    action: ScrmHubAction,
    onOpenPanel: (BottomPanelMode) -> Unit,
    canOpenPanel: Boolean,
    route: ScrmFloatingAccountRoute?,
    conversationId: String?
) {
    if (action.key == "message.conversation-operations") {
        com.paifa.univerge.accessibility.floatingchat.message.ScrmConversationOperationPreview(route, conversationId)
        return
    }
    var value by remember(action.key) { mutableStateOf("") }
    var checked by remember(action.key) { mutableStateOf(false) }
    Text("操作预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    when {
        action.key == "message.payment-status" -> PaymentStatusCenterPreview(route, conversationId)
        action.key == "message.forward" -> OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("目标会话 ID") },
            singleLine = true
        )
        action.key == "message.batch" || action.key == "contact.labels" -> OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (action.key == "message.batch") "搜索联系人或标签" else "标签名称，用逗号分隔") },
            singleLine = true
        )
        action.key.startsWith("contact.profile") || action.key == "contact.profile-edit" -> OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注或画像内容") },
            minLines = 2,
            maxLines = 3
        )
        action.key.startsWith("group.") || action.key.startsWith("moments.") -> Text(
            "已准备筛选条件、目标预览和影响范围区域，接入接口后在这里加载真实数据。",
            color = OverlayTokens.panelSecondaryText,
            fontSize = 11.sp
        )
        else -> Text(
            "已准备 UI 状态和参数展示，接口接入后将在此处显示确认、执行中、成功或失败结果。",
            color = OverlayTokens.panelSecondaryText,
            fontSize = 11.sp
        )
    }
    ScrmCheckboxRow(
        checked = checked,
        onCheckedChange = { checked = it },
        label = "我已确认当前目标和影响范围"
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        existingPanelFor(action.key)?.let { panel ->
            androidx.compose.material3.TextButton(enabled = canOpenPanel, onClick = { onOpenPanel(panel) }) {
                Text("打开工具", fontSize = 11.sp)
            }
        }
        Button(
            onClick = { },
            enabled = checked,
            colors = ButtonDefaults.buttonColors(containerColor = OverlayTokens.accent),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        ) { Text("生成请求预览", fontSize = 11.sp) }
    }
}

@Composable
private fun PaymentStatusCenterPreview(route: ScrmFloatingAccountRoute?, conversationId: String?) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loading by remember(route) { mutableStateOf(false) }
    var status by remember(route) { mutableStateOf<String?>(null) }
    var tasks by remember(route) { mutableStateOf<List<ScrmTaskResult>>(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("支付状态中心", color = OverlayTokens.panelPrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("账号：${route?.weChatId ?: "未选择"}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        Text("会话：${conversationId ?: "未选择"}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        Text("红包详情 / 红包状态 / 转账收款 / 钱包余额", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        Button(
            enabled = route != null && !loading,
            onClick = {
                val safeRoute = route ?: return@Button
                loading = true
                status = "正在读取最近支付任务"
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ScrmSettingsManager(context.applicationContext)
                                .loadSelectedSessionOrBootstrap()
                                .taskApi
                                .getRecentTasks(safeRoute.deviceUuid, count = 20)
                                .items
                                .orEmpty()
                        }
                    }.onSuccess { items ->
                        val paymentTasks = items.filter { item ->
                            val marker = listOfNotNull(item.resultCode, item.message).joinToString(" ").lowercase()
                            marker.contains("payment") || marker.contains("红包") || marker.contains("转账") || marker.contains("wallet")
                        }
                        tasks = paymentTasks
                        status = "已读取 ${paymentTasks.size} 条支付任务"
                    }.onFailure { error ->
                        status = "读取失败：${error.message ?: "未知错误"}"
                    }
                    loading = false
                }
            }
        ) { Text(if (loading) "读取中" else "刷新最近支付任务", fontSize = 10.sp) }
        status?.let { Text(it, color = Color(0xFF9A6200), fontSize = 10.sp) }
        tasks.take(5).forEach { task ->
            Text(
                "#${task.taskId} · ${task.status ?: "unknown"} · ${task.message ?: task.resultCode ?: "无说明"}",
                color = OverlayTokens.panelSecondaryText,
                fontSize = 10.sp,
                maxLines = 2
            )
        }
    }
}

private fun existingPanelFor(actionKey: String): BottomPanelMode? = when (actionKey) {
    "message.emoji" -> BottomPanelMode.ScrmEmoji
    "message.weapp-card" -> BottomPanelMode.ScrmWeAppCard
    "message.card-template" -> BottomPanelMode.ScrmCardTemplates
    "message.batch" -> BottomPanelMode.ScrmBatchSend
    "contact.profile",
    "contact.labels",
    "contact.moments-permission",
    "contact.profile-edit",
    "contact.refresh" -> BottomPanelMode.Contacts
    "moments.visibility",
    "moments.interactions",
    "moments.refresh",
    "moments.batch-plan" -> BottomPanelMode.Moments
    "moments.material-publish" -> BottomPanelMode.MomentMaterials
    else -> null
}

@Composable
private fun ScrmCheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
    }
}

private fun scrmHubActions(tab: ScrmHubTab): List<ScrmHubAction> = when (tab) {
    ScrmHubTab.Messages -> listOf(
        ScrmHubAction("转发消息", "选择目标会话并预览内容", ScrmUiStatus.Preview, "message.forward"),
        ScrmHubAction("撤回消息", "显示消息 ID 与时间", ScrmUiStatus.Preview, "message.revoke"),
        ScrmHubAction("语音转文字", "仅对语音消息启用", ScrmUiStatus.Preview, "message.transcribe"),
        ScrmHubAction("消息详情", "补拉服务端消息信息", ScrmUiStatus.Preview, "message.detail"),
        ScrmHubAction("支付状态中心", "红包、转账、钱包任务状态", ScrmUiStatus.Preview, "message.payment-status"),
        ScrmHubAction("会话同步与本地记录", "未读、历史、消息 ID 与本地记录清理", ScrmUiStatus.Preview, "message.conversation-operations"),
        ScrmHubAction("媒体下载", "先获取短期访问权限", ScrmUiStatus.Planned, "message.media"),
        ScrmHubAction("收藏表情", "填写表情 MD5 后预览发送", ScrmUiStatus.Preview, "message.emoji"),
        ScrmHubAction("小程序卡片", "填写 App ID、路径和缩略图", ScrmUiStatus.Preview, "message.weapp-card"),
        ScrmHubAction("卡片模板", "读取当前账号可用模板", ScrmUiStatus.Ready, "message.card-template"),
        ScrmHubAction("批量发送", "先预览命中会话和数量", ScrmUiStatus.Preview, "message.batch")
    )
    ScrmHubTab.Contacts -> listOf(
        ScrmHubAction("客户画像", "等级、来源、备注和标签", ScrmUiStatus.Preview, "contact.profile"),
        ScrmHubAction("标签管理", "创建、同步、删除和批量打标", ScrmUiStatus.Preview, "contact.labels"),
        ScrmHubAction("朋友圈权限", "控制好友可见范围", ScrmUiStatus.Planned, "contact.moments-permission"),
        ScrmHubAction("资料编辑", "备注、描述和电话", ScrmUiStatus.Planned, "contact.profile-edit"),
        ScrmHubAction("刷新资料", "重新拉取好友资料快照", ScrmUiStatus.Planned, "contact.refresh")
    )
    ScrmHubTab.Groups -> listOf(
        ScrmHubAction("筛选建群", "按联系人条件创建群聊", ScrmUiStatus.Preview, "group.create-by-filter"),
        ScrmHubAction("成员批量操作", "按筛选邀请或移出成员", ScrmUiStatus.Preview, "group.members-by-filter"),
        ScrmHubAction("管理员与群主", "调整群权限和群主", ScrmUiStatus.Preview, "group.roles"),
        ScrmHubAction("群设置", "免打扰、置顶、通讯录和备注", ScrmUiStatus.Preview, "group.settings"),
        ScrmHubAction("群内身份", "设置我的群昵称和成员显示", ScrmUiStatus.Preview, "group.identity"),
        ScrmHubAction("邀请审批", "查看并处理入群邀请", ScrmUiStatus.Planned, "group.invitations")
    )
    ScrmHubTab.Moments -> listOf(
        ScrmHubAction("朋友圈权限", "设置可见范围和置顶", ScrmUiStatus.Planned, "moments.visibility"),
        ScrmHubAction("互动消息", "未读、已读和主动拉取", ScrmUiStatus.Planned, "moments.interactions"),
        ScrmHubAction("删除与补拉", "删除动态或刷新好友动态", ScrmUiStatus.Planned, "moments.refresh"),
        ScrmHubAction("批量发布计划", "草稿、预览、暂停和重试", ScrmUiStatus.Planned, "moments.batch-plan"),
        ScrmHubAction("素材发布", "从素材复制到朋友圈", ScrmUiStatus.Ready, "moments.material-publish")
    )
}
