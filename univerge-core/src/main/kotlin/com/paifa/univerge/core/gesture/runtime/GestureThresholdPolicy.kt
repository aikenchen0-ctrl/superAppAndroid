package com.paifa.univerge.core.gesture.runtime

/**
 * Single source of truth for the effective side-gesture distances used by
 * every Android input adapter. Preferences remain expressed in dp; the
 * response ratio is an input-capture policy, not a platform-specific detail.
 */
fun configuredSideGestureThresholdsDp(
    shortPullDistanceDp: Float,
    longPullDistanceDp: Float
): SideGestureThresholds {
    val shortConfigured = shortPullDistanceDp
        .takeIf { it.isFinite() }
        ?.coerceIn(SIDE_SHORT_THRESHOLD_MIN_DP, SIDE_SHORT_THRESHOLD_MAX_DP)
        ?: SIDE_SHORT_THRESHOLD_MIN_DP
    val longConfigured = longPullDistanceDp
        .takeIf { it.isFinite() }
        ?.coerceIn(shortConfigured + SIDE_LONG_THRESHOLD_GAP_DP, SIDE_LONG_THRESHOLD_MAX_DP)
        ?: (shortConfigured + SIDE_LONG_THRESHOLD_GAP_DP)
    val responseRatio = SIDE_THRESHOLD_RESPONSE_RATIO
    return SideGestureThresholds(
        minPullDistanceDp = shortConfigured * responseRatio,
        longPullDistanceDp = longConfigured * responseRatio,
        // Vertical swipes use the same short-distance contract. Keeping a
        // separate fixed value here makes fallback Overlay behavior diverge
        // from Native and Server for the same preferences.
        minSwipeDistanceDp = shortConfigured * responseRatio
    )
}

internal const val SIDE_THRESHOLD_RESPONSE_RATIO = 0.70f
internal const val SIDE_SHORT_THRESHOLD_MIN_DP = 8f
internal const val SIDE_SHORT_THRESHOLD_MAX_DP = 120f
internal const val SIDE_LONG_THRESHOLD_GAP_DP = 8f
internal const val SIDE_LONG_THRESHOLD_MAX_DP = 320f
