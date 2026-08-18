package com.paifa.univerge.accessibility.floatingchat.scrm

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private data class OpenApiAction(val title: String, val method: String, val path: String, val subtitle: String, val risky: Boolean = false)
private data class OpenApiSection(val title: String, val actions: List<OpenApiAction>)

private val openApiSections = listOf(
    OpenApiSection("基础 / 设备", listOf(
        OpenApiAction("快速启动", "GET", "/openapi/v1/quick-start", "读取后端推荐 deviceUuid、weChatId 与下一步测试项。"),
        OpenApiAction("当前 OpenAPI 身份", "GET", "/openapi/v1/me", "确认当前 X-API-Key 对应的租户与身份。"),
        OpenApiAction("接入指南", "GET", "/openapi/v1/guide", "读取服务端当前接入要求与能力说明。"),
        OpenApiAction("测试计划", "GET", "/openapi/v1/test-plan", "读取服务端建议的真实接口验收顺序。"),
        OpenApiAction("能力检查", "GET", "/openapi/v1/capabilities", "检查当前设备和微信号可用能力。"),
        OpenApiAction("设备列表", "GET", "/openapi/v1/devices", "读取可访问设备。"),
        OpenApiAction("微信帐号", "GET", "/openapi/v1/wechat-accounts", "读取可访问微信号。"),
        OpenApiAction("最近任务", "GET", "/openapi/v1/tasks/recent", "查看 OpenAPI 最近任务。"),
        OpenApiAction("重启设备微信", "POST", "/openapi/v1/devices/restart-wechat", "重启当前设备上的微信。", true)
    )),
    OpenApiSection("微信登录会话", listOf(
        OpenApiAction("活跃登录会话", "GET", "/openapi/v1/wechat-login/active", "查询仍在进行的登录会话。"),
        OpenApiAction("开始登录会话", "POST", "/openapi/v1/wechat-login/start", "为当前设备创建二维码登录会话。", true),
        OpenApiAction("查询登录会话", "GET", "/openapi/v1/wechat-login/0", "把路径末尾 0 改成真实 sessionId。"),
        OpenApiAction("取消登录会话", "POST", "/openapi/v1/wechat-login/0/cancel", "把路径中的 0 改成真实 sessionId。", true)
    )),
    OpenApiSection("好友 / 标签 / 客户画像", listOf(
        OpenApiAction("联系人列表", "GET", "/openapi/v1/contacts", "按当前 weChatId 查询联系人。"),
        OpenApiAction("好友 wxid", "GET", "/openapi/v1/contacts/wxids", "获取所有好友 wxid。"),
        OpenApiAction("联系人标签", "GET", "/openapi/v1/contact-labels", "查询当前账号标签。"),
        OpenApiAction("好友请求", "GET", "/openapi/v1/friend-requests", "查询好友验证请求。"),
        OpenApiAction("查找联系人", "POST", "/openapi/v1/friends/find", "用手机号、wxid 或微信号查找联系人。"),
        OpenApiAction("添加好友", "POST", "/openapi/v1/friends", "发送好友验证并创建任务。", true),
        OpenApiAction("拉取好友请求", "POST", "/openapi/v1/friend-requests/pull", "从手机端拉取好友验证请求。", true),
        OpenApiAction("处理好友请求", "POST", "/openapi/v1/friend-requests/handle", "同意或拒绝好友请求。", true),
        OpenApiAction("删除好友", "DELETE", "/openapi/v1/friends/wxid_demo_friend", "把 path 末尾改成真实 friendId 或 wxid。", true),
        OpenApiAction("手机号批量加好友", "POST", "/openapi/v1/friends/by-phone", "按手机号批量添加好友。", true),
        OpenApiAction("通讯录加好友", "POST", "/openapi/v1/friends/from-phonebook", "从手机通讯录入口添加好友。", true),
        OpenApiAction("群内加好友", "POST", "/openapi/v1/friends/in-chatroom", "从群成员添加好友。", true),
        OpenApiAction("名片加好友", "POST", "/openapi/v1/friends/name-card", "通过名片消息添加好友。", true),
        OpenApiAction("发送验证消息", "POST", "/openapi/v1/friends/verify", "向待验证联系人发送好友验证。", true),
        OpenApiAction("新建/更新标签", "POST", "/openapi/v1/contact-labels", "创建或更新联系人标签。", true),
        OpenApiAction("删除标签", "DELETE", "/openapi/v1/contact-labels/1", "把 path 末尾改成真实 labelId。", true),
        OpenApiAction("好友设置标签", "POST", "/openapi/v1/contacts/labels", "给单个好友设置标签。", true),
        OpenApiAction("批量设置标签", "POST", "/openapi/v1/contacts/labels/batch", "给指定 wxid 列表批量设置标签。", true),
        OpenApiAction("按筛选批量打标", "POST", "/openapi/v1/contacts/labels/batch-by-filter", "按来源、等级、画像筛选后批量设置标签。", true),
        OpenApiAction("联系人详情", "GET", "/openapi/v1/contacts/1/detail", "把路径中的 1 改成真实 contactId。"),
        OpenApiAction("客户画像详情", "GET", "/openapi/v1/contacts/1/customer-profile", "把路径中的 1 改成真实 contactId。"),
        OpenApiAction("保存客户画像", "PUT", "/openapi/v1/contacts/1/customer-profile", "编辑 JSON body 后保存画像。", true),
        OpenApiAction("同步联系人", "POST", "/openapi/v1/contacts/sync", "同步当前微信号联系人。", true),
        OpenApiAction("同步标签", "POST", "/openapi/v1/contact-labels/sync", "同步当前微信号标签。", true)
    )),
    OpenApiSection("群聊", listOf(
        OpenApiAction("群聊列表", "GET", "/openapi/v1/chatrooms", "查询群聊。"),
        OpenApiAction("群成员列表", "GET", "/openapi/v1/chatrooms/demo@chatroom/members", "把群 id 改成真实值。"),
        OpenApiAction("同步群聊", "POST", "/openapi/v1/chatrooms/sync", "同步当前账号群聊。", true),
        OpenApiAction("创建群聊", "POST", "/openapi/v1/chatrooms", "按 memberWxids 创建群。", true),
        OpenApiAction("修改群名称", "POST", "/openapi/v1/chatrooms/rename", "修改指定群聊名称。", true),
        OpenApiAction("设置群公告", "POST", "/openapi/v1/chatrooms/notice", "发布或更新群公告。", true),
        OpenApiAction("刷新群资料", "POST", "/openapi/v1/chatrooms/refresh", "刷新指定群资料和成员信息。", true),
        OpenApiAction("邀请群成员", "POST", "/openapi/v1/chatrooms/members/invite", "把指定 wxid 邀请进群。", true),
        OpenApiAction("踢出群成员", "POST", "/openapi/v1/chatrooms/members/kick", "从群聊移出指定成员。", true),
        OpenApiAction("拉取群二维码", "POST", "/openapi/v1/chatrooms/qrcode", "获取群二维码。"),
        OpenApiAction("扫码进群", "POST", "/openapi/v1/chatrooms/join-by-qr", "通过群二维码链接入群。", true),
        OpenApiAction("同意入群邀请", "POST", "/openapi/v1/chatrooms/invites/agree", "同意加入群聊邀请。", true),
        OpenApiAction("审批入群邀请", "POST", "/openapi/v1/chatrooms/invites/approve", "审批入群申请。", true),
        OpenApiAction("退出群聊", "POST", "/openapi/v1/chatrooms/exit", "当前微信号退出指定群聊。", true)
    )),
    OpenApiSection("消息 / 支付", listOf(
        OpenApiAction("卡片模板", "GET", "/openapi/v1/messages/card-templates", "获取小程序、网页、公众号卡片模板。"),
        OpenApiAction("发送文本", "POST", "/openapi/v1/messages/text", "真实发送文本消息。", true),
        OpenApiAction("发送图片", "POST", "/openapi/v1/messages/image", "按 URL 发送图片。", true),
        OpenApiAction("发送视频", "POST", "/openapi/v1/messages/video", "按 URL 发送视频。", true),
        OpenApiAction("发送文件", "POST", "/openapi/v1/messages/file", "按 URL 发送文件。", true),
        OpenApiAction("发送语音", "POST", "/openapi/v1/messages/voice", "发送已上传语音 URL。", true),
        OpenApiAction("发送表情", "POST", "/openapi/v1/messages/emoji", "按 md5 发送表情或贴纸。", true),
        OpenApiAction("发送引用消息", "POST", "/openapi/v1/messages/quote", "发送引用或回复消息。", true),
        OpenApiAction("发送网页卡片", "POST", "/openapi/v1/messages/link-card", "发送网页链接卡片。", true),
        OpenApiAction("发送公众号文章", "POST", "/openapi/v1/messages/official-article-card", "发送公众号图文卡片。", true),
        OpenApiAction("发送小程序卡片", "POST", "/openapi/v1/messages/weapp-card", "发送小程序页面卡片。", true),
        OpenApiAction("发送收藏/笔记卡片", "POST", "/openapi/v1/messages/note-card", "发送收藏内容或笔记卡片。", true),
        OpenApiAction("批量发送到指定会话", "POST", "/openapi/v1/messages/batch", "混合批量发送到已知会话。", true),
        OpenApiAction("批量发送", "POST", "/openapi/v1/messages/batch-by-filter", "按标签或来源筛选后批量发送。", true),
        OpenApiAction("钱包余额", "POST", "/openapi/v1/payments/wallet-balance", "查询当前微信钱包余额。"),
        OpenApiAction("红包状态", "POST", "/openapi/v1/payments/red-packets/status-by-message", "按消息 ID 查询红包状态。"),
        OpenApiAction("发送红包", "POST", "/openapi/v1/payments/lucky-money", "真实发红包。", true),
        OpenApiAction("发送转账", "POST", "/openapi/v1/payments/remittance", "真实转账。", true)
    )),
    OpenApiSection("朋友圈", listOf(
        OpenApiAction("朋友圈素材", "GET", "/openapi/v1/moments/materials", "读取素材库。"),
        OpenApiAction("朋友圈素材详情", "GET", "/openapi/v1/moments/materials/1/detail", "把路径中的 1 改成真实素材 ID。"),
        OpenApiAction("创建朋友圈素材", "POST", "/openapi/v1/moments/materials", "保存一条图文或链接素材。", true),
        OpenApiAction("编辑朋友圈素材", "PUT", "/openapi/v1/moments/materials/1", "更新指定素材。", true),
        OpenApiAction("复制朋友圈素材", "POST", "/openapi/v1/moments/materials/1/copy", "复制指定素材。"),
        OpenApiAction("归档朋友圈素材", "POST", "/openapi/v1/moments/materials/1/archive", "归档指定素材。", true),
        OpenApiAction("批量计划", "GET", "/openapi/v1/moments/batch/plans", "读取朋友圈批量发布计划。"),
        OpenApiAction("创建朋友圈批量计划", "POST", "/openapi/v1/moments/batch/plans", "按目标 wxid 或标签创建发布计划。", true),
        OpenApiAction("同步朋友圈", "POST", "/openapi/v1/moments/sync", "同步朋友圈内容。", true),
        OpenApiAction("同步朋友圈消息", "POST", "/openapi/v1/moments/messages/sync", "同步朋友圈点赞和评论消息。", true),
        OpenApiAction("发布朋友圈", "POST", "/openapi/v1/moments", "真实发布朋友圈。", true),
        OpenApiAction("朋友圈详情", "POST", "/openapi/v1/moments/detail", "按 circleId 查询详情。"),
        OpenApiAction("朋友圈点赞", "POST", "/openapi/v1/moments/like", "对朋友圈点赞或取消点赞。", true),
        OpenApiAction("朋友圈评论", "POST", "/openapi/v1/moments/comments", "评论朋友圈或回复评论。", true),
        OpenApiAction("删除朋友圈评论", "POST", "/openapi/v1/moments/comments/delete", "删除指定朋友圈评论。", true)
    )),
    OpenApiSection("视频号", listOf(
        OpenApiAction("构建视频号模板", "POST", "/openapi/v1/finder/posts/template", "安全构建视频号发布模板。"),
        OpenApiAction("发布视频号", "POST", "/openapi/v1/finder/posts", "真实发布视频号内容。", true),
        OpenApiAction("视频号提及", "POST", "/openapi/v1/finder/mentions", "读取视频号点赞、评论、关注提醒。"),
        OpenApiAction("视频号主页", "POST", "/openapi/v1/finder/user-page", "打开或查询指定视频号主页。"),
        OpenApiAction("视频号评论列表", "POST", "/openapi/v1/finder/comments/list", "按 feedId 查询评论。")
    ))
)

