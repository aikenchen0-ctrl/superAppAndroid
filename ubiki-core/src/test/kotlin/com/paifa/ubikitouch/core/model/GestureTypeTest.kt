package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureTypeTest {
    @Test
    fun parsesPullInwardHoldId() {
        assertEquals(GestureType.PULL_INWARD_HOLD, GestureType.fromId("pull_inward_hold"))
    }

    @Test
    fun parsesShortAndLongPullInwardIds() {
        assertEquals(GestureType.PULL_INWARD_SHORT, GestureType.fromId("pull_inward_short"))
        assertEquals(GestureType.PULL_INWARD_LONG, GestureType.fromId("pull_inward_long"))
    }

    @Test
    fun parsesShortAndLongDiagonalPullIds() {
        assertEquals(GestureType.PULL_DIAGONAL_UP_SHORT, GestureType.fromId("pull_diagonal_up_short"))
        assertEquals(GestureType.PULL_DIAGONAL_UP_LONG, GestureType.fromId("pull_diagonal_up_long"))
        assertEquals(GestureType.PULL_DIAGONAL_DOWN_SHORT, GestureType.fromId("pull_diagonal_down_short"))
        assertEquals(GestureType.PULL_DIAGONAL_DOWN_LONG, GestureType.fromId("pull_diagonal_down_long"))
    }

    @Test
    fun returnsTapForUnknownIds() {
        assertEquals(GestureType.TAP, GestureType.fromId("unknown"))
    }
}
