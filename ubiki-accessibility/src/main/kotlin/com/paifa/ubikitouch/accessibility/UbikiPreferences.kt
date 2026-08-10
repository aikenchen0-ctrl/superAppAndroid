package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.content.SharedPreferences
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureDefaultAction
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.core.model.sanitizeEdgeInsetDp
import com.paifa.ubikitouch.core.sidefunction.SideFunctionConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Calendar

class UbikiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * 启用边缘触摸条
     */
    var globalEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, value).apply()

    var accessibilityKeepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_KEEP_ALIVE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_KEEP_ALIVE_ENABLED, value).apply()

    /**
     * 显示指示条
     */
    var showIndicators: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INDICATORS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_INDICATORS, value).apply()

    var edgeOverlayOpacityPercent: Int
        get() = sanitizeEdgeOverlayOpacityPercent(
            prefs.getInt(
                KEY_EDGE_OVERLAY_OPACITY_PERCENT,
                prefs.getInt(KEY_OVERLAY_OPACITY, DEFAULT_EDGE_OVERLAY_OPACITY_PERCENT)
            )
        )
        set(value) {
            val sanitized = sanitizeEdgeOverlayOpacityPercent(value)
            prefs.edit()
                .putInt(KEY_EDGE_OVERLAY_OPACITY_PERCENT, sanitized)
                .putInt(KEY_OVERLAY_OPACITY, sanitized)
                .apply()
        }

    /** Compatibility alias for callers using the original overlay opacity API. */
    var overlayOpacity: Int
        get() = edgeOverlayOpacityPercent
        set(value) {
            edgeOverlayOpacityPercent = value
        }

    internal var gestureInputMode: GestureInputMode
        get() = GestureInputMode.fromId(prefs.getString(KEY_GESTURE_INPUT_MODE, GestureInputMode.Auto.id))
        set(value) = prefs.edit().putString(KEY_GESTURE_INPUT_MODE, value.id).apply()

    var bottomGestureBarWidthDp: Int
        get() = sanitizeBottomGestureBarWidthDp(
            prefs.getInt(KEY_BOTTOM_GESTURE_BAR_WIDTH_DP, defaultBottomGestureBarWidthDp())
        )
        set(value) = prefs.edit()
            .putInt(KEY_BOTTOM_GESTURE_BAR_WIDTH_DP, sanitizeBottomGestureBarWidthDp(value))
            .apply()

    var floatingChatFrostedBackgroundEnabled: Boolean
        get() = prefs.getBoolean(
            KEY_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED,
            DEFAULT_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED
        )
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED, value).apply()

    var floatingChatBackgroundOpacityPercent: Int
        get() = sanitizeFloatingChatBackgroundOpacityPercent(
            prefs.getInt(
                KEY_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT,
                DEFAULT_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT
            )
        )
        set(value) = prefs.edit()
            .putInt(
                KEY_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT,
                sanitizeFloatingChatBackgroundOpacityPercent(value)
            )
            .apply()

    var floatingChatBlurRadiusDp: Int
        get() = sanitizeFloatingChatBlurRadiusDp(
            prefs.getInt(KEY_FLOATING_CHAT_BLUR_RADIUS_DP, DEFAULT_FLOATING_CHAT_BLUR_RADIUS_DP)
        )
        set(value) = prefs.edit()
            .putInt(KEY_FLOATING_CHAT_BLUR_RADIUS_DP, sanitizeFloatingChatBlurRadiusDp(value))
            .apply()

    var floatingChatBackgroundColorRgb: Int
        get() = sanitizeFloatingChatBackgroundColorRgb(
            prefs.getInt(KEY_FLOATING_CHAT_BACKGROUND_COLOR_RGB, DEFAULT_FLOATING_CHAT_BACKGROUND_COLOR_RGB)
        )
        set(value) = prefs.edit()
            .putInt(KEY_FLOATING_CHAT_BACKGROUND_COLOR_RGB, sanitizeFloatingChatBackgroundColorRgb(value))
            .apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var disableInLandscape: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_IN_LANDSCAPE, true)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_IN_LANDSCAPE, value).apply()

    var disableWhenKeyboardShown: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_WHEN_KEYBOARD_SHOWN, true)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_WHEN_KEYBOARD_SHOWN, value).apply()

    var swipeThresholdDp: Int
        get() = shortPullThresholdDp
        set(value) {
            shortPullThresholdDp = value
        }

    var shortPullThresholdDp: Int
        get() = sanitizeShortPullThresholdDp(prefs.getInt(KEY_SHORT_PULL_THRESHOLD_DP, prefs.getInt(KEY_SWIPE_THRESHOLD_DP, DEFAULT_SHORT_PULL_THRESHOLD_DP)))
        set(value) = prefs.edit()
            .putInt(KEY_SHORT_PULL_THRESHOLD_DP, sanitizeShortPullThresholdDp(value))
            .putInt(KEY_SWIPE_THRESHOLD_DP, sanitizeShortPullThresholdDp(value))
            .apply()

    var longPullThresholdDp: Int
        get() = sanitizeLongPullThresholdDp(
            shortThresholdDp = shortPullThresholdDp,
            longThresholdDp = prefs.getInt(KEY_LONG_PULL_THRESHOLD_DP, DEFAULT_LONG_PULL_THRESHOLD_DP)
        )
        set(value) = prefs.edit()
            .putInt(
                KEY_LONG_PULL_THRESHOLD_DP,
                sanitizeLongPullThresholdDp(shortThresholdDp = shortPullThresholdDp, longThresholdDp = value)
            )
            .apply()

    var pausedUntilEpochMs: Long
        get() = prefs.getLong(KEY_PAUSED_UNTIL_EPOCH_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_PAUSED_UNTIL_EPOCH_MS, value.coerceAtLeast(0L)).apply()

    fun isTemporarilyPaused(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return pausedUntilEpochMs > nowEpochMs
    }

    fun pauseFor(durationMs: Long, nowEpochMs: Long = System.currentTimeMillis()) {
        pausedUntilEpochMs = nowEpochMs + durationMs.coerceAtLeast(0L)
    }

    fun resumeNow() {
        pausedUntilEpochMs = 0L
    }

    fun isQuietHoursActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (quietHoursSchedules.isEmpty()) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return activeQuietHoursSchedule(quietHoursSchedules, minuteOfDay) != null
    }

    fun edgeConfig(side: EdgeSide): EdgeZoneConfig {
        return edgeConfigs(side).firstOrNull() ?: EdgeZoneConfig.defaultFor(side, EdgeZoneConfig.DEFAULT_ZONE_ID)
    }

    fun edgeConfigs(side: EdgeSide): List<EdgeZoneConfig> {
        val count = edgeZoneCount(side)
        return (0 until count).map { zoneId ->
            edgeConfig(side, zoneId)
        }
    }

    var leftEdgeInsetDp: Int
        get() = edgeInsetDp(EdgeSide.LEFT)
        set(value) = setEdgeInsetDp(EdgeSide.LEFT, value)

    var rightEdgeInsetDp: Int
        get() = edgeInsetDp(EdgeSide.RIGHT)
        set(value) = setEdgeInsetDp(EdgeSide.RIGHT, value)

    fun edgeInsetDp(side: EdgeSide): Int {
        return edgeConfig(side, EdgeZoneConfig.DEFAULT_ZONE_ID).edgeInsetDp
    }

    fun setEdgeInsetDp(side: EdgeSide, value: Int) {
        val sanitized = sanitizeEdgeInsetDp(value)
        prefs.edit()
            .putInt(edgeZoneInsetKey(side, EdgeZoneConfig.DEFAULT_ZONE_ID), sanitized)
            .putInt(edgeInsetKey(side), sanitized)
            .apply()
    }

    fun actionFor(side: EdgeSide, gestureType: GestureType): GestureAction {
        val stored = prefs.getString(actionKey(side, gestureType), null)
            ?: when (gestureType) {
                GestureType.PULL_INWARD_SHORT -> prefs.getString(actionKey(side, GestureType.PULL_INWARD), null)
                GestureType.PULL_DIAGONAL_UP_SHORT -> prefs.getString(actionKey(side, GestureType.PULL_DIAGONAL_UP), null)
                GestureType.PULL_DIAGONAL_UP_LONG -> prefs.getString(actionKey(side, GestureType.PULL_DIAGONAL_UP), null)
                GestureType.PULL_DIAGONAL_DOWN_SHORT -> prefs.getString(actionKey(side, GestureType.PULL_DIAGONAL_DOWN), null)
                GestureType.PULL_DIAGONAL_DOWN_LONG -> prefs.getString(actionKey(side, GestureType.PULL_DIAGONAL_DOWN), null)
                else -> null
            }
        return GestureAction.fromId(stored ?: GestureDefaultAction.forGesture(gestureType).id)
    }

    fun setAction(side: EdgeSide, gestureType: GestureType, action: GestureAction) {
        prefs.edit().putString(actionKey(side, gestureType), action.id).apply()
    }

    fun bottomGestureBarActionFor(gestureType: BottomGestureBarGestureType): GestureAction {
        val stored = prefs.getString(bottomGestureBarActionKey(gestureType), null)
        return GestureAction.fromId(stored ?: defaultBottomGestureBarAction(gestureType).id)
    }

    fun setBottomGestureBarAction(gestureType: BottomGestureBarGestureType, action: GestureAction) {
        prefs.edit().putString(bottomGestureBarActionKey(gestureType), action.id).apply()
    }

    var sideFunctionCustomActionIds: List<String>
        get() {
            val stored = prefs.getString(KEY_SIDE_FUNCTION_CUSTOM_ACTION_IDS, null) ?: return emptyList()
            val decoded = runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), stored)
            }.getOrDefault(emptyList())
            return SideFunctionConfig.fromCustomActionIds(decoded).customActionIds
        }
        set(value) {
            val normalized = SideFunctionConfig.fromCustomActionIds(value).customActionIds
            prefs.edit()
                .putString(
                    KEY_SIDE_FUNCTION_CUSTOM_ACTION_IDS,
                    Json.encodeToString(ListSerializer(String.serializer()), normalized)
                )
                .apply()
        }

    var quietHoursSchedules: List<QuietHoursSchedule>
        get() {
            val stored = prefs.getString(KEY_QUIET_HOURS_SCHEDULES, null) ?: return emptyList()
            val decoded = runCatching {
                Json.decodeFromString(ListSerializer(QuietHoursSchedule.serializer()), stored)
            }.getOrDefault(emptyList())
            return decoded.map(QuietHoursSchedule::sanitized)
        }
        set(value) {
            val normalized = value.map(QuietHoursSchedule::sanitized)
            prefs.edit()
                .putString(
                    KEY_QUIET_HOURS_SCHEDULES,
                    Json.encodeToString(ListSerializer(QuietHoursSchedule.serializer()), normalized)
                )
                .apply()
        }

    fun setEdgeConfig(config: EdgeZoneConfig) {
        val zoneId = config.zoneId.coerceIn(0, EdgeZoneConfig.MAX_ZONES_PER_SIDE - 1)
        val currentConfigs = edgeConfigs(config.side).toMutableList()
        while (currentConfigs.size <= zoneId) {
            currentConfigs += EdgeZoneConfig.defaultFor(config.side, currentConfigs.size)
        }
        currentConfigs[zoneId] = config.copy(zoneId = zoneId)
        setEdgeConfigs(config.side, currentConfigs)
    }

    fun setEdgeConfigs(side: EdgeSide, configs: List<EdgeZoneConfig>) {
        val normalized = configs
            .take(EdgeZoneConfig.MAX_ZONES_PER_SIDE)
            .mapIndexed { index, config ->
                config.copy(side = side, zoneId = index).sanitized()
            }
        val prefix = edgePrefix(side)
        val editor = prefs.edit()
            .putInt("${prefix}_zone_count", normalized.size)
        normalized.forEach { config ->
            putEdgeConfig(editor, config)
        }
        for (zoneId in normalized.size until EdgeZoneConfig.MAX_ZONES_PER_SIDE) {
            clearEdgeConfig(editor, side, zoneId)
        }
        normalized.firstOrNull()?.let { config ->
            putLegacyEdgeConfig(editor, config)
        } ?: clearLegacyEdgeConfig(editor, side)
        editor.apply()
    }

    val blockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty()

    fun isPackageBlocked(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return blockedPackages.contains(packageName)
    }

    fun addBlockedPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return
        prefs.edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, blockedPackages + normalized)
            .apply()
    }

    fun removeBlockedPackage(packageName: String) {
        prefs.edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, blockedPackages - packageName)
            .apply()
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun actionKey(side: EdgeSide, gestureType: GestureType): String {
        return "action_${side.id}_${gestureType.id}"
    }

    private fun bottomGestureBarActionKey(gestureType: BottomGestureBarGestureType): String {
        return "bottom_gesture_bar_action_${gestureType.id}"
    }

    private fun edgeInsetKey(side: EdgeSide): String {
        return "edge_${side.id}_inset_dp"
    }

    private fun edgeZoneInsetKey(side: EdgeSide, zoneId: Int): String {
        return "${edgeZonePrefix(side, zoneId)}_edge_inset_dp"
    }

    private fun edgeZoneCount(side: EdgeSide): Int {
        val prefix = edgePrefix(side)
        val key = "${prefix}_zone_count"
        return if (prefs.contains(key)) {
            prefs.getInt(key, 0).coerceIn(0, EdgeZoneConfig.MAX_ZONES_PER_SIDE)
        } else {
            1
        }
    }

    private fun edgeConfig(side: EdgeSide, zoneId: Int): EdgeZoneConfig {
        val prefix = edgePrefix(side)
        val zonePrefix = edgeZonePrefix(side, zoneId)
        val defaultConfig = EdgeZoneConfig.defaultFor(side, zoneId)
        val enabled = prefs.getBoolean(
            "${zonePrefix}_enabled",
            if (zoneId == EdgeZoneConfig.DEFAULT_ZONE_ID) {
                prefs.getBoolean("${prefix}_enabled", defaultConfig.enabled)
            } else {
                defaultConfig.enabled
            }
        )
        val thicknessDp = prefs.getInt(
            "${zonePrefix}_thickness_dp",
            if (zoneId == EdgeZoneConfig.DEFAULT_ZONE_ID) {
                prefs.getInt("${prefix}_thickness_dp", defaultConfig.thicknessDp)
            } else {
                defaultConfig.thicknessDp
            }
        )
        val topInsetPercent = if (prefs.contains("${zonePrefix}_top_inset_percent")) {
            prefs.getInt("${zonePrefix}_top_inset_percent", defaultConfig.topInsetPercent)
        } else if (zoneId == EdgeZoneConfig.DEFAULT_ZONE_ID) {
            EdgeZoneConfig.topInsetFromLegacy(
                prefs.getInt("${prefix}_length_percent", defaultConfig.lengthPercent),
                prefs.getInt("${prefix}_position_percent", defaultConfig.positionPercent)
            )
        } else {
            defaultConfig.topInsetPercent
        }
        val bottomInsetPercent = if (prefs.contains("${zonePrefix}_bottom_inset_percent")) {
            prefs.getInt("${zonePrefix}_bottom_inset_percent", defaultConfig.bottomInsetPercent)
        } else if (zoneId == EdgeZoneConfig.DEFAULT_ZONE_ID) {
            EdgeZoneConfig.bottomInsetFromLegacy(
                prefs.getInt("${prefix}_length_percent", defaultConfig.lengthPercent),
                prefs.getInt("${prefix}_position_percent", defaultConfig.positionPercent)
            )
        } else {
            defaultConfig.bottomInsetPercent
        }
        val edgeInsetDp = if (prefs.contains(edgeZoneInsetKey(side, zoneId))) {
            prefs.getInt(edgeZoneInsetKey(side, zoneId), defaultConfig.edgeInsetDp)
        } else if (zoneId < edgeZoneCount(side)) {
            prefs.getInt(edgeInsetKey(side), defaultConfig.edgeInsetDp)
        } else {
            defaultConfig.edgeInsetDp
        }
        return EdgeZoneConfig(
            side = side,
            zoneId = zoneId,
            enabled = enabled,
            thicknessDp = thicknessDp,
            edgeInsetDp = edgeInsetDp,
            topInsetPercent = topInsetPercent,
            bottomInsetPercent = bottomInsetPercent
        ).sanitized()
    }

    private fun putEdgeConfig(editor: SharedPreferences.Editor, config: EdgeZoneConfig) {
        val sanitized = config.sanitized()
        val prefix = edgeZonePrefix(sanitized.side, sanitized.zoneId)
        editor
            .putBoolean("${prefix}_enabled", sanitized.enabled)
            .putInt("${prefix}_thickness_dp", sanitized.thicknessDp)
            .putInt(edgeZoneInsetKey(sanitized.side, sanitized.zoneId), sanitized.edgeInsetDp)
            .putInt("${prefix}_top_inset_percent", sanitized.topInsetPercent)
            .putInt("${prefix}_bottom_inset_percent", sanitized.bottomInsetPercent)
    }

    private fun putLegacyEdgeConfig(editor: SharedPreferences.Editor, config: EdgeZoneConfig) {
        val sanitized = config.sanitized()
        val prefix = edgePrefix(sanitized.side)
        editor
            .putBoolean("${prefix}_enabled", sanitized.enabled)
            .putInt("${prefix}_thickness_dp", sanitized.thicknessDp)
            .putInt(edgeInsetKey(sanitized.side), sanitized.edgeInsetDp)
            .putInt("${prefix}_length_percent", sanitized.lengthPercent)
            .putInt("${prefix}_position_percent", sanitized.positionPercent)
    }

    private fun clearLegacyEdgeConfig(editor: SharedPreferences.Editor, side: EdgeSide) {
        val prefix = edgePrefix(side)
        editor
            .remove("${prefix}_enabled")
            .remove("${prefix}_thickness_dp")
            .remove(edgeInsetKey(side))
            .remove("${prefix}_length_percent")
            .remove("${prefix}_position_percent")
    }

    private fun clearEdgeConfig(editor: SharedPreferences.Editor, side: EdgeSide, zoneId: Int) {
        val prefix = edgeZonePrefix(side, zoneId)
        editor
            .remove("${prefix}_enabled")
            .remove("${prefix}_thickness_dp")
            .remove(edgeZoneInsetKey(side, zoneId))
            .remove("${prefix}_top_inset_percent")
            .remove("${prefix}_bottom_inset_percent")
    }

    private fun edgePrefix(side: EdgeSide): String {
        return "edge_${side.id}"
    }

    private fun edgeZonePrefix(side: EdgeSide, zoneId: Int): String {
        return "${edgePrefix(side)}_zone_$zoneId"
    }

    private companion object {
        const val FILE_NAME = "ubiki_touch_settings"
        const val KEY_GLOBAL_ENABLED = "global_enabled"
        const val KEY_ACCESSIBILITY_KEEP_ALIVE_ENABLED = "accessibility_keep_alive_enabled"
        const val KEY_SHOW_INDICATORS = "show_indicators"
        const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        const val KEY_GESTURE_INPUT_MODE = "gesture_input_mode"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val KEY_DISABLE_IN_LANDSCAPE = "disable_in_landscape"
        const val KEY_DISABLE_WHEN_KEYBOARD_SHOWN = "disable_when_keyboard_shown"
        const val KEY_SWIPE_THRESHOLD_DP = "swipe_threshold_dp"
        const val KEY_SHORT_PULL_THRESHOLD_DP = "short_pull_threshold_dp"
        const val KEY_LONG_PULL_THRESHOLD_DP = "long_pull_threshold_dp"
        const val KEY_PAUSED_UNTIL_EPOCH_MS = "paused_until_epoch_ms"
        const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        const val KEY_SIDE_FUNCTION_CUSTOM_ACTION_IDS = "side_function_custom_action_ids"
        const val KEY_QUIET_HOURS_SCHEDULES = "quiet_hours_schedules"
        const val DEFAULT_EDGE_OVERLAY_OPACITY_PERCENT = 0
    }
}

