package com.paifa.univerge.accessibility

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class QuietHoursSchedule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
) {
    fun sanitized(): QuietHoursSchedule {
        return copy(
            startMinuteOfDay = startMinuteOfDay.coerceIn(0, MINUTE_OF_DAY_MAX),
            endMinuteOfDay = endMinuteOfDay.coerceIn(0, MINUTE_OF_DAY_MAX)
        )
    }

    fun isActiveAt(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val minute = minuteOfDay.coerceIn(0, MINUTE_OF_DAY_MAX)
        val start = startMinuteOfDay.coerceIn(0, MINUTE_OF_DAY_MAX)
        val end = endMinuteOfDay.coerceIn(0, MINUTE_OF_DAY_MAX)
        if (start == end) return true
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }
}

internal fun activeQuietHoursSchedule(
    schedules: List<QuietHoursSchedule>,
    minuteOfDay: Int
): QuietHoursSchedule? {
    return schedules.firstOrNull { it.sanitized().isActiveAt(minuteOfDay) }
}

private const val MINUTE_OF_DAY_MAX = 23 * 60 + 59
