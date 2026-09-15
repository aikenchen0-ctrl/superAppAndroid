package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerRegressionTest {
    @Test
    fun verticalSideSwipeCommitsOnUpWithoutInwardDistance() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            actions = mapOf(GestureType.SWIPE_UP to GestureAction.Home)
        )

        recognizer.onDown(PointerSample(4f, 400f, 0L))
        val preview = recognizer.onMove(PointerSample(4f, 320f, 10L))
        assertTrue(preview is GestureSignal.Preview)
        assertEquals(GestureType.SWIPE_UP, (preview as GestureSignal.Preview).gesture)

        val commit = recognizer.onUp(PointerSample(4f, 300f, 20L))
        assertTrue(commit is GestureSignal.Commit)
        assertEquals(GestureType.SWIPE_UP, (commit as GestureSignal.Commit).gesture)
    }

    @Test
    fun smallOutwardJitterCanRecoverBeforeTheGestureCommits() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            actions = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        recognizer.onDown(PointerSample(8f, 400f, 0L))
        assertTrue(recognizer.onMove(PointerSample(4f, 400f, 10L)) !is GestureSignal.Cancel)
        val preview = recognizer.onMove(PointerSample(56f, 400f, 20L))
        assertTrue(preview is GestureSignal.Preview)

        val commit = recognizer.onUp(PointerSample(56f, 400f, 30L))
        assertTrue(commit is GestureSignal.Commit)
    }

    @Test
    fun diagonalLongUsesTheEuclideanFingerTravelDistance() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            actions = mapOf(GestureType.PULL_DIAGONAL_UP_LONG to GestureAction.Home),
            thresholds = SideGestureThresholds(
                minPullDistanceDp = 24f,
                longPullDistanceDp = 96f
            )
        )

        recognizer.onDown(PointerSample(8f, 400f, 0L))
        val preview = recognizer.onMove(PointerSample(88f, 320f, 10L)) as GestureSignal.Preview
        assertEquals(GestureType.PULL_DIAGONAL_UP_LONG, preview.gesture)
        val commit = recognizer.onUp(PointerSample(88f, 320f, 20L)) as GestureSignal.Commit
        assertEquals(GestureType.PULL_DIAGONAL_UP_LONG, commit.gesture)
    }
}
