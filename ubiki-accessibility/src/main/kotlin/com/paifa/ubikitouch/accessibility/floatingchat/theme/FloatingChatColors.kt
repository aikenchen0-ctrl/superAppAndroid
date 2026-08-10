package com.paifa.ubikitouch.accessibility.floatingchat.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.accessibility.floatingchat.contract.StatusTone

@Immutable
data class FloatingChatColors(
    val surface: Color,
    val elevatedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val error: Color,
    val info: Color,
    val success: Color,
    val warning: Color,
    val outline: Color
)

internal val FloatingChatLightColors = FloatingChatColors(
    surface = Color(0xF2D9E4E8),
    elevatedSurface = Color(0xE8EDF4F5),
    primaryText = Color(0xF2F8FCFF),
    secondaryText = Color(0xD9F1F7FA),
    accent = Color(0xFF7DCC16),
    error = Color(0xFFE27E76),
    info = Color(0xFF2E6F96),
    success = Color(0xFF347A55),
    warning = Color(0xFF8A6718),
    outline = Color(0x82F8FCFF)
)

internal val FloatingChatDarkColors = FloatingChatColors(
    surface = Color(0xFF172328),
    elevatedSurface = Color(0xFF213238),
    primaryText = Color(0xFFEAF2F4),
    secondaryText = Color(0xFFB8C8CD),
    accent = Color(0xFF8BCB31),
    error = Color(0xFFFF9292),
    info = Color(0xFF7EB7D8),
    success = Color(0xFF79C69A),
    warning = Color(0xFFE0BC61),
    outline = Color(0xFF4D626A)
)

internal fun FloatingChatColors.statusColor(tone: StatusTone): Color {
    return when (tone) {
        StatusTone.Neutral -> secondaryText
        StatusTone.Info -> info
        StatusTone.Success -> success
        StatusTone.Warning -> warning
        StatusTone.Error -> error
    }
}
