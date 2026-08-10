package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundColorRgb
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundOpacityPercent
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBlurRadiusDp

internal fun floatingChatOverlayUsesRuntimeSizedCompositionSections(): Boolean = true

internal fun floatingChatBlankAreaClickCollapsesOverlay(): Boolean = false

internal fun floatingChatBackKeyCollapsesOverlay(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboard(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboardWhenInputNotFocused(): Boolean = true

internal fun floatingChatBlankAreaClickResetsBottomPanel(): Boolean = true

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
    return background(
        baseColor.copy(alpha = (opacity * (0.82f + blurStrength * 0.10f)).coerceIn(0f, 1f))
    )
}
