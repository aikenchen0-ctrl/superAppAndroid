package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureProgressTest {
    @Test
    fun progressClampsBetweenZeroAndOne() {
        val halfway = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 24f,
            dy = 0f,
            thresholdPx = 48f
        )
        val beyond = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 96f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(halfway)
        assertEquals(0.5f, halfway!!.progress, 0.001f)
        assertNotNull(beyond)
        assertEquals(1f, beyond!!.progress, 0.001f)
    }

    @Test
    fun releaseBelowLongThresholdUsesShortDistanceGesture() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 32f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(progress)
        assertTrue(progress!!.committed)
        assertEquals(GestureType.PULL_INWARD_SHORT, progress.gestureType)
    }

    @Test
    fun releaseAtThresholdCommits() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -48f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(progress)
        assertTrue(progress!!.committed)
    }

    @Test
    fun preservesTouchYForVisualAnchoring() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 48f,
            dy = 0f,
            thresholdPx = 48f,
            touchY = 320f
        )

        assertNotNull(progress)
        assertEquals(320f, progress!!.touchY, 0.001f)
    }

    @Test
    fun splitsInwardPullIntoThreeSlopeSectors() {
        val upward = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 80f,
            dy = -48f,
            thresholdPx = 48f
        )
        val middle = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 80f,
            dy = 12f,
            thresholdPx = 48f
        )
        val downward = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -80f,
            dy = 48f,
            thresholdPx = 48f
        )

        assertEquals(GestureType.PULL_DIAGONAL_UP_SHORT, upward!!.gestureType)
        assertEquals(GestureType.PULL_INWARD_SHORT, middle!!.gestureType)
        assertEquals(GestureType.PULL_DIAGONAL_DOWN_SHORT, downward!!.gestureType)
    }

    @Test
    fun separatesStraightInwardPullIntoShortAndLongDistanceGestures() {
        val short = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 12f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )
        val long = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -112f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )

        assertEquals(GestureType.PULL_INWARD_SHORT, short!!.gestureType)
        assertTrue(short.committed)
        assertEquals(GestureType.PULL_INWARD_LONG, long!!.gestureType)
    }

    @Test
    fun shortAndLongDistanceAreSplitOnlyByConfiguredLongThreshold() {
        val tiny = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 2f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 120f,
            minimumDragDistancePx = 1f
        )
        val justBelowLong = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 119f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 120f
        )
        val long = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 120f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 120f
        )

        assertEquals(GestureType.PULL_INWARD_SHORT, tiny!!.gestureType)
        assertEquals(GestureType.PULL_INWARD_SHORT, justBelowLong!!.gestureType)
        assertEquals(GestureType.PULL_INWARD_LONG, long!!.gestureType)
        assertTrue(tiny.committed)
        assertTrue(justBelowLong.committed)
    }

    @Test
    fun longProgressUsesLongDistanceThresholdForBubbleStretch() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 72f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 120f
        )

        assertNotNull(progress)
        assertEquals(0.6f, progress!!.visualProgress, 0.001f)
        assertTrue(progress.committed)
        assertFalse(progress.longCommitted)
    }

    @Test
    fun diagonalPullDistanceUsesActualFingerTravelForShortAndLongAnimation() {
        val shortDiagonal = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 48f,
            dy = -36f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )
        val longDiagonal = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -80f,
            dy = 72f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )

        assertNotNull(shortDiagonal)
        assertEquals(GestureType.PULL_DIAGONAL_UP_SHORT, shortDiagonal!!.gestureType)
        assertEquals(60f, shortDiagonal.dragDistancePx, 0.001f)
        assertTrue(shortDiagonal.committed)
        assertFalse(shortDiagonal.longCommitted)

        assertNotNull(longDiagonal)
        assertEquals(GestureType.PULL_DIAGONAL_DOWN_LONG, longDiagonal!!.gestureType)
        assertEquals(107.63f, longDiagonal.dragDistancePx, 0.01f)
        assertTrue(longDiagonal.longCommitted)
        assertEquals(1f, longDiagonal.visualProgress, 0.001f)
    }

    @Test
    fun rejectsOutwardAndMostlyVerticalMovement() {
        assertNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = -48f,
                dy = 0f,
                thresholdPx = 48f
            )
        )
        assertNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = 8f,
                dy = 80f,
                thresholdPx = 48f
            )
        )
    }

    @Test
    fun rejectsTinyInwardMovementBeforePreviewActivation() {
        assertNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = 3f,
                dy = 0f,
                thresholdPx = 48f,
                minimumDragDistancePx = 8f
            )
        )
        assertNotNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = 8f,
                dy = 0f,
                thresholdPx = 48f,
                minimumDragDistancePx = 8f
            )
        )
    }
}
