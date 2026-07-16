package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun FloatingChatMediaStatusPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OverlayTokens.mediaActionStatus)
            .border(1.dp, OverlayTokens.mediaActionBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(text, 9.sp, color = OverlayTokens.primaryText, weight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}
