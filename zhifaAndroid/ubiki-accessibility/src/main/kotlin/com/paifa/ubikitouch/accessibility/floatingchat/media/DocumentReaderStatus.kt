package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun DocumentReaderStatus(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TextLabel(text, 13.sp, color = OverlayTokens.mediaSheetMutedText, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}
