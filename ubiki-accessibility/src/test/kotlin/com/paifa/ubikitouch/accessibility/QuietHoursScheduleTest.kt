package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursScheduleTest {
    @Test
    fun overnightScheduleMatchesAcrossMidnight() {
        val schedule = QuietHoursSchedule(startMinuteOfDay = 23 * 60, endMinuteOfDay = 6 * 60)

        assertTrue(schedule.isActiveAt(23 * 60 + 30))
        assertTrue(schedule.isActiveAt(5 * 60 + 45))
    }

    @Test
    fun activeScheduleReturnsFirstMatchingItem() {
        val schedules = listOf(
            QuietHoursSchedule(id = "a", startMinuteOfDay = 8 * 60, endMinuteOfDay = 9 * 60),
            QuietHoursSchedule(id = "b", startMinuteOfDay = 8 * 60 + 30, endMinuteOfDay = 10 * 60)
        )

        assertEquals("a", activeQuietHoursSchedule(schedules, 8 * 60 + 45)?.id)
    }
}
