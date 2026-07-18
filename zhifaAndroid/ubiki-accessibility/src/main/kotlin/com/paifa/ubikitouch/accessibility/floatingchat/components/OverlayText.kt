package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
@Composable
internal fun CompactInteractiveSize(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        content = content
    )
}

@Composable
internal fun TextLabel(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = OverlayTokens.primaryText,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    shadow: Shadow? = null
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle.Default.copy(
            color = color,
            fontSize = size,
            fontWeight = weight,
            lineHeight = lineHeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            shadow = shadow
        )
    )
}