@Composable
fun OpenApiWorkbenchActivityContent(context: Context, onClose: () -> Unit) {
    OpenApiWorkbenchPanel(
        manager = remember(context) { ScrmSettingsManager(context.applicationContext) },
        onClose = onClose
    )
}

@Composable
internal fun OpenApiWorkbenchPanel(manager: ScrmSettingsManager, onClose: () -> Unit) {
    var editor by remember { mutableStateOf<OpenApiAction?>(null) }
    var result by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showEnvironment by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { result = emptyList() }
    if (editor != null) {
        OpenApiRequestEditor(editor!!, manager, onClose = { editor = null }) { rows -> result = rows; editor = null }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：OpenAPI 复用 UI组件 的 M3 toolbar，30dp 状态区由共享组件内嵌。
        // 测试流程：打开 OpenAPI，刷新或查看环境后点击左上返回，确认页面向顶部退出。
        FloatingWorkspaceTopAppBar(
            title = "业务接口",
            onBack = onClose,
            actions = {
                TextButton(onClick = { refresh++ }) { Text("刷新") }
                TextButton(onClick = { showEnvironment = true }) { Text("环境") }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            openApiSections.forEach { section -> item(section.title) { OpenApiGroupedSection(section) { editor = it } } }
            if (result.isNotEmpty()) item("响应") { OpenApiResponseSection(result) }
        }
    }
    if (showEnvironment) {
        val environment = remember { manager.loadSummary() }
        AlertDialog(
            onDismissRequest = { showEnvironment = false },
            title = { Text("环境") },
            text = { Text("服务地址\n${environment.baseUrl}\n\nX-API-Key\n${environment.maskedApiKey ?: "未配置"}") },
            confirmButton = { TextButton(onClick = { showEnvironment = false }) { Text("完成") } }
        )
    }
}

@Composable private fun OpenApiGroupedSection(section: OpenApiSection, onAction: (OpenApiAction) -> Unit) {
    Column {
        Text(section.title, fontSize = 13.sp, color = Color(0xFF6D6D72), modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFFFFF), modifier = Modifier.fillMaxWidth()) {
            Column {
                section.actions.forEachIndexed { index, action ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onAction(action) }.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("${action.method} ${action.path}", fontSize = 12.sp, color = Color(0xFF193852))
                        Text(action.title, fontSize = 15.sp, color = Color(0xFF191F24), modifier = Modifier.padding(top = 5.dp))
                        Text(action.subtitle, fontSize = 12.sp, color = Color(0xFF6E6E73), modifier = Modifier.padding(top = 5.dp))
                    }
                    if (index < section.actions.lastIndex) Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E5EA)))
                }
            }
        }
    }
}

