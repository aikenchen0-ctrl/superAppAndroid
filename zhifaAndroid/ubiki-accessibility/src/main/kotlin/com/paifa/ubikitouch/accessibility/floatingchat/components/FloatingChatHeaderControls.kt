package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel

@Composable
internal fun FloatingChatUnreadDot() {
    Box(Modifier.size(18.dp).clip(CircleShape).background(OverlayTokens.alertMuted), contentAlignment = Alignment.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(OverlayTokens.alertCore))
    }
}

@Composable
internal fun FloatingChatHeaderIcon(label: String, onClick: (() -> Unit)? = null) {
    CompactInteractiveSize {
        FilledTonalIconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier.size(25.dp).border(1.dp, OverlayTokens.hairline, CircleShape),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = OverlayTokens.control,
                contentColor = OverlayTokens.primaryText,
                disabledContainerColor = OverlayTokens.control,
                disabledContentColor = OverlayTokens.primaryText
            )
        ) {
            TextLabel(label, 11.sp, color = OverlayTokens.primaryText, weight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
