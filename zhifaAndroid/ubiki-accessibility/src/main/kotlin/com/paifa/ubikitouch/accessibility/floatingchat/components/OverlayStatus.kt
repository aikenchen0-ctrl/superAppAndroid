package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayStatusProps
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatThemeValues
import com.paifa.ubikitouch.accessibility.floatingchat.theme.statusColor

@Composable
internal fun OverlayStatus(
    props: OverlayStatusProps,
    modifier: Modifier = Modifier
) {
    if (!props.visible) return
    Text(
        text = props.message,
        color = FloatingChatThemeValues.colors.statusColor(props.tone),
        style = FloatingChatThemeValues.typography.status,
        modifier = modifier
    )
}
