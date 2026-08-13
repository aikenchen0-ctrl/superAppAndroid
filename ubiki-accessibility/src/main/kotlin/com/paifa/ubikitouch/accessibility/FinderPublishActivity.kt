package com.paifa.ubikitouch.accessibility

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderMediaType
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderPostTemplateRequest
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderTaskAwaiter
import com.paifa.ubikitouch.accessibility.floatingchat.finder.ScrmFinderApi
import com.paifa.ubikitouch.accessibility.floatingchat.finder.finderParseMediaUrls
import com.paifa.ubikitouch.accessibility.floatingchat.finder.validatedFinderPostRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinderPublishActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BackHandler(onBack = ::finish); FinderPublishScreen(applicationContext, ::finish) }
    }
}

private val Background = Color(0xFFF2F3F5)
private val Primary = Color(0xFF1A1F24)
private val Secondary = Color(0xFF6D7076)
private val Purple = Color(0xFF805CDB)

@Composable private fun FinderPublishScreen(context: android.content.Context, onBack: () -> Unit) {
    val manager = remember { ScrmSettingsManager(context) }; val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }; var mediaUrl by remember { mutableStateOf("") }; var coverUrl by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf(false) }; var validationError by remember { mutableStateOf<String?>(null) }; var showEnvironment by remember { mutableStateOf(false) }; var working by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("草稿状态 · 未上传") }; var banner by remember { mutableStateOf("尚未调用真实接口 · 填写媒体 URL 后发布") }; var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun publish() { scope.launch { working = true; status = "正在校验真实媒体和发布参数"; banner = "正在调用 /openapi/v1/finder/posts/template"; runCatching { withContext(Dispatchers.IO) { val session = manager.loadSelectedSessionOrBootstrap(); val request = FinderPostTemplateRequest(session.deviceUuid, session.weChatId, content.trim(), finderParseMediaUrls(mediaUrl), FinderMediaType.Video.code, coverUrl.trim().takeIf(String::isNotEmpty)); val api = ScrmFinderApi(manager.loadApiConfig()); val template = api.buildPostTemplate(request); val validatedPost = validatedFinderPostRequest(template); val outcome = FinderTaskAwaiter(api).await { api.publishPost(validatedPost) }; listOf("template.path" to "/openapi/v1/finder/posts/template", "request.path" to "/openapi/v1/finder/posts", "deviceUuid" to validatedPost.deviceUuid, "weChatId" to validatedPost.weChatId, "content" to validatedPost.content, "medias" to validatedPost.medias.joinToString(", "), "mediaType" to "video", "cover" to (validatedPost.cover ?: "未提供"), "taskId" to outcome.taskId.toString(), "status" to outcome.message) } }.onSuccess { rows = it; status = "服务端任务已完成"; banner = "真实发布任务已完成" }.onFailure { rows = listOf("template.path" to "/openapi/v1/finder/posts/template", "request.path" to "/openapi/v1/finder/posts", "error" to (it.message ?: "视频号发布失败")); status = "发布失败，未确认微信端结果"; banner = "真实接口调用失败 · 未生成本地成功状态" }; working = false } }
    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF007AFF)) }; Text("视频号发布", Modifier.weight(1f), fontSize = 17.sp, color = Primary); TextButton(onClick = { if (content.isBlank() || mediaUrl.isBlank()) validationError = "请填写正文和真实视频媒体 URL，不能使用演示地址或本地占位内容。" else confirmation = true }, enabled = !working) { Text("发布", color = Color(0xFF007AFF), fontSize = 16.sp) }; TextButton(onClick = { showEnvironment = true }) { Text("环境", color = Color(0xFF007AFF), fontSize = 16.sp) } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { HeaderCard(manager.loadSummary().selectedWeChatId.orEmpty()) }; item { PreviewCard(mediaUrl.isNotBlank()) }; item { Card { CardTitle("正文内容", "可编辑"); OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().height(128.dp), placeholder = { Text("填写视频号发布正文") }) } }; item { Card { CardTitle("发布设置"); Setting("发布账号", manager.loadSummary().selectedWeChatId ?: "未选择"); Setting("话题", "未设置"); Setting("位置", "未设置"); Setting("可见范围", "后端默认"); Setting("审核方式", "后端决定"); OutlinedTextField(mediaUrl, { mediaUrl = it }, Modifier.fillMaxWidth(), label = { Text("真实视频 URL（https://...）") }); Spacer(Modifier.height(10.dp)); OutlinedTextField(coverUrl, { coverUrl = it }, Modifier.fillMaxWidth(), label = { Text("真实封面 URL（可选）") }) } }; item { Card { CardTitle("上传与后台任务"); Text(status, fontSize = 14.sp, color = Purple, fontWeight = FontWeight.SemiBold); LinearProgressIndicator(progress = { if (working) .45f else if (rows.isNotEmpty() && !status.startsWith("发布失败")) 1f else 0f }, Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Purple); Setting("上传队列", if (working) "正在发布" else "等待发布"); Setting("后台任务", "TaskResult"); Setting("发布审核", if (working) "正在提交" else "待提交") } }; item { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = Color(0x1A805CDB), modifier = Modifier.fillMaxWidth()) { Text(banner, color = Color(0xFF5C3DAE), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)) } }; if (rows.isNotEmpty()) item { ResultRows(rows) } }
    }
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("确认发布视频号") }, text = { Text("将使用当前微信帐号调用真实视频号发布接口。发布成功后可能产生公开内容，是否继续？") }, confirmButton = { TextButton(onClick = { confirmation = false; publish() }) { Text("发布", color = Color(0xFFD32F2F)) } }, dismissButton = { TextButton(onClick = { confirmation = false }) { Text("取消") } })
    validationError?.let { message -> AlertDialog(onDismissRequest = { validationError = null }, title = { Text("无法发布") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { validationError = null }) { Text("知道了") } }) }
    if (showEnvironment) { val environment = remember { manager.loadSummary() }; AlertDialog(onDismissRequest = { showEnvironment = false }, title = { Text("环境") }, text = { Text("服务地址\n${environment.baseUrl}\n\nX-API-Key\n${environment.maskedApiKey ?: "未配置"}") }, confirmButton = { TextButton(onClick = { showEnvironment = false }) { Text("完成") } }) }
}