internal fun defaultShortPullThresholdDp(): Int = DEFAULT_SHORT_PULL_THRESHOLD_DP

internal fun defaultLongPullThresholdDp(): Int = DEFAULT_LONG_PULL_THRESHOLD_DP

fun defaultFloatingChatFrostedBackgroundEnabled(): Boolean = DEFAULT_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED

fun defaultFloatingChatBackgroundOpacityPercent(): Int = DEFAULT_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT

fun defaultFloatingChatBlurRadiusDp(): Int = DEFAULT_FLOATING_CHAT_BLUR_RADIUS_DP

fun defaultFloatingChatBackgroundColorRgb(): Int = DEFAULT_FLOATING_CHAT_BACKGROUND_COLOR_RGB

fun sanitizeFloatingChatBackgroundOpacityPercent(value: Int): Int {
    return value.coerceIn(MIN_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT, MAX_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT)
}

fun sanitizeFloatingChatBlurRadiusDp(value: Int): Int {
    return value.coerceIn(MIN_FLOATING_CHAT_BLUR_RADIUS_DP, MAX_FLOATING_CHAT_BLUR_RADIUS_DP)
}

fun sanitizeFloatingChatBackgroundColorRgb(value: Int): Int {
    return value.coerceIn(MIN_FLOATING_CHAT_BACKGROUND_COLOR_RGB, MAX_FLOATING_CHAT_BACKGROUND_COLOR_RGB)
}

