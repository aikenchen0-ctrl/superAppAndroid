package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import kotlinx.coroutines.launch

internal enum class VideoShortFullScreenTab(val label: String, val title: String, val actionLabel: String) {
    Video("视频", "发送视频", "选择视频"),
    ShortVideo("短视频", "发送短视频", "选择短视频")
}

/**
 * iOS「视频 / 短视频」对应的 Android Material 3 全屏悬浮页。
 *
 * iOS 此功能走本地媒体选择并生成 video 消息，没有额外 OpenAPI；Android 复用已接入的
 * 视频选择和消息落库链路。测试流程：从右侧「视频」进入，切换两个分页并选择媒体，确认仅
 * 接受视频且聊天中新增 VideoPreview；点击返回确认页面向下退出且未创建 Dialog 或 Window。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoShortFullScreen(
    onPickVideo: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { VideoShortFullScreenTab.entries.size })
    // 复用已有 accessibility overlay 根视图，不创建 Dialog 或新 Window，避免 BadTokenException。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = "视频/短视频",
            onBack = onBack
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            VideoShortFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            VideoShortSelectionPage(
                tab = VideoShortFullScreenTab.entries[page],
                onPickVideo = onPickVideo
            )
        }
    }
}

@Composable
private fun VideoShortSelectionPage(
    tab: VideoShortFullScreenTab,
    onPickVideo: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = tab.title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    headlineContent = { Text(tab.actionLabel, fontWeight = FontWeight.Normal) },
                    supportingContent = {
                        Text("从设备选择后发送到当前聊天", fontWeight = FontWeight.Normal)
                    },
                    trailingContent = {
                        FilledTonalButton(onClick = onPickVideo) {
                            Text(tab.actionLabel, fontWeight = FontWeight.Normal)
                        }
                    }
                )
            }
        }
    }
}
