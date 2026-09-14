package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ConfigSnapshotTest {
    @Test
    fun copiesNestedCollectionsAndResolvesSideAndBottomActions() {
        val zones = mutableListOf(HotZoneSegment(0f, 100f))
        val sideActions = mutableMapOf<GestureType, GestureAction>(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        val snapshot = ConfigSnapshot(
            sideZones = mapOf(EdgeSide.LEFT to zones),
            bottomBar = BottomBarConfig(screenWidthDp = 400f, screenHeightDp = 800f, widthDp = 200f),
            sideActions = mapOf(EdgeSide.LEFT to sideActions),
            bottomActions = mapOf(GestureType.SWIPE_UP to GestureAction.Home),
            revision = 7L
        )

        zones += HotZoneSegment(200f, 50f)
        sideActions[GestureType.SWIPE_DOWN] = GestureAction.Recents

        assertEquals(1, snapshot.sideZones.getValue(EdgeSide.LEFT).size)
        assertEquals(GestureAction.Back, snapshot.actionFor(EdgeSide.LEFT, GestureType.PULL_INWARD_SHORT))
        assertEquals(GestureAction.Home, snapshot.bottomActionFor(GestureType.SWIPE_UP))
        assertEquals(7L, snapshot.revision)
        assertEquals(1f, snapshot.density, 0.001f)
        assertNotSame(zones, snapshot.sideZones.getValue(EdgeSide.LEFT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDensity() {
        ConfigSnapshot(revision = 1L, density = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanFourZonesOnOneSide() {
        ConfigSnapshot(
            sideZones = mapOf(
                EdgeSide.LEFT to (0 until 5).map { index -> HotZoneSegment(index * 10f, 5f, zoneId = index) }
            ),
            revision = 1L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBottomBarOutsideScreenBounds() {
        ConfigSnapshot(
            bottomBar = BottomBarConfig(screenWidthDp = 400f, screenHeightDp = 800f, widthDp = 401f),
            revision = 1L
        )
    }
}
