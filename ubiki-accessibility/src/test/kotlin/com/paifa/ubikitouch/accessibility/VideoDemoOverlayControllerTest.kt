package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDemoOverlayControllerTest {
    @Test
    fun togglesBetweenShowingAndHiding() {
        assertEquals(
            VideoDemoOverlayCommand.Show,
            nextVideoDemoOverlayCommand(isShowing = false)
        )
        assertEquals(
            VideoDemoOverlayCommand.Hide,
            nextVideoDemoOverlayCommand(isShowing = true)
        )
    }

    @Test
    fun occupiesTheFullAvailableScreen() {
        assertEquals(0, videoDemoOverlayHeightPx(screenHeightPx = 0))
        assertEquals(1, videoDemoOverlayHeightPx(screenHeightPx = 1))
        assertEquals(2400, videoDemoOverlayHeightPx(screenHeightPx = 2400))
    }
}
