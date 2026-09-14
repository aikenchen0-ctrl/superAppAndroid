package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import java.util.Collections

data class BottomBarConfig(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val widthDp: Float,
    val heightDp: Float = 24f
) {
    init {
        requireFiniteNonNegative(screenWidthDp, "screenWidthDp")
        requireFiniteNonNegative(screenHeightDp, "screenHeightDp")
        requireFiniteNonNegative(widthDp, "widthDp")
        requireFiniteNonNegative(heightDp, "heightDp")
        // A zero screen dimension is the unbound default used while a snapshot
        // is being assembled. Enforce containment once display metrics are known.
        if (screenWidthDp > 0f) {
            require(widthDp <= screenWidthDp) { "bottom bar width must be within the screen width" }
        }
        if (screenHeightDp > 0f) {
            require(heightDp <= screenHeightDp) { "bottom bar height must be within the screen height" }
        }
    }

    val leftDp: Float get() = ((screenWidthDp - widthDp) / 2f).coerceAtLeast(0f)
    val rightDp: Float get() = (leftDp + widthDp).coerceAtMost(screenWidthDp)
    val topDp: Float get() = (screenHeightDp - heightDp).coerceAtLeast(0f)
}

class ConfigSnapshot(
    sideZones: Map<EdgeSide, List<HotZoneSegment>> = emptyMap(),
    val bottomBar: BottomBarConfig = BottomBarConfig(0f, 0f, 0f, heightDp = 0f),
    sideActions: Map<EdgeSide, Map<GestureType, GestureAction>> = emptyMap(),
    bottomActions: Map<GestureType, GestureAction> = emptyMap(),
    val revision: Long = 1L,
    val density: Float = 1f
) {
    init {
        require(revision > 0L) { "revision must be positive" }
        require(density.isFinite() && density > 0f) { "density must be a finite positive value" }
        validateZones(sideZones)
        sideActions.values.forEach { actions -> actions.values.forEach(::validateAction) }
        bottomActions.values.forEach(::validateAction)
    }

    val sideZones: Map<EdgeSide, List<HotZoneSegment>> = immutableNestedListMap(sideZones)
    val sideActions: Map<EdgeSide, Map<GestureType, GestureAction>> = immutableNestedMap(sideActions)
    val bottomActions: Map<GestureType, GestureAction> = immutableMap(bottomActions)

    val version: Long get() = revision
    val snapshotVersion: Long get() = revision

    fun actionFor(side: EdgeSide, gesture: GestureType): GestureAction =
        sideActions[side]?.get(gesture) ?: GestureAction.None

    fun bottomActionFor(gesture: GestureType): GestureAction =
        bottomActions[gesture] ?: GestureAction.None

    override fun equals(other: Any?): Boolean = other is ConfigSnapshot &&
        sideZones == other.sideZones && bottomBar == other.bottomBar &&
        sideActions == other.sideActions && bottomActions == other.bottomActions &&
        revision == other.revision && density == other.density

    override fun hashCode(): Int = listOf(sideZones, bottomBar, sideActions, bottomActions, revision, density).hashCode()

    override fun toString(): String =
        "ConfigSnapshot(revision=$revision, density=$density, sideZones=$sideZones, bottomBar=$bottomBar)"

    private fun validateZones(zonesBySide: Map<EdgeSide, List<HotZoneSegment>>) {
        zonesBySide.forEach { (side, zones) ->
            require(zones.size <= MAX_ZONES_PER_SIDE) { "$side supports at most $MAX_ZONES_PER_SIDE zones" }
            val ids = zones.map { zone ->
                require(zone.zoneId >= 0) { "zoneId must be non-negative" }
                require(
                    zone.startDp.isFinite() && zone.lengthDp.isFinite() &&
                        zone.thicknessDp.isFinite() && zone.edgeInsetDp.isFinite()
                ) {
                    "zone geometry must be finite"
                }
                require(zone.lengthDp >= 0f) { "zone length must be non-negative" }
                require(zone.thicknessDp >= 0f) { "zone thickness must be non-negative" }
                require(zone.edgeInsetDp >= 0f) { "zone edge inset must be non-negative" }
                zone.zoneId
            }
            require(ids.size == ids.toSet().size) { "$side contains duplicate zoneId values" }
        }
    }

    private fun validateAction(action: GestureAction) {
        if (action is GestureAction.LaunchApp) {
            require(isValidPackageName(action.packageName)) {
                "invalid Android package name: ${action.packageName}"
            }
        }
    }

    private fun <K, V> immutableMap(source: Map<K, V>): Map<K, V> =
        Collections.unmodifiableMap(source.toMap())

    private fun immutableNestedMap(
        source: Map<EdgeSide, Map<GestureType, GestureAction>>
    ): Map<EdgeSide, Map<GestureType, GestureAction>> =
        Collections.unmodifiableMap(source.mapValues { immutableMap(it.value) }.toMap())

    private fun immutableNestedListMap(
        source: Map<EdgeSide, List<HotZoneSegment>>
    ): Map<EdgeSide, List<HotZoneSegment>> =
        Collections.unmodifiableMap(source.mapValues { Collections.unmodifiableList(it.value.toList()) }.toMap())

    companion object {
        const val MAX_ZONES_PER_SIDE = 4
        private val PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

        fun isValidPackageName(packageName: String): Boolean = PACKAGE_NAME.matches(packageName)
    }
}

private fun requireFiniteNonNegative(value: Float, name: String) {
    require(value.isFinite() && value >= 0f) { "$name must be finite and non-negative" }
}
