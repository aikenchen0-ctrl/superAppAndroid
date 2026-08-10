package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal data class FavoriteUiItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val source: String = "",
    val type: FloatingChatMessageType = FloatingChatMessageType.Text
)

@Composable
internal fun FavoritePanel(
    items: List<FavoriteUiItem>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FavoriteFilter.Recent) }
    val visibleItems = remember(items, query, filter) {
        items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.subtitle.contains(query, ignoreCase = true) ||
                item.source.contains(query, ignoreCase = true)
            val matchesFilter = filter == FavoriteFilter.Recent ||
                item.type == FloatingChatMessageType.ImageThumbnail ||
                item.type == FloatingChatMessageType.VideoPreview
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayTokens.momentsBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLabel("收藏", 17.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (selectionMode) {
                TextLabel("已选 ${selectedIds.size}", 11.sp, color = OverlayTokens.momentsName, maxLines = 1)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEAF0F2))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = "搜索收藏", tint = OverlayTokens.panelIcon, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            PanelTextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索收藏内容",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FavoriteFilterChip("最近使用", filter == FavoriteFilter.Recent) { filter = FavoriteFilter.Recent }
            FavoriteFilterChip("图片与视频", filter == FavoriteFilter.Media) { filter = FavoriteFilter.Media }
        }
        if (visibleItems.isEmpty()) {
            TextLabel("暂无收藏", 12.sp, color = OverlayTokens.panelSecondaryText, modifier = Modifier.padding(vertical = 28.dp), maxLines = 1)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visibleItems, key = { item -> item.id }) { item ->
                    FavoriteRow(
                        item = item,
                        selected = item.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) onToggleSelection(item.id) else onOpen(item.id) },
                        onLongPress = { onLongPress(item.id) }
                    )
                }
            }
        }
    }
}

private enum class FavoriteFilter { Recent, Media }

@Composable
private fun FavoriteFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = if (selected) OverlayTokens.inputFocus else Color(0xFFE5ECEE)
    ) {
        TextLabel(
            label,
            10.sp,
            color = if (selected) OverlayTokens.primaryText else OverlayTokens.panelSecondaryText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoriteRow(
    item: FavoriteUiItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFB))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(favoriteRowIconColor(item.type)),
            contentAlignment = Alignment.Center
        ) {
            Icon(favoriteRowIcon(item.type), contentDescription = null, tint = Color(0xFFF5FAFB), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextLabel(item.title, 13.sp, color = Color(0xFF1C3036), maxLines = 2)
            if (item.subtitle.isNotBlank()) TextLabel(item.subtitle, 11.sp, color = Color(0xFF6C7B80), maxLines = 2)
            if (item.source.isNotBlank()) TextLabel(item.source, 10.sp, color = Color(0xFF9AA5A8), maxLines = 1)
        }
        if (selectionMode) {
            IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) OverlayTokens.inputFocus else Color(0xFFE1E8EA)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = "已选择", tint = OverlayTokens.primaryText, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun favoriteRowIcon(type: FloatingChatMessageType): ImageVector = when (type) {
    FloatingChatMessageType.ImageThumbnail -> Icons.Filled.Image
    FloatingChatMessageType.VideoPreview -> Icons.Filled.VideoLibrary
    else -> Icons.Filled.Star
}

private fun favoriteRowIconColor(type: FloatingChatMessageType): Color = when (type) {
    FloatingChatMessageType.ImageThumbnail -> Color(0xFF4D9AA5)
    FloatingChatMessageType.VideoPreview -> Color(0xFF7659A8)
    else -> Color(0xFFE29B36)
}
