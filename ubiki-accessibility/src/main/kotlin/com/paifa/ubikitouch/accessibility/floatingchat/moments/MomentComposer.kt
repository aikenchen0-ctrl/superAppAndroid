package com.paifa.ubikitouch.accessibility.floatingchat.moments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MomentComposer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        BasicTextField(draft, onDraftChanged, modifier = Modifier.fillMaxWidth().padding(8.dp), minLines = 3)
        Text("发布", color = Color(0xFF49679E), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
    }
}