@Composable private fun OpenApiResponseSection(rows: List<Pair<String, String>>) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column { rows.forEach { (key, value) -> Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { Text(key, fontSize = 12.sp, color = Color(0xFF193852)); Text(value, fontSize = 15.sp, color = Color(0xFF191F24), modifier = Modifier.padding(top = 5.dp)) } } }
    }
}

@Composable private fun OpenApiRequestEditor(action: OpenApiAction, manager: ScrmSettingsManager, onClose: () -> Unit, onRun: (List<Pair<String, String>>) -> Unit) {
    var path by remember { mutableStateOf(action.path) }; var query by remember { mutableStateOf("") }; var body by remember { mutableStateOf("{}") }; var confirm by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    LaunchedEffect(action.path) { runCatching { withContext(Dispatchers.IO) { manager.loadSelectedSessionOrBootstrap() } }.onSuccess { session -> query = "deviceUuid=${session.deviceUuid}&weChatId=${session.weChatId}"; body = "{\n  \"deviceUuid\": \"${session.deviceUuid}\",\n  \"weChatId\": \"${session.weChatId}\"\n}" }.onFailure { error = it.message } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        FloatingWorkspaceTopAppBar(
            title = action.title,
            onBack = onClose,
            actions = {
                TextButton(onClick = {
                    error = validateOpenApiRequest(path, query, body)
                    if (error == null) { if (action.risky) confirm = true else runOpenApi(action, path, query, body, manager, scope, onRun) }
                }) { Text("调用") }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("${action.method}\n${action.subtitle}", fontSize = 13.sp, color = Color(0xFF6E6E73)) }
            item { OpenApiEditorCard("接口", "可以把 path 中的 demo ID 改成真实 wxid、群 ID 或素材 ID。") { OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("/openapi/v1/...") }) } }
            item { OpenApiEditorCard("Query", "格式：deviceUuid=xxx&weChatId=xxx。没有参数可留空。") { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().height(110.dp), label = { Text("Query") }) } }
            item { OpenApiEditorCard("JSON Body", "发送前可编辑真实参数；媒体字段需要手机可访问的 http/https URL。") { OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(280.dp), label = { Text("JSON Body") }) } }
            error?.let { item { Text(it, color = Color(0xFFB33A3A), fontSize = 12.sp) } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认调用") }, text = { Text("${action.title}\n${action.method} $path\n该接口可能产生真实业务动作。") }, confirmButton = { TextButton(onClick = { confirm = false; runOpenApi(action, path, query, body, manager, scope, onRun) }) { Text("确认调用") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable
private fun OpenApiEditorCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF8F8FA), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 16.sp, color = Color(0xFF17191C))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF6E6E73), modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            content()
        }
    }
}

