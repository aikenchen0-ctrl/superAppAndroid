package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.QuickPhraseUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.QuickPhraseUiState
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

@Composable
internal fun QuickPhrasePanel(
    state: QuickPhraseUiState,
    onEvent: (QuickPhraseUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextLabel("快捷语", 17.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                TextLabel("点击发送，长内容可直接编辑", 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
            }
            IconButton(onClick = { onEvent(QuickPhraseUiEvent.AddRequested) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新增快捷语", tint = OverlayTokens.toolIcon)
            }
        }

        if (state.editingIndex != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = OverlayTokens.quickPhraseRow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextLabel(
                        if (state.editingIndex == state.phrases.size) "新增快捷语" else "编辑快捷语",
                        12.sp,
                        color = OverlayTokens.panelPrimaryText,
                        maxLines = 1
                    )
                    PanelTextInput(
                        value = state.draft,
                        onValueChange = { value -> onEvent(QuickPhraseUiEvent.DraftChanged(value.take(120))) },
                        placeholder = "输入常用回复",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onEvent(QuickPhraseUiEvent.SaveRequested) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = "保存快捷语", tint = OverlayTokens.toolIconActive)
                        }
                        IconButton(onClick = { onEvent(QuickPhraseUiEvent.CancelRequested) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "取消编辑", tint = OverlayTokens.panelIcon)
                        }
                    }
                }
            }
        }

        if (state.phrases.isEmpty()) {
            TextLabel("还没有快捷语，点击右上角添加", 12.sp, color = OverlayTokens.panelSecondaryText, modifier = Modifier.padding(vertical = 24.dp), maxLines = 1)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(state.phrases, key = { index, phrase -> "$index-$phrase" }) { index, phrase ->
                    QuickPhraseRow(
                        phrase = phrase,
                        onSend = { onEvent(QuickPhraseUiEvent.SendRequested(index)) },
                        onEdit = { onEvent(QuickPhraseUiEvent.EditRequested(index)) },
                        onDelete = { onEvent(QuickPhraseUiEvent.DeleteRequested(index)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPhraseRow(phrase: String, onSend: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onSend,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFB),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, top = 8.dp, bottom = 8.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLabel(phrase, 12.sp, color = OverlayTokens.panelPrimaryText, modifier = Modifier.weight(1f), maxLines = 3)
            IconButton(onClick = onSend, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送快捷语", tint = OverlayTokens.toolIconActive)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑快捷语", tint = OverlayTokens.panelIcon)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "删除快捷语", tint = Color(0xFFB65353))
            }
        }
    }
}

@Composable
internal fun QuickPhrasePanel(
    phrases: List<String>,
    onSendPhrase: (String) -> Unit,
    onAddPhrase: (String) -> Unit,
    onUpdatePhrase: (Int, String) -> Unit,
    onDeletePhrase: (Int) -> Unit
) {
    var state by remember(phrases) { mutableStateOf(QuickPhraseUiState(phrases = phrases)) }
    QuickPhrasePanel(state, { event ->
        state = when (event) {
            QuickPhraseUiEvent.AddRequested -> state.copy(editingIndex = state.phrases.size, draft = "")
            is QuickPhraseUiEvent.EditRequested -> state.copy(editingIndex = event.index, draft = state.phrases.getOrNull(event.index).orEmpty())
            is QuickPhraseUiEvent.DraftChanged -> state.copy(draft = event.value)
            QuickPhraseUiEvent.SaveRequested -> {
                state.editingIndex?.let { index ->
                    if (index == state.phrases.size) onAddPhrase(state.draft) else onUpdatePhrase(index, state.draft)
                }
                state.copy(editingIndex = null, draft = "")
            }
            QuickPhraseUiEvent.CancelRequested -> state.copy(editingIndex = null, draft = "")
            is QuickPhraseUiEvent.DeleteRequested -> { onDeletePhrase(event.index); state.copy(editingIndex = null, draft = "") }
            is QuickPhraseUiEvent.SendRequested -> { state.phrases.getOrNull(event.index)?.let(onSendPhrase); state }
        }
    })
}
