package com.paifa.ubikitouch.core.model

data class EdgeZoneConfig(
    val side: EdgeSide,
    val zoneId: Int = DEFAULT_ZONE_ID,
    val enabled: Boolean = true,
    val thicknessDp: Int = 24,
    val topInsetPercent: Int = 15,
    val bottomInsetPercent: Int = 15
) {
    constructor(
        side: EdgeSide,
        enabled: Boolean,
        thicknessDp: Int,
        lengthPercent: Int,
        positionPercent: Int
    ) : this(
        side = side,
        zoneId = DEFAULT_ZONE_ID,
        enabled = enabled,
        thicknessDp = thicknessDp,
        topInsetPercent = topInsetFromLegacy(lengthPercent, positionPercent),
        bottomInsetPercent = bottomInsetFromLegacy(lengthPercent, positionPercent)
    )

    val lengthPercent: Int
        get() = (100 - topInsetPercent.coerceIn(0, MAX_INSET_PERCENT) -
            bottomInsetPercent.coerceIn(0, MAX_INSET_PERCENT)).coerceIn(MIN_LENGTH_PERCENT, 100)

    val positionPercent: Int
        get() {
            val freePercent = (100 - lengthPercent).coerceAtLeast(0)
            if (freePercent == 0) return 50
            return (topInsetPercent.coerceIn(0, MAX_INSET_PERCENT) * 100 / freePercent).coerceIn(0, 100)
        }

    fun sanitized(): EdgeZoneConfig = sanitize(this)

    companion object {
        const val DEFAULT_ZONE_ID = 0
        const val MAX_ZONES_PER_SIDE = 4
        const val MIN_THICKNESS_DP = 1
        const val MAX_THICKNESS_DP = 96
        const val MIN_LENGTH_PERCENT = 10
        const val MAX_INSET_PERCENT = 100 - MIN_LENGTH_PERCENT

        fun defaultFor(side: EdgeSide, zoneId: Int): EdgeZoneConfig {
            val (topInset, bottomInset) = when (zoneId) {
                1 -> 0 to 55
                2 -> 55 to 0
                3 -> 35 to 35
                else -> 15 to 15
            }
            return EdgeZoneConfig(
                side = side,
                zoneId = zoneId.coerceIn(0, MAX_ZONES_PER_SIDE - 1),
                topInsetPercent = topInset,
                bottomInsetPercent = bottomInset
            )
        }

        fun sanitize(config: EdgeZoneConfig): EdgeZoneConfig {
            val zoneId = config.zoneId.coerceIn(0, MAX_ZONES_PER_SIDE - 1)
            val topInset = config.topInsetPercent.coerceIn(0, MAX_INSET_PERCENT)
            val bottomInset = config.bottomInsetPercent.coerceIn(0, MAX_INSET_PERCENT - topInset)
            return config.copy(
                zoneId = zoneId,
                thicknessDp = config.thicknessDp.coerceIn(MIN_THICKNESS_DP, MAX_THICKNESS_DP),
                topInsetPercent = topInset,
                bottomInsetPercent = bottomInset
            )
        }

        fun topInsetFromLegacy(lengthPercent: Int, positionPercent: Int): Int {
            val length = lengthPercent.coerceIn(MIN_LENGTH_PERCENT, 100)
            val freePercent = 100 - length
            return (freePercent * positionPercent.coerceIn(0, 100) / 100).coerceIn(0, MAX_INSET_PERCENT)
        }

        fun bottomInsetFromLegacy(lengthPercent: Int, positionPercent: Int): Int {
            val length = lengthPercent.coerceIn(MIN_LENGTH_PERCENT, 100)
            val topInset = topInsetFromLegacy(length, positionPercent)
            return (100 - length - topInset).coerceIn(0, MAX_INSET_PERCENT - topInset)
        }
    }
}