fun sanitizeEdgeOverlayOpacityPercent(value: Int): Int {
    return value.coerceIn(MIN_EDGE_OVERLAY_OPACITY_PERCENT, MAX_EDGE_OVERLAY_OPACITY_PERCENT)
}

fun floatingChatBackgroundColorPresetRgbs(): List<Int> = FLOATING_CHAT_BACKGROUND_COLOR_PRESET_RGBS

internal fun isFloatingChatAppearancePreferenceKey(key: String?): Boolean {
    return key in FloatingChatAppearancePreferenceKeys
}

internal fun isBottomGestureBarPreferenceKey(key: String?): Boolean {
    return key == KEY_BOTTOM_GESTURE_BAR_WIDTH_DP || key?.startsWith("bottom_gesture_bar_action_") == true
}

internal fun sanitizeShortPullThresholdDp(value: Int): Int {
    return value.coerceIn(MIN_SHORT_PULL_THRESHOLD_DP, MAX_SHORT_PULL_THRESHOLD_DP)
}

internal fun sanitizeLongPullThresholdDp(
    shortThresholdDp: Int,
    longThresholdDp: Int
): Int {
    val short = sanitizeShortPullThresholdDp(shortThresholdDp)
    return longThresholdDp.coerceIn(
        maxOf(MIN_LONG_PULL_THRESHOLD_DP, short + MIN_LONG_PULL_GAP_DP),
        MAX_LONG_PULL_THRESHOLD_DP
    )
}

