package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureHintOverlayControllerTest {
    @Test
    fun hintWindowNeverReceivesTouchOrFocus() {
        val flags = gestureHintWindowFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }
}
