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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                if (state.isDeleting) "删除快捷语" else "快捷语管理",
                17.sp,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                if (state.isDeleting) {
                    if (state.phrases.isEmpty()) "暂无快捷语" else "选择要删除的快捷语"
                } else {
                    "选择快捷语填入输入框，或新增、删除快捷语"
                },
                10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }

        if (state.isAdding) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = OverlayTokens.quickPhraseRow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextLabel("新增快捷语", 12.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                    PanelTextInput(
                        value = state.draft,
                        onValueChange = { value -> onEvent(QuickPhraseUiEvent.DraftChanged(value.take(120))) },
                        placeholder = "输入快捷语",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onEvent(QuickPhraseUiEvent.SaveRequested) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = "保存快捷语", tint = OverlayTokens.toolIconActive)
                        }
                        IconButton(onClick = { onEvent(QuickPhraseUiEvent.CancelRequested) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "取消新增", tint = OverlayTokens.panelIcon)
                        }
                    }
                }
            }
        }

        if (state.phrases.isEmpty()) {
            Spacer(modifier = Modifier.size(12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(state.phrases, key = { index, phrase -> "$index-$phrase" }) { index, phrase ->
                    QuickPhraseRow(
                        phrase = phrase,
                        destructive = state.isDeleting,
                        onClick = {
                            onEvent(
                                if (state.isDeleting) QuickPhraseUiEvent.DeleteRequested(index)
                                else QuickPhraseUiEvent.SendRequested(index)
                            )
                        }
                    )
                }
            }
        }

        if (!state.isDeleting) {
            QuickPhraseAction("新增快捷语") { onEvent(QuickPhraseUiEvent.AddRequested) }
            QuickPhraseAction("删除快捷语", destructive = state.phrases.isNotEmpty()) {
                onEvent(QuickPhraseUiEvent.DeleteManagerRequested)
            }
        } else {
            QuickPhraseAction("返回快捷语管理") { onEvent(QuickPhraseUiEvent.BackToManagerRequested) }
        }
        QuickPhraseAction("取消") { onEvent(QuickPhraseUiEvent.CancelRequested) }
    }
}

@Composable
private fun QuickPhraseRow(phrase: String, destructive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFB),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextLabel(
            phrase,
            12.sp,
            color = if (destructive) Color(0xFFB65353) else OverlayTokens.panelPrimaryText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            maxLines = 3
        )
    }
}

@Composable
private fun QuickPhraseAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FAFB), modifier = Modifier.fillMaxWidth()) {
        TextLabel(
            label,
            13.sp,
            color = if (destructive) Color(0xFFB65353) else OverlayTokens.toolIconActive,
            modifier = Modifier.padding(vertical = 11.dp),
            maxLines = 1
        )
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
            QuickPhraseUiEvent.AddRequested -> state.copy(isAdding = true, isDeleting = false, draft = "")
            QuickPhraseUiEvent.DeleteManagerRequested -> state.copy(isAdding = false, isDeleting = true, draft = "")
            QuickPhraseUiEvent.BackToManagerRequested -> state.copy(isAdding = false, isDeleting = false, draft = "")
            is QuickPhraseUiEvent.DraftChanged -> state.copy(draft = event.value)
            QuickPhraseUiEvent.SaveRequested -> {
                if (state.isAdding) onAddPhrase(state.draft)
                state.copy(isAdding = false, draft = "")
            }
            QuickPhraseUiEvent.CancelRequested -> state.copy(isAdding = false, isDeleting = false, draft = "")
            is QuickPhraseUiEvent.DeleteRequested -> { onDeletePhrase(event.index); state.copy(isAdding = false, draft = "") }
            is QuickPhraseUiEvent.SendRequested -> { state.phrases.getOrNull(event.index)?.let(onSendPhrase); state }
        }
    })
}
