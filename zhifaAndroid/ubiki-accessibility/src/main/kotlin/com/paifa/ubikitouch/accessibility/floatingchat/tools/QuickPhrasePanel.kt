package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.QuickPhraseUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.QuickPhraseUiState

@Composable
internal fun QuickPhrasePanel(
    state: QuickPhraseUiState,
    onEvent: (QuickPhraseUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLabel("快捷语", 13.sp, maxLines = 1)
            Text("新增", modifier = Modifier.clickable { onEvent(QuickPhraseUiEvent.AddRequested) }.padding(6.dp))
        }
        if (state.editingIndex != null) {
            BasicTextField(
                value = state.draft,
                onValueChange = { onEvent(QuickPhraseUiEvent.DraftChanged(it)) },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            Text("保存", modifier = Modifier.clickable { onEvent(QuickPhraseUiEvent.SaveRequested) }.padding(6.dp))
        }
        state.phrases.forEachIndexed { index, phrase ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onEvent(QuickPhraseUiEvent.SendRequested(index)) }.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phrase, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("编辑", modifier = Modifier.clickable { onEvent(QuickPhraseUiEvent.EditRequested(index)) }.padding(4.dp))
                Text("删除", modifier = Modifier.clickable { onEvent(QuickPhraseUiEvent.DeleteRequested(index)) }.padding(4.dp))
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
            QuickPhraseUiEvent.AddRequested -> state.copy(editingIndex = phrases.size, draft = "")
            is QuickPhraseUiEvent.EditRequested -> state.copy(editingIndex = event.index, draft = phrases.getOrNull(event.index).orEmpty())
            is QuickPhraseUiEvent.DraftChanged -> state.copy(draft = event.value)
            QuickPhraseUiEvent.SaveRequested -> {
                state.editingIndex?.let { index ->
                    if (index == phrases.size) onAddPhrase(state.draft) else onUpdatePhrase(index, state.draft)
                }
                state.copy(editingIndex = null, draft = "")
            }
            is QuickPhraseUiEvent.DeleteRequested -> { onDeletePhrase(event.index); state }
            is QuickPhraseUiEvent.SendRequested -> { phrases.getOrNull(event.index)?.let(onSendPhrase); state }
        }
    })
}
