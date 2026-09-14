package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotZoneGeometryTest {
    @Test
    fun normalizesDisabledClipsAndMergesOverlappingSegments() {
        val result = HotZoneGeometry.normalize(
            listOf(
                HotZoneSegment(startDp = -20f, lengthDp = 80f, thicknessDp = 30f),
                HotZoneSegment(startDp = 50f, lengthDp = 100f, thicknessDp = 20f),
                HotZoneSegment(startDp = 200f, lengthDp = 20f, enabled = false)
            ),
            screenLengthDp = 120f
        )

        assertEquals(listOf(HotZoneRect(0f, 120f, 30f)), result)
    }

    @Test
    fun reportUsesUnionLengthPerSideAndFlagsOnlySideOverTwoHundredDp() {
        val report = HotZoneGeometry.report(
            mapOf(
                EdgeSide.LEFT to listOf(HotZoneSegment(0f, 150f), HotZoneSegment(150f, 100f)),
                EdgeSide.RIGHT to listOf(HotZoneSegment(0f, 100f), HotZoneSegment(80f, 20f))
            ),
            screenLengthDp = 500f
        )

        assertEquals(250f, report.forSide(EdgeSide.LEFT).unionLengthDp, 0.001f)
        assertTrue(report.forSide(EdgeSide.LEFT).over200Dp)
        assertEquals(100f, report.forSide(EdgeSide.RIGHT).unionLengthDp, 0.001f)
        assertTrue(!report.forSide(EdgeSide.RIGHT).over200Dp)
    }

    @Test
    fun triggerRectsKeepEachZoneThicknessWhileReportMergesOnlyBudgetIntervals() {
        val segments = listOf(
            HotZoneSegment(0f, 100f, thicknessDp = 10f, zoneId = 0),
            HotZoneSegment(50f, 100f, thicknessDp = 30f, zoneId = 1)
        )

        val triggerRects = HotZoneGeometry.triggerRects(segments, screenLengthDp = 200f, density = 2f)
        val side = HotZoneGeometry.report(
            mapOf(EdgeSide.LEFT to segments),
            screenLengthDp = 200f,
            density = 2f
        ).forSide(EdgeSide.LEFT)

        assertEquals(listOf(10f, 30f), triggerRects.map { it.thicknessDp })
        assertEquals(listOf(0, 1), triggerRects.map { it.zoneId })
        assertEquals(150f, side.unionLengthDp, 0.001f)
        assertEquals(300f, side.unionLengthPx, 0.001f)
        assertEquals(1, side.mergedIntervals.size)
        assertEquals(0f, side.mergedIntervals.single().startPx, 0.001f)
        assertEquals(300f, side.mergedIntervals.single().endPx, 0.001f)
        assertTrue(side.withinBudget)
        assertFalse(side.over200Dp)
    }

    @Test
    fun reportTreatsExactlyTwoHundredDpAsWithinBudget() {
        val side = HotZoneGeometry.report(
            mapOf(EdgeSide.LEFT to listOf(HotZoneSegment(0f, 200f))),
            screenLengthDp = 500f,
            density = 3f
        ).forSide(EdgeSide.LEFT)

        assertEquals(200f, side.unionLengthDp, 0.001f)
        assertEquals(600f, side.unionLengthPx, 0.001f)
        assertTrue(side.withinBudget)
        assertEquals(ExclusionBackend.DETERMINISTIC_EXCLUSION, side.backend)
    }
}
