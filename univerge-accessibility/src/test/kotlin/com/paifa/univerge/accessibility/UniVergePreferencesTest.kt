package com.paifa.univerge.accessibility

import android.app.Application
import android.content.Context
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UniVergePreferencesTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test(timeout = 60_000L)
    fun keyboardShownDisablesEdgeGesturesByDefault() {
        assertEquals(true, UniVergePreferences(context).disableWhenKeyboardShown)
    }

    @Test(timeout = 60_000L)
    fun edgeInsetsArePersistedIndependentlyAndSanitized() {
        val preferences = UniVergePreferences(context)

        preferences.leftEdgeInsetDp = -4
        preferences.rightEdgeInsetDp = 200

        assertEquals(0, preferences.leftEdgeInsetDp)
        assertEquals(96, preferences.rightEdgeInsetDp)

        preferences.setEdgeInsetDp(EdgeSide.LEFT, 32)
        preferences.setEdgeInsetDp(EdgeSide.RIGHT, 48)

        val reloaded = UniVergePreferences(context)
        assertEquals(32, reloaded.edgeInsetDp(EdgeSide.LEFT))
        assertEquals(48, reloaded.edgeInsetDp(EdgeSide.RIGHT))
    }

    @Test(timeout = 60_000L)
    fun legacyInsetIsReadForExistingZonesAndEachZoneIsPersistedSeparately() {
        context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("edge_left_inset_dp", 32)
            .putInt("edge_left_zone_count", 2)
            .commit()

        val preferences = UniVergePreferences(context)
        assertEquals(
            listOf(32, 32),
            preferences.edgeConfigs(EdgeSide.LEFT).map { it.edgeInsetDp }
        )

        preferences.setEdgeConfigs(
            EdgeSide.LEFT,
            listOf(
                EdgeZoneConfig(EdgeSide.LEFT, zoneId = 0, edgeInsetDp = 12),
                EdgeZoneConfig(EdgeSide.LEFT, zoneId = 1, edgeInsetDp = 48)
            )
        )

        val reloaded = UniVergePreferences(context)
        assertEquals(
            listOf(12, 48),
            reloaded.edgeConfigs(EdgeSide.LEFT).map { it.edgeInsetDp }
        )
        val stored = context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
        assertEquals(12, stored.getInt("edge_left_zone_0_edge_inset_dp", -1))
        assertEquals(48, stored.getInt("edge_left_zone_1_edge_inset_dp", -1))
    }

    @Test(timeout = 60_000L)
    fun aNewZoneUsesZeroInsetWhenOnlyLegacyValueExists() {
        context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("edge_left_inset_dp", 32)
            .commit()

        val preferences = UniVergePreferences(context)
        preferences.setEdgeConfigs(
            EdgeSide.LEFT,
            listOf(
                preferences.edgeConfig(EdgeSide.LEFT),
                EdgeZoneConfig.defaultFor(EdgeSide.LEFT, 1)
            )
        )

        assertEquals(
            listOf(32, 0),
            UniVergePreferences(context).edgeConfigs(EdgeSide.LEFT).map { it.edgeInsetDp }
        )
    }

    @Test(timeout = 60_000L)
    fun removingTheLastZonePersistsAnEmptySideWithoutLeavingAnyZoneStateBehind() {
        val preferences = UniVergePreferences(context)
        preferences.setEdgeConfigs(
            EdgeSide.LEFT,
            listOf(EdgeZoneConfig.defaultFor(EdgeSide.LEFT, zoneId = 0))
        )

        preferences.setEdgeConfigs(EdgeSide.LEFT, emptyList())

        assertEquals(emptyList<EdgeZoneConfig>(), preferences.edgeConfigs(EdgeSide.LEFT))
        assertEquals(
            emptyList<EdgeZoneConfig>(),
            UniVergePreferences(context).edgeConfigs(EdgeSide.LEFT)
        )
        assertEquals(
            0,
            context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
                .getInt("edge_left_zone_count", -1)
        )
        val stored = context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
        assertEquals(false, stored.contains("edge_left_zone_0_enabled"))
        assertEquals(false, stored.contains("edge_left_inset_dp"))
    }

    @Test(timeout = 60_000L)
    fun edgeOverlayColorOpacityDefaultsToTransparentAndIsClamped() {
        val preferences = UniVergePreferences(context)

        assertEquals(0, preferences.edgeOverlayOpacityPercent)
        assertEquals(0, preferences.overlayOpacity)

        preferences.edgeOverlayOpacityPercent = -10
        assertEquals(0, preferences.edgeOverlayOpacityPercent)
        preferences.overlayOpacity = 140
        assertEquals(100, preferences.edgeOverlayOpacityPercent)
    }

    @Test(timeout = 60_000L)
    fun quietHoursSchedulesRoundTripThroughPreferences() {
        val preferences = UniVergePreferences(context)

        preferences.quietHoursSchedules = listOf(
            QuietHoursSchedule(id = "morning", startMinuteOfDay = 8 * 60, endMinuteOfDay = 9 * 60)
        )

        assertEquals(1, UniVergePreferences(context).quietHoursSchedules.size)
        assertEquals("morning", UniVergePreferences(context).quietHoursSchedules.first().id)
    }

    @Test(timeout = 60_000L)
    fun quietHoursCanActivatePauseWithoutTemporaryTimer() {
        val preferences = UniVergePreferences(context)

        preferences.quietHoursSchedules = listOf(
            QuietHoursSchedule(startMinuteOfDay = 8 * 60, endMinuteOfDay = 10 * 60)
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 30)
        }
        assertEquals(true, preferences.isQuietHoursActive(calendar.timeInMillis))
    }

    @Test(timeout = 60_000L)
    fun sideFunctionActionsRoundTripWithOrderingAndLimit() {
        val preferences = UniVergePreferences(context)

        preferences.sideFunctionCustomActionIds = listOf(
            "home",
            "launch_app:pkg",
            "",
            "recents",
            "notifications",
            "quick_settings",
            "screenshot"
        )

        assertEquals(
            listOf("home", "launch_app:pkg", "recents", "notifications", "quick_settings"),
            UniVergePreferences(context).sideFunctionCustomActionIds
        )
    }

    private fun clearPreferences() {
        context.getSharedPreferences("ubiki_touch_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
