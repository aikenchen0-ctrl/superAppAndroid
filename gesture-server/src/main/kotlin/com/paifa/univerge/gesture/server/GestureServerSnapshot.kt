package com.paifa.univerge.gesture.server

import android.os.Bundle
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType

/** Small, versioned DTO used at the process boundary. It contains no Context or View. */
data class GestureServerZone(
    val zoneId: Int,
    val enabled: Boolean,
    val startDp: Float,
    val lengthDp: Float,
    val thicknessDp: Float,
    val edgeInsetDp: Float = 0f
) {
    fun isValid(): Boolean =
        zoneId >= 0 &&
            startDp.isFinite() && startDp >= 0f &&
            lengthDp.isFinite() && lengthDp > 0f &&
            thicknessDp.isFinite() && thicknessDp >= 0f &&
            edgeInsetDp.isFinite() && edgeInsetDp >= 0f
}

data class GestureServerSnapshot(
    val version: Long,
    val density: Float,
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val leftZones: List<GestureServerZone> = emptyList(),
    val rightZones: List<GestureServerZone> = emptyList(),
    val bottomWidthDp: Float = 156f,
    val bottomHeightDp: Float = 30f,
    val leftActions: Map<String, String> = emptyMap(),
    val rightActions: Map<String, String> = emptyMap(),
    val bottomActions: Map<String, String> = emptyMap()
) {
    fun isValid(): Boolean {
        if (version <= 0L || !density.isFinite() || density <= 0f) return false
        if (!screenWidthDp.isFinite() || screenWidthDp < 0f) return false
        if (!screenHeightDp.isFinite() || screenHeightDp < 0f) return false
        if (!bottomWidthDp.isFinite() || bottomWidthDp <= 0f) return false
        if (!bottomHeightDp.isFinite() || bottomHeightDp <= 0f) return false
        return validZones(leftZones) && validZones(rightZones) &&
            validActions(leftActions) && validActions(rightActions) && validActions(bottomActions)
    }

    fun actionFor(side: EdgeSide, gesture: GestureType): GestureAction =
        GestureAction.fromId(
            when (side) {
                EdgeSide.LEFT -> leftActions[gesture.id]
                EdgeSide.RIGHT -> rightActions[gesture.id]
            } ?: GestureAction.None.id
        )

    fun bottomActionFor(gesture: GestureType): GestureAction =
        GestureAction.fromId(bottomActions[gesture.id] ?: GestureAction.None.id)

    fun toBundle(): Bundle = Bundle().apply {
        putLong(KEY_VERSION, version)
        putFloat(KEY_DENSITY, density)
        putFloat(KEY_SCREEN_WIDTH_DP, screenWidthDp)
        putFloat(KEY_SCREEN_HEIGHT_DP, screenHeightDp)
        putFloat(KEY_BOTTOM_WIDTH_DP, bottomWidthDp)
        putFloat(KEY_BOTTOM_HEIGHT_DP, bottomHeightDp)
        putParcelableArrayList(KEY_LEFT_ZONES, ArrayList(leftZones.map(GestureServerZone::toBundle)))
        putParcelableArrayList(KEY_RIGHT_ZONES, ArrayList(rightZones.map(GestureServerZone::toBundle)))
        putBundle(KEY_LEFT_ACTIONS, leftActions.toBundle())
        putBundle(KEY_RIGHT_ACTIONS, rightActions.toBundle())
        putBundle(KEY_BOTTOM_ACTIONS, bottomActions.toBundle())
    }

    companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_DENSITY = "density"
        private const val KEY_SCREEN_WIDTH_DP = "screen_width_dp"
        private const val KEY_SCREEN_HEIGHT_DP = "screen_height_dp"
        private const val KEY_BOTTOM_WIDTH_DP = "bottom_width_dp"
        private const val KEY_BOTTOM_HEIGHT_DP = "bottom_height_dp"
        private const val KEY_LEFT_ZONES = "left_zones"
        private const val KEY_RIGHT_ZONES = "right_zones"
        private const val KEY_LEFT_ACTIONS = "left_actions"
        private const val KEY_RIGHT_ACTIONS = "right_actions"
        private const val KEY_BOTTOM_ACTIONS = "bottom_actions"

        fun fromBundle(bundle: Bundle?): GestureServerSnapshot? {
            if (bundle == null) return null
            val left = bundle.zoneList(KEY_LEFT_ZONES) ?: return null
            val right = bundle.zoneList(KEY_RIGHT_ZONES) ?: return null
            return GestureServerSnapshot(
                version = bundle.getLong(KEY_VERSION, 0L),
                density = bundle.getFloat(KEY_DENSITY, Float.NaN),
                screenWidthDp = bundle.getFloat(KEY_SCREEN_WIDTH_DP, Float.NaN),
                screenHeightDp = bundle.getFloat(KEY_SCREEN_HEIGHT_DP, Float.NaN),
                leftZones = left,
                rightZones = right,
                bottomWidthDp = bundle.getFloat(KEY_BOTTOM_WIDTH_DP, Float.NaN),
                bottomHeightDp = bundle.getFloat(KEY_BOTTOM_HEIGHT_DP, Float.NaN),
                leftActions = bundle.getBundle(KEY_LEFT_ACTIONS).toActionMap(),
                rightActions = bundle.getBundle(KEY_RIGHT_ACTIONS).toActionMap(),
                bottomActions = bundle.getBundle(KEY_BOTTOM_ACTIONS).toActionMap()
            )
        }

        private fun Bundle.zoneList(key: String): List<GestureServerZone>? {
            @Suppress("DEPRECATION")
            val values = getParcelableArrayList<Bundle>(key) ?: return null
            return values.map(::gestureServerZoneFromBundle).takeIf { it.size == values.size }
        }

        private fun validZones(zones: List<GestureServerZone>): Boolean {
            if (zones.size > 4) return false
            val ids = HashSet<Int>(zones.size)
            return zones.all { it.isValid() && ids.add(it.zoneId) }
        }

        private fun validActions(actions: Map<String, String>): Boolean =
            actions.size <= GestureType.entries.size && actions.keys.all { key ->
                GestureType.entries.any { it.id == key }
            }
    }
}

private fun Map<String, String>.toBundle(): Bundle = Bundle().apply {
    forEach { (gestureId, actionId) -> putString(gestureId, actionId) }
}

private fun Bundle?.toActionMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keySet().associateWith { key -> getString(key).orEmpty() }
}

private fun GestureServerZone.toBundle(): Bundle = Bundle().apply {
    putInt("zone_id", zoneId)
    putBoolean("enabled", enabled)
    putFloat("start_dp", startDp)
    putFloat("length_dp", lengthDp)
    putFloat("thickness_dp", thicknessDp)
    putFloat("edge_inset_dp", edgeInsetDp)
}

private fun gestureServerZoneFromBundle(bundle: Bundle): GestureServerZone =
    GestureServerZone(
        zoneId = bundle.getInt("zone_id", -1),
        enabled = bundle.getBoolean("enabled", false),
        startDp = bundle.getFloat("start_dp", Float.NaN),
        lengthDp = bundle.getFloat("length_dp", Float.NaN),
        thicknessDp = bundle.getFloat("thickness_dp", Float.NaN),
        edgeInsetDp = bundle.getFloat("edge_inset_dp", 0f)
    )
