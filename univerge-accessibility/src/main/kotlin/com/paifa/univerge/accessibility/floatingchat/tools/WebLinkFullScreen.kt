package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.core.model.FloatingChatMessage
import java.net.URI
import kotlinx.coroutines.launch

internal enum class WebLinkFullScreenTab(val label: String) {
    Send("发送链接"),
    Conversation("当前会话")
}

internal data class WebLinkDraft(
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String
)

internal sealed interface WebLinkSubmissionState {
    data object Idle : WebLinkSubmissionState
    data object Sending : WebLinkSubmissionState
    data class Sent(val message: String) : WebLinkSubmissionState
    data class Failed(val message: String) : WebLinkSubmissionState
}

/**
 * 对应 iOS 网页链接发送页的 Android Material 3 全屏工作区。
 * 测试流程：点击右侧“网页链接”，填写 http/https 地址和标题后发送，切换“当前会话”确认链接记录，
 * 最后点击左上角返回，确认实体从顶部向下退出且没有额外窗口附着。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebLinkFullScreen(
    webLinks: List<FloatingChatMessage>,
    submissionState: WebLinkSubmissionState,
    onSendWebLink: (WebLinkDraft) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { WebLinkFullScreenTab.entries.size })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 页面根保持 surface，显示和关闭运动统一由外层悬浮工作区承担。
        FloatingWorkspaceTopAppBar(title = "网页链接", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            WebLinkFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (WebLinkFullScreenTab.entries[page]) {
                WebLinkFullScreenTab.Send -> WebLinkSendPage(
                    submissionState = submissionState,
                    onSendWebLink = onSendWebLink
                )
                WebLinkFullScreenTab.Conversation -> WebLinkConversationPage(webLinks)
            }
        }
    }
}

@Composable
private fun WebLinkSendPage(
    submissionState: WebLinkSubmissionState,
    onSendWebLink: (WebLinkDraft) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    val validUrl = isSupportedWebLink(url)
    val validThumbnail = thumbnailUrl.isBlank() || isSupportedWebLink(thumbnailUrl)
    val canSend = validUrl && title.isNotBlank() && validThumbnail && submissionState !is WebLinkSubmissionState.Sending

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "发送网页链接",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("网页地址") },
                        supportingText = { Text("仅支持 http 或 https 地址") },
                        isError = url.isNotBlank() && !validUrl,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("链接标题") },
                        isError = title.isBlank(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("链接描述（可选）") }
                    )
                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = { thumbnailUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("缩略图地址（可选）") },
                        isError = thumbnailUrl.isNotBlank() && !validThumbnail,
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = {
                            onSendWebLink(
                                WebLinkDraft(
                                    url = url.trim(),
                                    title = title.trim(),
                                    description = description.trim(),
                                    thumbnailUrl = thumbnailUrl.trim()
                                )
                            )
                        },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (submissionState is WebLinkSubmissionState.Sending) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("发送链接", fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
        when (submissionState) {
            is WebLinkSubmissionState.Sent -> item {
                Text(submissionState.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
            }
            is WebLinkSubmissionState.Failed -> item {
                Text(submissionState.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Normal)
            }
            else -> Unit
        }
    }
}

@Composable
private fun WebLinkConversationPage(webLinks: List<FloatingChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "当前会话",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        if (webLinks.isEmpty()) {
            item { Text("暂无网页链接消息", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(webLinks.size, key = { index -> webLinks[index].id }) { index ->
                val message = webLinks[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Link, contentDescription = null) },
                        headlineContent = { Text(message.text.ifBlank { "网页链接" }, fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                message.resourceUrl ?: message.detail.orEmpty(),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    }
}

/** 对应网页链接表单的边界校验，只允许可被 SCRM 链接卡片消费的 http/https 地址。 */
internal fun isSupportedWebLink(value: String): Boolean {
    return runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
    }.getOrDefault(false) && runCatching { URI(value.trim()).host.isNullOrBlank().not() }.getOrDefault(false)
}
