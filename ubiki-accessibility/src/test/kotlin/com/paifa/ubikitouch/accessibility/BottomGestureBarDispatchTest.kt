package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test

class BottomGestureBarDispatchTest {
    @Test
    fun nativeMoveDispatchesLongPressAsSoonAsItIsRecognized() {
        assertTrue(shouldDispatchBottomGestureDuringMove(BottomGestureBarGestureType.LongPress))
    }
}
