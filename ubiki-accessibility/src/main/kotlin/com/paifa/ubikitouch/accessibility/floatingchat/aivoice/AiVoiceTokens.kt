package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AiVoiceTokens(
    val panelPadding: Dp = 12.dp,
    val itemSpacing: Dp = 10.dp,
    val cornerRadius: Dp = 8.dp,
    val controlHeight: Dp = 40.dp,
    val titleStyle: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    val itemTitleStyle: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    val descriptionStyle: TextStyle = TextStyle(fontSize = 10.sp)
    , val failureContainer: Color = Color(0xFFFFF1F0)
    , val failureBorder: Color = Color(0xFFC94A4A)
)
