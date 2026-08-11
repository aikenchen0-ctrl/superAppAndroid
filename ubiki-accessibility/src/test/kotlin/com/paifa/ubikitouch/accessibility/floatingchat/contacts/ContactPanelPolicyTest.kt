package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPanelPolicyTest {
    @Test
    fun userProfileOverlayUsesActivityLikeFullScreenSurface() {
        assertTrue(contactEditPanelUsesFullScreenProfileSurface())
    }

    @Test
    fun userProfileOverlayReservesStatusBarHeight() {
        assertTrue(contactEditPanelReservesStatusBarHeightDp() == 30)
    }
}