private const val DEFAULT_SHORT_PULL_THRESHOLD_DP = 45
private const val DEFAULT_LONG_PULL_THRESHOLD_DP = 175
private const val MIN_SHORT_PULL_THRESHOLD_DP = 20
private const val MAX_SHORT_PULL_THRESHOLD_DP = 100
private const val MIN_LONG_PULL_GAP_DP = 8
private const val MIN_LONG_PULL_THRESHOLD_DP = 110
private const val MAX_LONG_PULL_THRESHOLD_DP = 285
private const val DEFAULT_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED = true
private const val DEFAULT_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT = 78
private const val DEFAULT_FLOATING_CHAT_BLUR_RADIUS_DP = 18
private const val DEFAULT_FLOATING_CHAT_BACKGROUND_COLOR_RGB = 0xEAF3F6
private const val MIN_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT = 0
private const val MAX_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT = 100
private const val MIN_FLOATING_CHAT_BLUR_RADIUS_DP = 0
private const val MAX_FLOATING_CHAT_BLUR_RADIUS_DP = 40
private const val MIN_FLOATING_CHAT_BACKGROUND_COLOR_RGB = 0x000000
private const val MAX_FLOATING_CHAT_BACKGROUND_COLOR_RGB = 0xFFFFFF
private const val MIN_EDGE_OVERLAY_OPACITY_PERCENT = 0
private const val MAX_EDGE_OVERLAY_OPACITY_PERCENT = 100
internal const val KEY_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED = "floating_chat_frosted_background_enabled"
internal const val KEY_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT = "floating_chat_background_opacity_percent"
internal const val KEY_FLOATING_CHAT_BLUR_RADIUS_DP = "floating_chat_blur_radius_dp"
internal const val KEY_FLOATING_CHAT_BACKGROUND_COLOR_RGB = "floating_chat_background_color_rgb"
internal const val KEY_BOTTOM_GESTURE_BAR_WIDTH_DP = "bottom_gesture_bar_width_dp"
private const val KEY_EDGE_OVERLAY_OPACITY_PERCENT = "edge_overlay_opacity_percent"
private val FLOATING_CHAT_BACKGROUND_COLOR_PRESET_RGBS = listOf(
    0xEAF3F6,
    0xF1E9DC,
    0xE8F0E8,
    0xECEAF7,
    0xF2E8EA
)
private val FloatingChatAppearancePreferenceKeys = setOf(
    KEY_FLOATING_CHAT_FROSTED_BACKGROUND_ENABLED,
    KEY_FLOATING_CHAT_BACKGROUND_OPACITY_PERCENT,
    KEY_FLOATING_CHAT_BLUR_RADIUS_DP,
    KEY_FLOATING_CHAT_BACKGROUND_COLOR_RGB
)
