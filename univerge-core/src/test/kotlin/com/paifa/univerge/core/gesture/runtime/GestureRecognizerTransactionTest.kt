package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTransactionTest {
    @Test
    fun terminalDataCarriesTheRecognizerTransactionId() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(HotZoneSegment(0f, 800f, 24f, zoneId = 7)),
            actions = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        recognizer.onDown(PointerSample(8f, 160f, 0L))
        val commit = recognizer.onUp(PointerSample(64f, 160f, 40L)) as GestureSignal.Commit

        assertEquals(commit.gestureId, commit.data.gestureId)
        assertTrue(commit.gestureId > 0L)
    }
    @Test
    fun primitiveMoveReusesPreviewAndOnlyUpCommits() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        recognizer.onDown(xDp = 4f, yDp = 100f, timeMillis = 0L, pointerId = 7)
        val first = recognizer.onMove(40f, 100f, 10L, pointerId = 7) as GestureSignal.Preview
        val second = recognizer.onMove(60f, 100f, 20L, pointerId = 7) as GestureSignal.Preview

        assertSame(first, second)
        assertEquals(60f, second.data.endX, 0.001f)
        assertTrue(recognizer.onHoldTimer(900L) !is GestureSignal.Commit)

        val commit = recognizer.onUp(60f, 100f, 30L, pointerId = 7) as GestureSignal.Commit
        assertEquals(7, commit.activePointerId)
        assertTrue(commit.gestureId > 0L)
        assertTrue(recognizer.onUp(60f, 100f, 40L, pointerId = 7) is GestureSignal.Ignored)
    }

    @Test
    fun inwardRollbackAndOutwardEscapeCancelPermanently() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            emptyMap()
        )
        recognizer.onDown(4f, 100f, 0L, pointerId = 1)
        recognizer.onMove(80f, 100f, 10L, pointerId = 1)
        assertTrue(recognizer.onMove(12f, 100f, 20L, pointerId = 1) is GestureSignal.Cancel)
        assertTrue(recognizer.onUp(100f, 100f, 30L, pointerId = 1) is GestureSignal.Ignored)

        val outward = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            emptyMap()
        )
        outward.onDown(4f, 100f, 0L, pointerId = 2)
        assertTrue(outward.onMove(-20f, 100f, 10L, pointerId = 2) is GestureSignal.Cancel)
    }

    @Test
    fun slightRetractionAfterPreviewKeepsTheSessionAndCommitsTheCurrentGesture() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        recognizer.onDown(4f, 100f, 0L, pointerId = 1)
        assertTrue(recognizer.onMove(80f, 100f, 10L, pointerId = 1) is GestureSignal.Preview)

        // The finger has moved back by 12dp, but is still well inside the
        // activation band. This must remain recoverable instead of cancelling.
        assertTrue(recognizer.onMove(68f, 100f, 20L, pointerId = 1) is GestureSignal.Preview)
        assertTrue(recognizer.onUp(68f, 100f, 30L, pointerId = 1) is GestureSignal.Commit)
    }

    @Test
    fun holdCandidateIsStickyAgainstOrthogonalSlopAndTimerNeverCommits() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)),
            mapOf(GestureType.PULL_INWARD_HOLD to GestureAction.Recents),
            thresholds = SideGestureThresholds(holdDurationMs = 400L)
        )
        recognizer.onDown(5f, 200f, 0L, pointerId = 3)
        recognizer.onMove(35f, 220f, 20L, pointerId = 3)
        assertTrue(recognizer.onHoldTimer(500L) !is GestureSignal.Commit)
        recognizer.onMove(35f, 200f, 600L, pointerId = 3)
        val commit = recognizer.onUp(35f, 200f, 620L, pointerId = 3) as GestureSignal.Commit
        assertTrue(commit.gesture != GestureType.PULL_INWARD_HOLD)
    }

    @Test
    fun pointerReplacementCancelsActiveSession() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.SWIPE_LEFT to GestureAction.Back)
        )
        recognizer.onDown(200f, 785f, 0L, pointerId = 11)
        val cancel = recognizer.onMove(100f, 785f, 20L, pointerId = 12) as GestureSignal.Cancel
        assertEquals(11, cancel.activePointerId)
        assertTrue(recognizer.onUp(100f, 785f, 30L, pointerId = 11) is GestureSignal.Ignored)
    }

    @Test
    fun commitPreservesConfiguredNonContiguousZoneId() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT,
            400f,
            800f,
            listOf(
                HotZoneSegment(0f, 200f, thicknessDp = 32f, zoneId = 10),
                HotZoneSegment(300f, 200f, thicknessDp = 32f, zoneId = 42)
            ),
            mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        recognizer.onDown(4f, 350f, 0L, pointerId = 5)
        val commit = recognizer.onUp(60f, 350f, 30L, pointerId = 5) as GestureSignal.Commit

        assertEquals(42, commit.zoneId)
    }
}
