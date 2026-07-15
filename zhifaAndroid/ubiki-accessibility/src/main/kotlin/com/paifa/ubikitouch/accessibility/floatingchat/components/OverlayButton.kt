package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayButtonEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayButtonProps
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayButtonStyle
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatThemeValues

@Composable
internal fun OverlayButton(
    props: OverlayButtonProps,
    onEvent: (OverlayButtonEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FloatingChatThemeValues.colors
    val dimensions = FloatingChatThemeValues.dimensions
    val content: @Composable () -> Unit = {
        Text(
            text = props.label,
            style = FloatingChatThemeValues.typography.label
        )
    }
    val buttonModifier = modifier.heightIn(min = dimensions.controlHeight)

    when (props.style) {
        OverlayButtonStyle.Primary -> Button(
            onClick = { onEvent(OverlayButtonEvent.Clicked) },
            enabled = props.enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.surface
            ),
            content = { content() }
        )
        OverlayButtonStyle.Secondary,
        OverlayButtonStyle.Destructive -> OutlinedButton(
            onClick = { onEvent(OverlayButtonEvent.Clicked) },
            enabled = props.enabled,
            modifier = buttonModifier,
            border = BorderStroke(
                1.dp,
                if (props.style == OverlayButtonStyle.Destructive) colors.error else colors.outline
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (props.style == OverlayButtonStyle.Destructive) colors.error else colors.primaryText
            ),
            content = { content() }
        )
    }
}
