package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
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
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.launch

internal enum class OfficialArticleFullScreenTab(val label: String) {
    Compose("发送文章"),
    Conversation("当前会话")
}

internal data class OfficialArticleDraft(
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val sourceName: String
)

internal sealed interface OfficialArticleSubmissionState {
    data object Idle : OfficialArticleSubmissionState
    data object Sending : OfficialArticleSubmissionState
    data class Sent(val message: String) : OfficialArticleSubmissionState
    data class Failed(val message: String) : OfficialArticleSubmissionState
}

/**
 * 对应 iOS 公众号文章卡片发送页的 Android M3 全屏工作区。
 * 测试流程：点击右侧“公众号文章”，填写 http/https 地址、标题和摘要后发送；切换当前会话查看记录，
 * 点击左上角返回确认顶部返回按钮和自上向下实体退场动画均正常，且不会附着额外系统窗口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfficialArticleFullScreen(
    articles: List<FloatingChatMessage>,
    submissionState: OfficialArticleSubmissionState,
    onSendArticle: (OfficialArticleDraft) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { OfficialArticleFullScreenTab.entries.size })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 文章内容占用工具栏后的稳定工作区，统一工具栏处理顶部安全区。
        FloatingWorkspaceTopAppBar(title = "公众号文章", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            OfficialArticleFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (OfficialArticleFullScreenTab.entries[page]) {
                OfficialArticleFullScreenTab.Compose -> OfficialArticleComposePage(
                    submissionState = submissionState,
                    onSendArticle = onSendArticle
                )
                OfficialArticleFullScreenTab.Conversation -> OfficialArticleConversationPage(articles)
            }
        }
    }
}

@Composable
private fun OfficialArticleComposePage(
    submissionState: OfficialArticleSubmissionState,
    onSendArticle: (OfficialArticleDraft) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("公众号") }
    val validUrl = isSupportedWebLink(url)
    val validThumbnail = thumbnailUrl.isBlank() || isSupportedWebLink(thumbnailUrl)
    val canSend = validUrl && title.isNotBlank() && sourceName.isNotBlank() && validThumbnail &&
        submissionState !is OfficialArticleSubmissionState.Sending

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "发送公众号文章",
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
                        label = { Text("文章地址") },
                        supportingText = { Text("仅支持 http 或 https 地址") },
                        isError = url.isNotBlank() && !validUrl,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("文章标题") },
                        isError = title.isBlank(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("文章摘要（可选）") }
                    )
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("公众号名称") },
                        isError = sourceName.isBlank(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = { thumbnailUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("封面地址（可选）") },
                        isError = thumbnailUrl.isNotBlank() && !validThumbnail,
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = {
                            onSendArticle(
                                OfficialArticleDraft(
                                    url = url.trim(),
                                    title = title.trim(),
                                    description = description.trim(),
                                    thumbnailUrl = thumbnailUrl.trim(),
                                    sourceName = sourceName.trim()
                                )
                            )
                        },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (submissionState is OfficialArticleSubmissionState.Sending) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("发送文章", fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
        when (submissionState) {
            is OfficialArticleSubmissionState.Sent -> item {
                Text(submissionState.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
            }
            is OfficialArticleSubmissionState.Failed -> item {
                Text(submissionState.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Normal)
            }
            else -> Unit
        }
    }
}

@Composable
private fun OfficialArticleConversationPage(articles: List<FloatingChatMessage>) {
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
        if (articles.isEmpty()) {
            item { Text("暂无公众号文章消息", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(articles, key = { message -> message.id }) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(message.text.ifBlank { "公众号文章" }, fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(message.resourceUrl ?: message.detail.orEmpty(), fontWeight = FontWeight.Normal)
                        }
                    )
                }
            }
        }
    }
}
