package com.paifa.ubikitouch.accessibility.floatingchat.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class FloatingChatDimensions(
    val controlHeight: Dp = 40.dp,
    val compactControlHeight: Dp = 32.dp,
    val avatarSize: Dp = 40.dp,
    val cornerRadius: Dp = 8.dp,
    val contentPadding: Dp = 12.dp,
    val itemSpacing: Dp = 8.dp
)
