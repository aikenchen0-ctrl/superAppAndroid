package com.paifa.univerge.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test

class BottomGestureBarDispatchTest {
    @Test
    fun nativeMoveDispatchesLongPressAsSoonAsItIsRecognized() {
        assertTrue(shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.LongPress))
    }

    @Test
    fun nativeMoveDispatchesDirectionalGesturesBeforeReleaseButKeepsTapReleaseBased() {
        assertTrue(shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.SwipeUp))
        assertTrue(shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.SwipeUpHold))
        assertTrue(shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.SwipeHorizontal))
        assertTrue(!shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.Tap))
    }
}
