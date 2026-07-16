package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
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
import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiState

@Composable
internal fun LocationPanel(
    state: LocationUiState,
    onEvent: (LocationUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth().background(Color(0xFFF7F7F7))) {
        item {
            TextLabel("选择位置", 13.sp, color = Color(0xFF222222), modifier = Modifier.padding(14.dp), maxLines = 1)
        }
        if (state.error != null) {
            item { TextLabel(state.error, 12.sp, color = Color(0xFFE45858), modifier = Modifier.padding(14.dp), maxLines = 2) }
        }
        items(state.options) { option ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onEvent(LocationUiEvent.SendRequested(option)) }.padding(14.dp)) {
                TextLabel(option, 13.sp, color = Color(0xFF222222), maxLines = 1)
            }
        }
        item {
            TextLabel("刷新位置", 12.sp, color = Color(0xFF49679E), modifier = Modifier.clickable { onEvent(LocationUiEvent.RefreshRequested) }.padding(14.dp), maxLines = 1)
        }
    }
}
