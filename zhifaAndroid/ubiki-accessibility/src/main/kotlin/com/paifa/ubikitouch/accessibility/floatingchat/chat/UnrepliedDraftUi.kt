package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

@Composable
internal fun UnrepliedInlineDraft(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnrepliedDraftEditor(
        text = text,
        onTextChanged = onTextChanged,
        onSend = onSend,
        modifier = modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp)
    )
}

@Composable
internal fun UnrepliedBottomDraft(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnrepliedDraftEditor(
        text = text,
        onTextChanged = onTextChanged,
        onSend = onSend,
        modifier = modifier
            .background(OverlayTokens.control.copy(alpha = 0.96f), RoundedCornerShape(8.dp))
            .padding(6.dp)
    )
}

@Composable
internal fun SelectUnrepliedBottomDraftButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = if (selected) OverlayTokens.accent else OverlayTokens.secondaryText
        )
        Text(
            text = "编辑草稿",
            color = if (selected) OverlayTokens.accent else OverlayTokens.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun UnrepliedDraftEditor(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("回复草稿", fontSize = 12.sp) },
            singleLine = false,
            maxLines = 3,
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = OverlayTokens.control,
                unfocusedContainerColor = OverlayTokens.control
            )
        )
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "发送草稿"
            )
        }
    }
}
