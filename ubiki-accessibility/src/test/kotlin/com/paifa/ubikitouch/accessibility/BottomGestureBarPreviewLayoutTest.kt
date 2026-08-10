package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomGestureBarPreviewLayoutTest {
    @Test
    fun outlineUsesTheRealDisplayHeightIncludingTheNavigationBar() {
        val realDisplayHeight = 2_400
        val appContentHeight = 2_256

        assertEquals(
            realDisplayHeight,
            bottomGestureBarOutlineScreenHeightPx(realDisplayHeight, appContentHeight)
        )
    }

    @Test
    fun outlineWindowUsesTheRealDisplaySizeToAvoidNavigationBarClipping() {
        assertEquals(
            1_080 to 2_400,
            bottomGestureBarOutlineWindowSize(
                realDisplayWidthPx = 1_080,
                realDisplayHeightPx = 2_400,
                fallbackWidthPx = 1_080,
                fallbackHeightPx = 2_256
            )
        )
    }

    @Test
    fun persistentOutlineTracksTheGlobalIndicatorSwitch() {
        assertEquals(true, shouldShowBottomGestureBarIndicator(showIndicators = true, barVisible = true))
        assertEquals(false, shouldShowBottomGestureBarIndicator(showIndicators = false, barVisible = true))
        assertEquals(false, shouldShowBottomGestureBarIndicator(showIndicators = true, barVisible = false))
    }

    @Test
    fun previewBoundsMatchTheConfiguredBottomTouchWidth() {
        val bounds = bottomGestureBarPreviewBounds(
            screenWidthPx = 1_080,
            screenHeightPx = 2_400,
            density = 1f,
            widthDp = 200
        )

        assertEquals(440f, bounds.left, 0.001f)
        assertEquals(640f, bounds.right, 0.001f)
        assertEquals(2_370f, bounds.top, 0.001f)
        assertEquals(2_400f, bounds.bottom, 0.001f)
    }
}
