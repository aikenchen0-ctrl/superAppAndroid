package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderMediaType
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderPoi
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderPostRequest
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderPostTemplateRequest
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderTaskAwaiter
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderTaskOutcome
import com.paifa.ubikitouch.accessibility.floatingchat.finder.ScrmFinderApi
import com.paifa.ubikitouch.accessibility.floatingchat.finder.finderComposeContent
import com.paifa.ubikitouch.accessibility.floatingchat.finder.finderParseMediaUrls
import com.paifa.ubikitouch.accessibility.floatingchat.finder.toFinderUserMessage
import com.paifa.ubikitouch.accessibility.floatingchat.finder.validatedFinderPostRequest
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinderPublishActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(onBack = ::finish)
            FinderPublishScreen(applicationContext, ::finish)
        }
    }
}

private enum class FinderPublishTab(val label: String) {
    Publish("发布"),
    Tasks("任务")
}

private data class FinderPublishAuditRow(val label: String, val value: String)

private data class FinderPublishDraft(
    val content: String,
    val medias: List<String>,
    val mediaType: FinderMediaType,
    val cover: String?,
    val poi: FinderPoi?
) {
    fun templateFor(deviceUuid: String, weChatId: String) = FinderPostTemplateRequest(
        deviceUuid = deviceUuid,
        weChatId = weChatId,
        content = content,
        medias = medias,
        mediaType = mediaType.code,
        cover = cover,
        poi = poi,
        includePostRequest = true
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FinderPublishScreen(context: Context, onBack: () -> Unit) {
    val settingsManager = remember { ScrmSettingsManager(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { FinderPublishTab.entries.size }
    val summary = remember { settingsManager.loadSummary() }
    var content by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf("") }
    var mediaUrls by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var poiCity by remember { mutableStateOf("") }
    var poiName by remember { mutableStateOf("") }
    var poiAddress by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf(FinderMediaType.Video) }
    var validatedPost by remember { mutableStateOf<FinderPostRequest?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("尚未调用视频号接口") }
    var failure by remember { mutableStateOf<String?>(null) }
    var auditRows by remember {
        mutableStateOf(
            listOf(
                FinderPublishAuditRow("模板接口", "/openapi/v1/finder/posts/template"),
                FinderPublishAuditRow("发布接口", "/openapi/v1/finder/posts")
            )
        )
    }

    fun invalidateTemplate() {
        validatedPost = null
        confirmed = false
    }

    fun snapshotDraft(): FinderPublishDraft {
        val hasPoi = listOf(poiCity, poiName, poiAddress).any { it.isNotBlank() }
        return FinderPublishDraft(
            content = finderComposeContent(content, topics),
            medias = finderParseMediaUrls(mediaUrls),
            mediaType = mediaType,
            cover = coverUrl.trim().takeIf(String::isNotEmpty),
            poi = if (hasPoi) {
                FinderPoi(
                    city = poiCity.trim().takeIf(String::isNotEmpty),
                    name = poiName.trim().takeIf(String::isNotEmpty),
                    address = poiAddress.trim().takeIf(String::isNotEmpty)
                )
            } else {
                null
            }
        )
    }

    fun templateRows(post: FinderPostRequest, warnings: List<String>) = buildList {
        add(FinderPublishAuditRow("模板接口", "/openapi/v1/finder/posts/template"))
        add(FinderPublishAuditRow("发布接口", "/openapi/v1/finder/posts"))
        add(FinderPublishAuditRow("账号", post.weChatId))
        add(FinderPublishAuditRow("媒体类型", mediaType.label))
        add(FinderPublishAuditRow("媒体数量", post.medias.size.toString()))
        post.cover?.let { add(FinderPublishAuditRow("封面", it)) }
        post.poi?.name?.let { add(FinderPublishAuditRow("位置", it)) }
        warnings.forEach { add(FinderPublishAuditRow("模板提示", it)) }
    }

    fun validateTemplate() {
        val draft = runCatching(::snapshotDraft).getOrElse { error ->
            failure = error.toFinderUserMessage()
            status = "模板校验未开始"
            return
        }
        scope.launch {
            working = true
            failure = null
            status = "正在调用模板预检接口"
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = settingsManager.loadSelectedSessionOrBootstrap()
                    val api = ScrmFinderApi(settingsManager.loadApiConfig())
                    val template = api.buildPostTemplate(draft.templateFor(session.deviceUuid, session.weChatId))
                    validatedFinderPostRequest(template) to (template.warnings ?: emptyList())
                }
            }.onSuccess { (post, warnings) ->
                validatedPost = post
                auditRows = templateRows(post, warnings)
                status = "模板预检通过，请确认后提交发布任务"
            }.onFailure { error ->
                validatedPost = null
                failure = error.toFinderUserMessage()
                status = "模板预检失败，未创建发布任务"
            }
            working = false
        }
    }

    fun publish() {
        val post = validatedPost ?: return
        scope.launch {
            working = true
            failure = null
            status = "正在提交视频号发布任务并等待服务端结果"
            runCatching {
                withContext(Dispatchers.IO) {
                    val api = ScrmFinderApi(settingsManager.loadApiConfig())
                    FinderTaskAwaiter(api).await { api.publishPost(post) }
                }
            }.onSuccess { outcome ->
                auditRows = auditRows + outcome.toAuditRows()
                status = if (outcome.completed) "服务端任务已完成" else outcome.message
            }.onFailure { error ->
                failure = error.toFinderUserMessage()
                status = "发布失败，未确认微信端结果"
                auditRows = auditRows + FinderPublishAuditRow("发布错误", failure.orEmpty())
            }
            working = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // UI：视频号发布复用悬浮聊天工作区的工具栏，状态区由工具栏内边距统一处理。
        // 测试流程：从聊天根打开视频号发布，确认顶部无独立空白区，点击左上返回由宿主执行退出动画。
        FloatingWorkspaceTopAppBar(title = "视频号发布", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            FinderPublishTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (FinderPublishTab.entries[page]) {
                FinderPublishTab.Publish -> FinderPublishForm(
                    accountId = summary.selectedWeChatId.orEmpty(),
                    content = content,
                    topics = topics,
                    mediaUrls = mediaUrls,
                    coverUrl = coverUrl,
                    poiCity = poiCity,
                    poiName = poiName,
                    poiAddress = poiAddress,
                    mediaType = mediaType,
                    confirmed = confirmed,
                    working = working,
                    status = status,
                    failure = failure,
                    templateReady = validatedPost != null,
                    onContentChange = { content = it; invalidateTemplate() },
                    onTopicsChange = { topics = it; invalidateTemplate() },
                    onMediaUrlsChange = { mediaUrls = it; invalidateTemplate() },
                    onCoverUrlChange = { coverUrl = it; invalidateTemplate() },
                    onPoiCityChange = { poiCity = it; invalidateTemplate() },
                    onPoiNameChange = { poiName = it; invalidateTemplate() },
                    onPoiAddressChange = { poiAddress = it; invalidateTemplate() },
                    onMediaTypeChange = { mediaType = it; invalidateTemplate() },
                    onConfirmedChange = { confirmed = it },
                    onValidateTemplate = ::validateTemplate,
                    onPublish = ::publish
                )
                FinderPublishTab.Tasks -> FinderPublishTaskList(
                    rows = auditRows,
                    status = status,
                    failure = failure,
                    working = working
                )
            }
        }
    }
}

@Composable
private fun FinderPublishForm(
    accountId: String,
    content: String,
    topics: String,
    mediaUrls: String,
    coverUrl: String,
    poiCity: String,
    poiName: String,
    poiAddress: String,
    mediaType: FinderMediaType,
    confirmed: Boolean,
    working: Boolean,
    status: String,
    failure: String?,
    templateReady: Boolean,
    onContentChange: (String) -> Unit,
    onTopicsChange: (String) -> Unit,
    onMediaUrlsChange: (String) -> Unit,
    onCoverUrlChange: (String) -> Unit,
    onPoiCityChange: (String) -> Unit,
    onPoiNameChange: (String) -> Unit,
    onPoiAddressChange: (String) -> Unit,
    onMediaTypeChange: (FinderMediaType) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onValidateTemplate: () -> Unit,
    onPublish: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FinderPublishHeader(accountId)
        }
        item {
            FinderSection(title = "发布内容") {
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("正文") },
                    minLines = 4
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = topics,
                    onValueChange = onTopicsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("话题，使用逗号分隔") }
                )
            }
        }
        item {
            FinderSection(title = "媒体与封面") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinderMediaType.entries.forEach { type ->
                        FilterChip(
                            selected = mediaType == type,
                            onClick = { onMediaTypeChange(type) },
                            label = { Text(type.label) },
                            enabled = !working
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = mediaUrls,
                    onValueChange = onMediaUrlsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("媒体 URL，每行一条") },
                    supportingText = { Text("仅支持手机可访问的 HTTP(S) 地址") },
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = coverUrl,
                    onValueChange = onCoverUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("封面 URL，可选") }
                )
            }
        }
        item {
            FinderSection(title = "位置，可选") {
                OutlinedTextField(value = poiCity, onValueChange = onPoiCityChange, modifier = Modifier.fillMaxWidth(), label = { Text("城市") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = poiName, onValueChange = onPoiNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("地点名称") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = poiAddress, onValueChange = onPoiAddressChange, modifier = Modifier.fillMaxWidth(), label = { Text("地点地址") })
            }
        }
        item {
            FinderPublishStatusCard(status = status, failure = failure, working = working, templateReady = templateReady)
        }
        item {
            FinderSection(title = "提交发布") {
                Button(onClick = onValidateTemplate, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                    Text("校验发布模板")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirmedChange,
                        enabled = templateReady && !working
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("我确认提交视频号发布任务", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = onPublish,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = templateReady && confirmed && !working
                ) {
                    Text("确认发布")
                }
            }
        }
    }
}

@Composable
private fun FinderPublishHeader(accountId: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("视频号发布", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                Text("使用当前微信账号提交服务端发布任务", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (accountId.isNotBlank()) {
                Text(accountId, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FinderSection(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun FinderPublishStatusCard(status: String, failure: String?, working: Boolean, templateReady: Boolean) {
    FinderSection(title = "接口状态") {
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        LinearProgressIndicator(
            progress = { if (working) 0.5f else if (templateReady && failure == null) 1f else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        failure?.let {
            Text(it, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FinderPublishTaskList(
    rows: List<FinderPublishAuditRow>,
    status: String,
    failure: String?,
    working: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { FinderPublishStatusCard(status = status, failure = failure, working = working, templateReady = false) }
        item {
            Text(
                text = "请求与任务记录",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        items(rows, key = { "${it.label}:${it.value}" }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(row.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
                    Text(row.value, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun FinderTaskOutcome.toAuditRows() = listOf(
    FinderPublishAuditRow("任务 ID", taskId.toString()),
    FinderPublishAuditRow("任务结果", message),
    FinderPublishAuditRow("任务完成", if (completed) "已完成" else "服务端未确认完成")
)
