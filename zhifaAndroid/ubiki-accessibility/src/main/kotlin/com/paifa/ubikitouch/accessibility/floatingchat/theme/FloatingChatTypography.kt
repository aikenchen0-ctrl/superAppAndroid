package com.paifa.ubikitouch.accessibility.floatingchat.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class FloatingChatTypography(
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    val body: TextStyle = TextStyle(fontSize = 13.sp),
    val status: TextStyle = TextStyle(fontSize = 11.sp),
    val avatar: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
)
