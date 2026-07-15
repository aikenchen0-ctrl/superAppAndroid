package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.GestureAction

internal enum class GestureInputMode(val id: String) {
    Auto("auto"),
    NativeTouchInteraction("native_touch_interaction"),
    SecureSlimOverlay("secure_slim_overlay"),
    SlimOverlayFallback("slim_overlay_fallback");

    companion object {
        fun fromId(id: String?): GestureInputMode {
            return entries.firstOrNull { it.id == id } ?: Auto
        }
    }
}

internal enum class ResolvedGestureInputMode {
    NativeTouchInteraction,
    SecureSlimOverlay,
    SlimOverlayFallback
}

internal fun resolveGestureInputMode(
    requestedMode: GestureInputMode,
    sdkInt: Int,
    nativeTouchInteractionAvailable: Boolean,
    secureTakeoverApplied: Boolean
): ResolvedGestureInputMode {
    return when (requestedMode) {
        GestureInputMode.NativeTouchInteraction -> {
            if (nativeTouchInteractionAvailable && sdkInt >= NATIVE_TOUCH_INTERACTION_MIN_SDK) {
                ResolvedGestureInputMode.NativeTouchInteraction
            } else {
                overlayModeForSecureTakeover(secureTakeoverApplied)
            }
        }
        GestureInputMode.SecureSlimOverlay -> {
            if (secureTakeoverApplied) {
                ResolvedGestureInputMode.SecureSlimOverlay
            } else {
                ResolvedGestureInputMode.SlimOverlayFallback
            }
        }
        GestureInputMode.SlimOverlayFallback -> ResolvedGestureInputMode.SlimOverlayFallback
        GestureInputMode.Auto -> {
            if (nativeTouchInteractionAvailable && sdkInt >= NATIVE_TOUCH_INTERACTION_MIN_SDK) {
                ResolvedGestureInputMode.NativeTouchInteraction
            } else {
                overlayModeForSecureTakeover(secureTakeoverApplied)
            }
        }
    }
}

internal fun shouldCreateGestureOverlayWindows(mode: ResolvedGestureInputMode): Boolean {
    return mode != ResolvedGestureInputMode.NativeTouchInteraction
}

internal data class NativeTouchInteractionEligibility(
    val sdkInt: Int,
    val requestedMode: GestureInputMode,
    val runtimeFailed: Boolean,
    val globalEnabled: Boolean,
    val screenInteractive: Boolean,
    val packageBlocked: Boolean,
    val paused: Boolean,
    val landscapeDisabled: Boolean,
    val keyboardDisabled: Boolean
)

internal fun shouldRequestNativeTouchInteraction(
    eligibility: NativeTouchInteractionEligibility,
    floatingChatExpanded: Boolean,
    externalActivityVisible: Boolean = false
): Boolean {
    return eligibility.sdkInt >= NATIVE_TOUCH_INTERACTION_MIN_SDK &&
        eligibility.requestedMode != GestureInputMode.SlimOverlayFallback &&
        eligibility.requestedMode != GestureInputMode.SecureSlimOverlay &&
        !eligibility.runtimeFailed &&
        eligibility.globalEnabled &&
        eligibility.screenInteractive &&
        !eligibility.packageBlocked &&
        !eligibility.paused &&
        !eligibility.landscapeDisabled &&
        !eligibility.keyboardDisabled
}

internal fun nativeTouchRecoveryNeeded(
    floatingChatExpanded: Boolean,
    controllerRunning: Boolean,
    externalActivityVisible: Boolean = false
): Boolean {
    return !controllerRunning
}

internal fun floatingChatOwnsGestureSurface(
    floatingChatExpanded: Boolean,
    externalActivityVisible: Boolean
): Boolean {
    return floatingChatExpanded && !externalActivityVisible
}

internal fun edgeGestureOverlayWindowsAllowed(
    floatingChatExpanded: Boolean,
    externalActivityVisible: Boolean = false
): Boolean {
    return !floatingChatOwnsGestureSurface(floatingChatExpanded, externalActivityVisible)
}

internal fun shouldRestoreFloatingChatFromExternalActivity(
    action: GestureAction,
    externalActivityVisible: Boolean
): Boolean {
    return action == GestureAction.ExpandFloatingChat && externalActivityVisible
}

internal fun nativeTouchInteractionEdgeStartTargetDp(configuredThicknessDp: Int): Int {
    return gestureOverlayThicknessDp(configuredThicknessDp)
        .coerceAtLeast(NATIVE_TOUCH_INTERACTION_EDGE_START_TARGET_DP)
}

private fun overlayModeForSecureTakeover(secureTakeoverApplied: Boolean): ResolvedGestureInputMode {
    return if (secureTakeoverApplied) {
        ResolvedGestureInputMode.SecureSlimOverlay
    } else {
        ResolvedGestureInputMode.SlimOverlayFallback
    }
}

internal const val NATIVE_TOUCH_INTERACTION_MIN_SDK = 33
private const val NATIVE_TOUCH_INTERACTION_EDGE_START_TARGET_DP = 24