private fun validateOpenApiRequest(path: String, query: String, body: String): String? {
    if (!path.startsWith("/openapi/v1/") && !path.startsWith("/openapi/docs/")) return "接口路径需要以 /openapi/v1/ 或 /openapi/docs/ 开头。"
    if (query.split('&').any { it.isNotBlank() && it.trim().split('=', limit = 2).size != 2 }) return "Query 格式不正确，请使用 key=value&key=value。"
    if (body.isNotBlank() && runCatching { Json.parseToJsonElement(body) }.isFailure) return "JSON Body 格式不正确。"
    return null
}

private fun runOpenApi(action: OpenApiAction, path: String, queryText: String, body: String, manager: ScrmSettingsManager, scope: kotlinx.coroutines.CoroutineScope, onRun: (List<Pair<String, String>>) -> Unit) { scope.launch { runCatching { withContext(Dispatchers.IO) { val session = manager.loadSelectedSessionOrBootstrap(); val query = queryText.split('&').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 && p[0].isNotBlank() }?.let { it[0] to it[1] } }.toMap(); session.openApiRaw.requestRaw(action.method, path, query, body.takeIf { action.method != "GET" && it.isNotBlank() }).toString() } }.onSuccess { onRun(listOf("${action.method} ${path}" to it)) }.onFailure { onRun(listOf("${action.title}" to (it.message ?: "请求失败"))) } } }
