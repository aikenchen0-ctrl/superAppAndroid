package com.paifa.ubikitouch.accessibility.floatingchat.finder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.scrm.ScrmException
import com.paifa.ubikitouch.accessibility.scrm.toUserMessage

internal data class FinderSession(
    val deviceUuid: String,
    val weChatId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

internal data class FinderOperationUiState(
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null
)

internal fun Throwable.toFinderUserMessage(): String {
    return (this as? ScrmException)?.toUserMessage()
        ?: message?.takeIf(String::isNotBlank)
        ?: "视频号请求失败"
}

@Composable
internal fun FinderPanelHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextLabel(
            text = title,
            size = 14.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        subtitle?.let {
            TextLabel(
                text = it,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
internal fun FinderTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        singleLine = singleLine,
        label = { TextLabel(label, 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = OverlayTokens.panelPrimaryText),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OverlayTokens.accent,
            unfocusedBorderColor = OverlayTokens.resourcePanelBorder,
            focusedContainerColor = OverlayTokens.panel,
            unfocusedContainerColor = OverlayTokens.panel,
            cursorColor = OverlayTokens.accent
        )
    )
}

@Composable
internal fun FinderActionButton(
    label: String,
    enabled: Boolean,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        danger -> Color(0xFFB65757)
        accent -> OverlayTokens.accent
        else -> OverlayTokens.resourcePanel
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = if (accent || danger) Color.White else OverlayTokens.panelPrimaryText,
            disabledContainerColor = OverlayTokens.resourcePanel.copy(alpha = 0.55f),
            disabledContentColor = OverlayTokens.panelSecondaryText
        ),
        modifier = Modifier.heightIn(min = 34.dp)
    ) {
        TextLabel(
            text = label,
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = if (accent || danger) Color.White else OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
    }
}

@Composable
internal fun FinderOperationStatus(state: FinderOperationUiState) {
    state.status?.let { status ->
        TextLabel(
            text = status,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 3,
            lineHeight = 13.sp,
            modifier = Modifier.fillMaxWidth().background(OverlayTokens.resourcePanel, RoundedCornerShape(6.dp)).padding(7.dp)
        )
    }
    state.error?.let { error ->
        TextLabel(
            text = error,
            size = 10.sp,
            color = Color(0xFFB65757),
            maxLines = 3,
            lineHeight = 13.sp,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF1EF), RoundedCornerShape(6.dp)).padding(7.dp)
        )
    }
}

@Composable
internal fun FinderPanelSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().background(OverlayTokens.panel, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
internal fun FinderInlineActions(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
