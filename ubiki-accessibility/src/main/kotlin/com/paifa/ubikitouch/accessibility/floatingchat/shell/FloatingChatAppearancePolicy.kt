package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundColorRgb
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundOpacityPercent
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBlurRadiusDp

internal fun floatingChatOverlayUsesRuntimeSizedCompositionSections(): Boolean = true

internal fun floatingChatBlankAreaClickCollapsesOverlay(): Boolean = false

internal fun floatingChatBackKeyCollapsesOverlay(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboard(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboardWhenInputNotFocused(): Boolean = false

internal fun floatingChatAppearancePanelPlacement(): String = "after_global_controls"

internal fun floatingChatAppearanceSettingsPreview(): Boolean = true

internal fun floatingChatOverlayUsesFrostedBackground(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBackgroundOpacity(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBlurRadius(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBackgroundColor(): Boolean = true

internal fun floatingChatOverlayFallsBackWhenBackdropBlurUnavailable(): Boolean = true

internal fun floatingChatOverlayRefreshRecreatesCurrentStateForAppearanceChanges(): Boolean = true

internal fun floatingChatAppearanceRefreshUsesDebouncedSingleRunnable(): Boolean = true

internal fun floatingChatCollapseRemovesOverlayInsteadOfShowingButton(): Boolean = false

internal fun floatingChatCollapseRetainsExpandedComposition(): Boolean = true

internal fun floatingChatExpandReusesRetainedComposeView(): Boolean = true

internal fun floatingChatRetainedCollapsedOverlayIsNotTouchable(): Boolean = true

internal fun floatingChatCollapsedStateShowsFloatingButton(): Boolean = false

internal fun Modifier.floatingChatFrostedBackdrop(
    enabled: Boolean,
    opacityPercent: Int,
    blurRadiusDp: Int,
    backgroundColorRgb: Int
): Modifier {
    if (!enabled) return background(Color.Transparent)
    val opacity = sanitizeFloatingChatBackgroundOpacityPercent(opacityPercent) / 100f
    val blurStrength = sanitizeFloatingChatBlurRadiusDp(blurRadiusDp) / 40f
    val baseColor = Color(0xFF000000 or sanitizeFloatingChatBackgroundColorRgb(backgroundColorRgb).toLong())
    return drawWithContent {
        drawRect(
            color = baseColor.copy(alpha = opacity * 0.82f)
        )
        drawRect(
            color = Color(0xFFFFFFFF).copy(alpha = opacity * (0.10f + blurStrength * 0.10f))
        )
        drawCircle(
            color = baseColor.lightenedForFrostedBackdrop().copy(alpha = opacity * blurStrength * 0.22f),
            radius = size.minDimension * 0.38f,
            center = Offset(size.width * 0.22f, size.height * 0.18f)
        )
        drawCircle(
            color = baseColor.darkenedForFrostedBackdrop().copy(alpha = opacity * blurStrength * 0.16f),
            radius = size.minDimension * 0.32f,
            center = Offset(size.width * 0.82f, size.height * 0.74f)
        )
        drawContent()
    }
}

private fun Color.lightenedForFrostedBackdrop(): Color {
    return Color(
        red = (red + (1f - red) * 0.58f).coerceIn(0f, 1f),
        green = (green + (1f - green) * 0.58f).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * 0.58f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun Color.darkenedForFrostedBackdrop(): Color {
    return Color(
        red = (red * 0.82f).coerceIn(0f, 1f),
        green = (green * 0.82f).coerceIn(0f, 1f),
        blue = (blue * 0.82f).coerceIn(0f, 1f),
        alpha = 1f
    )
}
