package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.launch

private const val ChannelsVideoAnimationDurationMillis = 260

internal enum class ChannelsVideoFullScreenTab(val label: String) {
    Select("选择作品"),
    CurrentConversation("当前会话")
}

/**
 * 对应 iOS `channelsVideo` 工具消息的 Android Material 3 全屏悬浮页。
 * iOS 右侧入口直接发送视频号视频演示卡片，不调用 Finder 发布或其他 OpenAPI；此页仅使用
 * 已有悬浮根视图，避免创建 Dialog 或新 Window 引起 BadTokenException。
 * 测试流程：点击右侧“视频号视频”，切换两个分页，点击“发送”后确认当前会话新增
 * ChannelsVideo 卡片；点击左上返回，确认页面以向下滑动动画关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelsVideoFullScreen(
    channelsVideos: List<FloatingChatMessage>,
    onSendChannelsVideo: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ChannelsVideoFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            translationY.snapTo(pageHeightPx)
            translationY.animateTo(0f, tween(ChannelsVideoAnimationDurationMillis))
            entered = true
        }
    }
    fun exit(afterExit: () -> Unit) {
        if (exiting) return
        exiting = true
        scope.launch {
            translationY.animateTo(pageHeightPx, tween(ChannelsVideoAnimationDurationMillis))
            afterExit()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { this.translationY = translationY.value }
    ) {
        Spacer(Modifier.height(30.dp))
        TopAppBar(
            title = {
                Text(
                    text = "视频号视频",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
            },
            navigationIcon = {
                IconButton(onClick = { exit(onBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            ChannelsVideoFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (ChannelsVideoFullScreenTab.entries[page]) {
                ChannelsVideoFullScreenTab.Select -> ChannelsVideoSelectionPage(
                    onSendChannelsVideo = { exit(onSendChannelsVideo) }
                )
                ChannelsVideoFullScreenTab.CurrentConversation -> ChannelsVideoHistoryPage(channelsVideos)
            }
        }
    }
}

@Composable
private fun ChannelsVideoSelectionPage(onSendChannelsVideo: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "视频号视频",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                    headlineContent = { Text("开业现场回放", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("视频号视频 · 00:18", fontWeight = FontWeight.Normal) },
                    trailingContent = {
                        FilledTonalButton(onClick = onSendChannelsVideo) {
                            Text("发送", fontWeight = FontWeight.Normal)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelsVideoHistoryPage(channelsVideos: List<FloatingChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "当前会话",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        if (channelsVideos.isEmpty()) {
            item { Text("暂无视频号视频", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(channelsVideos, key = { message -> message.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = message.text.ifBlank { "视频号视频" },
                                fontWeight = FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Text(
                                text = message.detail ?: "视频号视频",
                                fontWeight = FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    }
}