@Composable private fun HeaderCard(account: String) { Card { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = Purple, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.VideoLibrary, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("视频号发布", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary); Text("使用当前微信帐号发布", fontSize = 13.sp, color = Secondary) }; Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = Color(0x1A805CDB)) { Text(account.ifBlank { "未选择帐号" }, fontSize = 12.sp, color = Color(0xFF5C3DAE), modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }
@Composable private fun PreviewCard(hasMedia: Boolean) { Card { CardTitle("视频预览"); Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF171A21), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(42.dp)); Text(if (hasMedia) "已填写真实视频 URL" else "未选择真实视频", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) }; Text("短视频     封面可选     位置未设置", color = Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)); Text("发布位置：未设置（接口不会发送位置字段）", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) } }
@Composable private fun Card(content: @Composable () -> Unit) { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = { content() }) } }
@Composable private fun CardTitle(title: String, trailing: String? = null) { Row(Modifier.fillMaxWidth()) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.weight(1f)); trailing?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Secondary) } } }
@Composable private fun Setting(title: String, value: String) { Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 13.sp, color = Secondary, modifier = Modifier.weight(1f)); Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun ResultRows(rows: List<Pair<String, String>>) { Card { Text("OpenApiFinderPostRequest / TaskResult", fontSize = 13.sp, color = Secondary); rows.forEach { (key, value) -> Text(key, fontSize = 12.sp, color = Color(0xFF193852), modifier = Modifier.padding(top = 10.dp)); Text(value, fontSize = 15.sp, color = Primary, modifier = Modifier.padding(top = 4.dp)) } } }
