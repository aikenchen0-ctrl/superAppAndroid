package com.paifa.ubikitouch.accessibility.floatingchat.moments

import androidx.compose.ui.graphics.Color

internal fun scrmMomentAvatarColor(key: String): Color {
    val palette = listOf(0xFF607699, 0xFF597D66, 0xFF8A6A42, 0xFF7B627D, 0xFF4F7F8D)
    return Color(palette[(key.hashCode() and Int.MAX_VALUE) % palette.size])
}
