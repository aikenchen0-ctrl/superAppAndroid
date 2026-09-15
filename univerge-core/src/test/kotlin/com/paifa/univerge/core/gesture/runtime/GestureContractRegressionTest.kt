package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureContractRegressionTest {
    @Test(expected = IllegalArgumentException::class)
    fun snapshotRejectsNonPositiveRevision() {
        ConfigSnapshot(revision = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotRejectsDuplicateZoneIdsOnOneSide() {
        ConfigSnapshot(
            sideZones = mapOf(
                EdgeSide.LEFT to listOf(
                    HotZoneSegment(0f, 100f, zoneId = 0),
                    HotZoneSegment(200f, 100f, zoneId = 0)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotRejectsInvalidLaunchPackageName() {
        ConfigSnapshot(
            sideActions = mapOf(
                EdgeSide.LEFT to mapOf(
                    GestureType.PULL_INWARD_SHORT to GestureAction.LaunchApp("not a package")
                )
            )
        )
    }

    @Test
    fun triggerRectsKeepTheThicknessOfEachOverlappingZone() {
        val rects = HotZoneGeometry.triggerRects(
            listOf(
                HotZoneSegment(0f, 100f, thicknessDp = 10f, zoneId = 0),
                HotZoneSegment(50f, 100f, thicknessDp = 30f, zoneId = 1)
            ),
            screenLengthDp = 200f
        )

        assertEquals(listOf(10f, 30f), rects.map { it.thicknessDp })
        assertEquals(listOf(0, 1), rects.map { it.zoneId })
    }

    @Test
    fun moveReusesPreviewAndCommitCarriesSessionIdentity() {
        val snapshot = ConfigSnapshot(
            sideZones = mapOf(EdgeSide.LEFT to listOf(HotZoneSegment(0f, 200f, zoneId = 0))),
            sideActions = mapOf(EdgeSide.LEFT to mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)),
            revision = 4L
        )
        val recognizer = SideGestureRecognizer(snapshot, EdgeSide.LEFT, 400f, 800f)

        recognizer.onDown(PointerSample(4f, 100f, 0L, pointerId = 7))
        val first = recognizer.onMove(PointerSample(40f, 100f, 10L, pointerId = 7)) as GestureSignal.Preview
        val second = recognizer.onMove(PointerSample(55f, 100f, 20L, pointerId = 7)) as GestureSignal.Preview

        assertSame(first, second)
        val commit = recognizer.onUp(PointerSample(55f, 100f, 30L, pointerId = 7)) as GestureSignal.Commit
        assertEquals(4L, commit.snapshotVersion)
        assertEquals(7, commit.activePointerId)
        assertTrue(commit.gestureId > 0L)
        assertNotSame(first, commit)
    }

    @Test
    fun partialRollbackAndOutwardEscapeCancelPermanently() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f, zoneId = 0)),
            mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )
        recognizer.onDown(PointerSample(4f, 100f, 0L, pointerId = 1))
        recognizer.onMove(PointerSample(80f, 100f, 10L, pointerId = 1))
        assertTrue(recognizer.onMove(PointerSample(12f, 100f, 20L, pointerId = 1)) is GestureSignal.Cancel)
        assertTrue(recognizer.onUp(PointerSample(100f, 100f, 30L, pointerId = 1)) is GestureSignal.Ignored)

        val outward = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f, zoneId = 0)),
            emptyMap()
        )
        outward.onDown(PointerSample(4f, 100f, 0L, pointerId = 2))
        assertTrue(outward.onMove(PointerSample(-20f, 100f, 10L, pointerId = 2)) is GestureSignal.Cancel)
    }

    @Test
    fun bottomRecognizesHorizontalAndDoesNotCommitReplacementPointer() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.SWIPE_LEFT to GestureAction.Back)
        )
        recognizer.onDown(PointerSample(200f, 785f, 0L, pointerId = 3))
        assertTrue(recognizer.onMove(PointerSample(120f, 785f, 20L, pointerId = 4)) is GestureSignal.Cancel)

        val second = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.SWIPE_LEFT to GestureAction.Back)
        )
        second.onDown(PointerSample(200f, 785f, 0L, pointerId = 3))
        assertTrue(second.onMove(PointerSample(120f, 785f, 20L, pointerId = 3)) is GestureSignal.Preview)
        assertTrue(second.onUp(PointerSample(120f, 785f, 30L, pointerId = 3)) is GestureSignal.Commit)
    }
}
