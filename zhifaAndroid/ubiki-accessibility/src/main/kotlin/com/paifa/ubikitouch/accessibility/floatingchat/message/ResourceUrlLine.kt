package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun ResourceUrlLine(url: String?) {
    if (url.isNullOrBlank()) return
    TextLabel(url, 8.sp, color = OverlayTokens.linkText, maxLines = 1, shadow = OverlayTokens.imModuleTextShadow)
}
