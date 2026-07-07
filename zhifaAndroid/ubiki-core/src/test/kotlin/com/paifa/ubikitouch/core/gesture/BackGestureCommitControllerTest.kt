package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureCommitControllerTest {
    @Test
    fun commitsOnceWhenValidInwardProgressStarts() {
        val controller = BackGestureCommitController()
        val below = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 32f,
            dy = 0f,
            thresholdPx = 48f
        )!!
        val atThreshold = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 48f,
            dy = 0f,
            thresholdPx = 48f
        )!!
        val beyond = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 72f,
            dy = 0f,
            thresholdPx = 48f
        )!!

        assertTrue(controller.update(below))
        assertFalse(controller.update(atThreshold))
        assertFalse(controller.update(beyond))
        assertTrue(controller.committed)
    }

    @Test
    fun resetAllowsNextGestureToCommit() {
        val controller = BackGestureCommitController()
        val committedProgress = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -48f,
            dy = 0f,
            thresholdPx = 48f
        )!!

        assertTrue(controller.update(committedProgress))
        controller.reset()

        assertFalse(controller.committed)
        assertTrue(controller.update(committedProgress))
    }

    @Test
    fun commitsShortPullBelowDistanceThreshold() {
        val controller = BackGestureCommitController()
        val fastShortPull = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 28f,
            dy = 0f,
            thresholdPx = 64f
        )!!

        assertTrue(controller.update(fastShortPull, velocityPxPerSecond = 920f))
        assertTrue(controller.committed)
    }

    @Test
    fun slowShortPullStillCommitsBelowDistanceThreshold() {
        val controller = BackGestureCommitController()
        val slowShortPull = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 28f,
            dy = 0f,
            thresholdPx = 64f
        )!!

        assertTrue(controller.update(slowShortPull, velocityPxPerSecond = 260f))
        assertTrue(controller.committed)
    }

    @Test
    fun fastShortPullStillCommitsOnlyOnce() {
        val controller = BackGestureCommitController()
        val fastShortPull = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -28f,
            dy = 0f,
            thresholdPx = 64f
        )!!

        assertTrue(controller.update(fastShortPull, velocityPxPerSecond = 920f))
        assertFalse(controller.update(fastShortPull, velocityPxPerSecond = 920f))
    }

    @Test
    fun canCommitShortThenLongPullInOneContinuousGesture() {
        val controller = BackGestureCommitController()
        val shortPull = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 48f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )!!
        val stillShortPull = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 72f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )!!
        val longPull = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 108f,
            dy = 0f,
            thresholdPx = 40f,
            longThresholdPx = 96f
        )!!

        assertTrue(controller.update(shortPull))
        assertFalse(controller.update(stillShortPull))
        assertTrue(controller.update(longPull))
        assertFalse(controller.update(longPull))
    }
}
