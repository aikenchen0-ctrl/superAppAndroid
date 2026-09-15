package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomGestureRecognizerReliabilityTest {
    @Test
    fun repeatedStationaryMovesDoNotPostponeUpwardHoldTimer() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(
                screenWidthDp = 400f,
                screenHeightDp = 800f,
                widthDp = 240f,
                heightDp = 32f
            ),
            actions = mapOf(
                GestureType.SWIPE_UP to GestureAction.Home,
                GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot
            ),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val moving = recognizer.onMove(PointerSample(200f, 700f, 100L))
        assertEquals(GestureType.SWIPE_UP, (moving as GestureSignal.Preview).gesture)

        // A platform can deliver repeated MOVE samples with the same coordinates
        // while the pointer is held still. They must not reset the pause window.
        recognizer.onMove(PointerSample(200.5f, 700.5f, 300L))
        recognizer.onHoldTimer(600L)

        val commit = recognizer.onUp(PointerSample(200.5f, 700.5f, 620L)) as GestureSignal.Commit
        assertEquals(GestureType.SWIPE_UP_HOLD, commit.gesture)
        assertTrue(commit.action == GestureAction.Screenshot)
    }

    @Test
    fun slowContinuousMovementDoesNotLookStationaryToTheHoldTimer() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(
                GestureType.SWIPE_UP to GestureAction.Home,
                GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot
            ),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        recognizer.onMove(PointerSample(200f, 700f, 100L))
        recognizer.onMove(PointerSample(200f, 699f, 200L))
        recognizer.onMove(PointerSample(200f, 698f, 300L))
        recognizer.onMove(PointerSample(200f, 697f, 400L))
        recognizer.onHoldTimer(600L)

        val preview = recognizer.onMove(PointerSample(200f, 696f, 601L)) as GestureSignal.Preview
        assertEquals(GestureType.SWIPE_UP, preview.gesture)
    }

    @Test
    fun movementAfterAnArmedPauseReturnsToTheNormalSwipeCandidate() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(
                GestureType.SWIPE_UP to GestureAction.Home,
                GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot
            ),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        recognizer.onMove(PointerSample(200f, 700f, 100L))
        recognizer.onHoldTimer(600L)

        val resumed = recognizer.onMove(PointerSample(200f, 650f, 620L)) as GestureSignal.Preview
        assertEquals(GestureType.SWIPE_UP, resumed.gesture)
    }
}
