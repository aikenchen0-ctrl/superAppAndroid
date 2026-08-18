package com.paifa.univerge.app

import com.paifa.univerge.app.EdgeConfigAdjustmentMode
import com.paifa.univerge.app.edgeConfigAdjustmentMode
import com.paifa.univerge.app.shouldPersistBottomGestureBarWidth
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeConfigAdjustmentModeTest {
    @Test
    fun continuousSliderAdjustmentOnlyUpdatesThePreview() {
        assertEquals(EdgeConfigAdjustmentMode.Preview, edgeConfigAdjustmentMode(isFinished = false))
    }

    @Test
    fun completedSliderAdjustmentPersistsTheDraft() {
        assertEquals(EdgeConfigAdjustmentMode.Persist, edgeConfigAdjustmentMode(isFinished = true))
    }

    @Test
    fun bottomGestureWidthOnlyPersistsAfterAdjustmentEnds() {
        assertEquals(false, shouldPersistBottomGestureBarWidth(isFinished = false))
        assertEquals(true, shouldPersistBottomGestureBarWidth(isFinished = true))
    }
}
