package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingChatGestureExclusionTest {
    @Test
    fun expandedChatBottomExclusionMatchesConfiguredBarAtThePhysicalBottom() {
        val rect = floatingChatBottomGestureExclusionRect(
            screenWidthPx = 1_080,
            screenHeightPx = 2_400,
            density = 3f,
            widthDp = 200,
            heightDp = 30
        )

        assertEquals(240, rect?.left)
        assertEquals(840, rect?.right)
        assertEquals(2_310, rect?.top)
        assertEquals(2_400, rect?.bottom)
    }

    @Test
    fun invalidDisplayOrDensityDoesNotPublishABottomExclusion() {
        assertNull(floatingChatBottomGestureExclusionRect(0, 2_400, 3f, 200, 30))
        assertNull(floatingChatBottomGestureExclusionRect(1_080, 0, 3f, 200, 30))
        assertNull(floatingChatBottomGestureExclusionRect(1_080, 2_400, 0f, 200, 30))
    }
}
