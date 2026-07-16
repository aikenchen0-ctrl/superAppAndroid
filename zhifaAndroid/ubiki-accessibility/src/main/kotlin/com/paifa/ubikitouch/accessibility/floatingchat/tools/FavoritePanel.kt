package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel

data class FavoriteUiItem(val id: String, val title: String, val subtitle: String = "")

@Composable
internal fun FavoritePanel(
    items: List<FavoriteUiItem>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { TextLabel("收藏", 14.sp, color = Color(0xFF222222), modifier = Modifier.padding(14.dp), maxLines = 1) }
        items(items, key = { it.id }) { item ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) }.padding(14.dp)) {
                TextLabel(item.title, 13.sp, color = Color(0xFF222222), maxLines = 1)
                if (item.subtitle.isNotBlank()) TextLabel(item.subtitle, 11.sp, color = Color(0xFF888888), maxLines = 2)
                TextLabel(if (item.id in selectedIds) "已选" else "选择", 11.sp, color = Color(0xFF49679E), modifier = Modifier.clickable { onToggleSelection(item.id) }.padding(top = 4.dp), maxLines = 1)
            }
        }
    }
}
