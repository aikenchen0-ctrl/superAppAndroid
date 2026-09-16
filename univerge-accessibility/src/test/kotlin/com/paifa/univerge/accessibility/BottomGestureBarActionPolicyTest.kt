package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomGestureBarActionPolicyTest {
    @Test
    fun selectingNormalSwipeClearsTheConflictingPauseBinding() {
        val normalized = normalizeBottomGestureBarActionBindings(
            actions = mapOf(
                BottomGestureBarGestureType.SwipeUp to GestureAction.Home,
                BottomGestureBarGestureType.SwipeUpHold to GestureAction.Screenshot
            ),
            changedGesture = BottomGestureBarGestureType.SwipeUp,
            changedAction = GestureAction.Home
        )

        assertEquals(GestureAction.Home, normalized[BottomGestureBarGestureType.SwipeUp])
        assertEquals(GestureAction.None, normalized[BottomGestureBarGestureType.SwipeUpHold])
    }

    @Test
    fun selectingPauseClearsNormalSwipeButDisablingOneDoesNotTouchTheOther() {
        val pauseSelected = normalizeBottomGestureBarActionBindings(
            actions = mapOf(BottomGestureBarGestureType.SwipeUp to GestureAction.Home),
            changedGesture = BottomGestureBarGestureType.SwipeUpHold,
            changedAction = GestureAction.Screenshot
        )
        assertEquals(GestureAction.None, pauseSelected[BottomGestureBarGestureType.SwipeUp])
        assertEquals(GestureAction.Screenshot, pauseSelected[BottomGestureBarGestureType.SwipeUpHold])

        val disabled = normalizeBottomGestureBarActionBindings(
            actions = pauseSelected,
            changedGesture = BottomGestureBarGestureType.SwipeUpHold,
            changedAction = GestureAction.None
        )
        assertEquals(GestureAction.None, disabled[BottomGestureBarGestureType.SwipeUp])
        assertEquals(GestureAction.None, disabled[BottomGestureBarGestureType.SwipeUpHold])
    }
}
