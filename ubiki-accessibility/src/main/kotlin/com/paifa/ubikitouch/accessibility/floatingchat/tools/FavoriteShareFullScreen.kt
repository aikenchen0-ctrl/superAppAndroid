package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import kotlinx.coroutines.launch

internal const val FavoriteShareStatusBarHeightDp = 30

internal enum class FavoriteShareTab(val label: String) {
    Recent("最近使用"),
    Media("图片与视频"),
    File("文件"),
    Link("链接"),
    Text("文本"),
    Chat("聊天记录")
}

/**
 * iOS FavoriteLibraryViewController 的 Android M3 全屏工作区。
 * 测试流程：右侧点击“收藏分享”，搜索并切换分类，点击收藏预览或长按进入多选，执行转发/删除。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteShareFullScreen(
    items: List<FavoriteCollectionItem>,
    multiSelectMode: Boolean,
    selectedItemIds: Map<String, Boolean>,
    onPreviewItem: (FavoriteCollectionItem) -> Unit,
    onLongPressItem: (FavoriteCollectionItem, androidx.compose.ui.geometry.Rect?) -> Unit,
    onToggleSelection: (FavoriteCollectionItem) -> Unit,
    onForwardSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { FavoriteShareTab.entries.size })
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 页面只提供内容，悬浮聊天根容器统一负责显示和关闭时的实体位移动画。
        FloatingWorkspaceTopAppBar(
            title = if (multiSelectMode) "已选择 ${selectedItemIds.count { it.value }} 项" else "收藏分享",
            onBack = onBack
        ) {
                if (multiSelectMode) {
                    IconButton(onClick = onForwardSelected, enabled = selectedItemIds.any { it.value }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "转发已选收藏")
                    }
                    IconButton(onClick = onDeleteSelected, enabled = selectedItemIds.any { it.value }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除已选收藏")
                    }
                    IconButton(onClick = onCancelSelection) {
                        Icon(Icons.Filled.Check, contentDescription = "结束多选")
                    }
                }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("搜索收藏") }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            FavoriteShareTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val tab = FavoriteShareTab.entries[page]
            val visibleItems = remember(items, query, tab) {
                items.filter { it.matchesFavoriteShareTab(tab) && it.matchesFavoriteShareQuery(query) }
            }
            if (visibleItems.isEmpty()) {
                Text(
                    text = "暂无收藏",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleItems, key = { it.messageId }) { item ->
                        FavoriteShareRow(
                            item = item,
                            selected = selectedItemIds[item.messageId] == true,
                            selectionMode = multiSelectMode,
                            onClick = { if (multiSelectMode) onToggleSelection(item) else onPreviewItem(item) },
                            onLongPress = { onLongPressItem(item, null) }
                        )
                    }
                }
            }
        }
    }
}

private fun FavoriteCollectionItem.matchesFavoriteShareQuery(query: String): Boolean {
    val value = query.trim()
    return value.isEmpty() || title.contains(value, true) || description.contains(value, true) || source.contains(value, true)
}

private fun FavoriteCollectionItem.matchesFavoriteShareTab(tab: FavoriteShareTab): Boolean {
    return when (tab) {
        FavoriteShareTab.Recent -> true
        FavoriteShareTab.Media -> type in setOf(
            FloatingChatMessageType.ImageThumbnail,
            FloatingChatMessageType.VideoPreview,
            FloatingChatMessageType.CapturedPhoto,
            FloatingChatMessageType.StickerGif
        )
        FavoriteShareTab.File -> type == FloatingChatMessageType.FilePreview
        FavoriteShareTab.Link -> type in setOf(
            FloatingChatMessageType.WebLink,
            FloatingChatMessageType.MiniProgramLink,
            FloatingChatMessageType.ContactLink
        )
        FavoriteShareTab.Text -> type in setOf(
            FloatingChatMessageType.Text,
            FloatingChatMessageType.MixedText,
            FloatingChatMessageType.Quote
        )
        FavoriteShareTab.Chat -> type == FloatingChatMessageType.ChatHistory
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteShareRow(
    item: FavoriteCollectionItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = favoriteShareIcon(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text(item.source, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Star,
                    contentDescription = if (selected) "已选择" else "未选择",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun favoriteShareIcon(type: FloatingChatMessageType): ImageVector = when (type) {
    FloatingChatMessageType.ImageThumbnail,
    FloatingChatMessageType.CapturedPhoto,
    FloatingChatMessageType.StickerGif -> Icons.Filled.Image
    FloatingChatMessageType.VideoPreview -> Icons.Filled.VideoLibrary
    FloatingChatMessageType.FilePreview -> Icons.AutoMirrored.Filled.Article
    FloatingChatMessageType.WebLink,
    FloatingChatMessageType.MiniProgramLink,
    FloatingChatMessageType.ContactLink -> Icons.Filled.Link
    else -> Icons.Filled.Star
}
