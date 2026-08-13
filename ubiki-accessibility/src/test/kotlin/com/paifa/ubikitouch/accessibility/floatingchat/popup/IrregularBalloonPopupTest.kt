package com.paifa.ubikitouch.accessibility.floatingchat.popup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrregularBalloonPopupTest {
    @Test
    fun popupOpensAwayFromTriggerAndClampsToViewport() {
        val rightTrigger = Rect(280f, 80f, 312f, 112f)
        val placement = calculateBalloonPlacement(
            triggerBounds = rightTrigger,
            popupSize = IntSize(140, 90),
            viewportSize = IntSize(320, 480),
            edgePaddingPx = 8f,
            gapPx = 12f
        )

        assertFalse(placement.expandToRight)
        assertEquals(128f, placement.offset.x, 0f)
        assertTrue(placement.offset.y >= 8f)
    }

    @Test
    fun leftTriggerExpandsRightAndBottomEdgeIsAvoided() {
        val placement = calculateBalloonPlacement(
            triggerBounds = Rect(8f, 450f, 40f, 478f),
            popupSize = IntSize(160, 100),
            viewportSize = IntSize(320, 480),
            edgePaddingPx = 8f,
            gapPx = 12f
        )

        assertTrue(placement.expandToRight)
        assertEquals(52f, placement.offset.x, 0f)
        assertEquals(372f, placement.offset.y, 0f)
    }

    @Test
    fun coordinateStateTracksTriggerAndBubbleBounds() {
        val state = BalloonCoordinateState()
        val trigger = Rect(10f, 20f, 30f, 40f)
        val bubble = Rect(70f, 20f, 110f, 60f)

        state.updateTrigger(trigger)
        state.updateBubble("primary", bubble)

        assertEquals(trigger, state.triggerBounds)
        assertEquals(bubble, state.bubbleBounds["primary"])
        val versionAfterUpdates = state.version
        state.removeBubble("primary")
        assertTrue(state.version > versionAfterUpdates)
        assertTrue(state.bubbleBounds.isEmpty())
    }

    @Test
    fun triggerToggleAndOutsideTapClosePopup() {
        val state = BalloonPopupState()

        state.toggle()
        assertTrue(state.isVisible)
        state.toggle()
        assertFalse(state.isVisible)
        state.show()
        state.dismiss()
        assertFalse(state.isVisible)
    }
}
