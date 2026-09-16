package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomGestureRecognizerReliabilityTest {
    @Test
    fun horizontalSwipeCommitsWhenTheDirectionThresholdIsCrossed() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.SWIPE_RIGHT to GestureAction.Back)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onMove(PointerSample(260f, 785f, 100L))

        assertEquals(GestureType.SWIPE_RIGHT, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Back, commit.action)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(260f, 785f, 120L)))
    }

    @Test
    fun upwardSwipeWithoutHoldBindingCommitsBeforeRelease() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.SWIPE_UP to GestureAction.Home)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onMove(PointerSample(200f, 700f, 80L))

        assertEquals(GestureType.SWIPE_UP, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Home, commit.action)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(200f, 700f, 100L)))
    }

    @Test
    fun longPressCommitsFromTheTimerWithoutWaitingForUp() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.LONG_PRESS to GestureAction.Notifications),
            thresholds = BottomGestureThresholds(longPressDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onHoldTimer(500L)

        assertEquals(GestureType.LONG_PRESS, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Notifications, commit.action)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(200f, 785f, 520L)))
    }

    @Test
    fun upwardPauseCommitsHoldFromTheTimerAndDoesNotFallBackToSwipeUp() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        recognizer.onMove(PointerSample(200f, 700f, 100L))
        val commit = recognizer.onHoldTimer(600L)

        assertEquals(GestureType.SWIPE_UP_HOLD, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Screenshot, commit.action)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(200f, 700f, 620L)))
    }

    @Test
    fun boundUpwardSwipeWinsImmediatelyWhenThePauseActionIsAlsoPresent() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(
                GestureType.SWIPE_UP to GestureAction.Home,
                GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot
            ),
            thresholds = BottomGestureThresholds(
                upwardHoldDurationMs = 500L,
                upwardSwipeDisambiguationMs = 120L
            )
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onMove(PointerSample(200f, 700f, 80L))

        assertEquals(GestureType.SWIPE_UP, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Home, commit.action)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(200f, 700f, 100L)))
    }

    @Test
    fun repeatedStationaryMovesDoNotPostponeUpwardHoldTimer() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(
                screenWidthDp = 400f,
                screenHeightDp = 800f,
                widthDp = 240f,
                heightDp = 32f
            ),
            actions = mapOf(GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val moving = recognizer.onMove(PointerSample(200f, 700f, 100L))
        assertEquals(GestureType.SWIPE_UP, (moving as GestureSignal.Preview).gesture)

        // A platform can deliver repeated MOVE samples with the same coordinates
        // while the pointer is held still. They must not reset the pause window.
        recognizer.onMove(PointerSample(200.5f, 700.5f, 300L))
        val commit = recognizer.onHoldTimer(600L) as GestureSignal.Commit
        assertEquals(GestureType.SWIPE_UP_HOLD, commit.gesture)
        assertTrue(commit.action == GestureAction.Screenshot)
        assertEquals(GestureSignal.Ignored, recognizer.onUp(PointerSample(200.5f, 700.5f, 620L)))
    }

    @Test
    fun upwardHoldTimerWinsOnceThePauseWindowIsReached() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        recognizer.onMove(PointerSample(200f, 700f, 100L))
        val commit = recognizer.onHoldTimer(600L) as GestureSignal.Commit
        assertEquals(GestureType.SWIPE_UP_HOLD, commit.gesture)
        assertEquals(GestureSignal.Ignored, recognizer.onMove(PointerSample(200f, 650f, 620L)))
    }

    @Test
    fun explicitlyEnabledHoldCanBeRecognizedWithoutBindingAnAction() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = emptyMap(),
            enabledGestures = setOf(GestureType.SWIPE_UP, GestureType.SWIPE_UP_HOLD),
            thresholds = BottomGestureThresholds(upwardHoldDurationMs = 500L)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        recognizer.onMove(PointerSample(200f, 700f, 100L))
        val commit = recognizer.onHoldTimer(600L) as GestureSignal.Commit

        assertEquals(GestureType.SWIPE_UP_HOLD, commit.gesture)
        assertEquals(GestureAction.None, commit.action)
    }

    @Test
    fun releaseCommitClosesTheBottomTransactionImmediately() {
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            actions = mapOf(GestureType.TAP to GestureAction.Recents)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onUp(PointerSample(200f, 785f, 120L))

        assertTrue(commit is GestureSignal.Commit)
        assertFalse(recognizer.hasActiveGesture)
        assertTrue(recognizer.updateGeometry(BottomBarConfig(400f, 800f, widthDp = 200f, heightDp = 32f)))
    }
}
