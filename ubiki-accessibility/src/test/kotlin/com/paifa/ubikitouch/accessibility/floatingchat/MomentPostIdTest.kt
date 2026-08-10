package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.moments.scrmCircleIdForMomentPostId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentPostIdTest {
    @Test
    fun parsesOnlyScrmMomentIds() {
        assertEquals(42L, scrmCircleIdForMomentPostId("scrm-moment:42"))
        assertNull(scrmCircleIdForMomentPostId("local-moment-42"))
        assertNull(scrmCircleIdForMomentPostId("scrm-moment-invalid"))
    }
}
