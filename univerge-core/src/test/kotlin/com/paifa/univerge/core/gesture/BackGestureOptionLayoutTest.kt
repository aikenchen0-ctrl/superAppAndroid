package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureOptionLayoutTest {
    @Test
    fun optionBoxesGrowFromZeroAndHitUpperAndLowerRegions() {
        assertEquals(
            BackGestureOption.None,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 300f,
                touchX = 40f,
                touchY = 300f,
                progress = 0f,
                density = 1f
            )
        )
        assertEquals(
            BackGestureOption.FunctionOne,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 300f,
                touchX = 120f,
                touchY = 260f,
                progress = 1f,
                density = 1f
            )
        )
        assertEquals(
            BackGestureOption.FunctionTwo,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 300f,
                touchX = 120f,
                touchY = 340f,
                progress = 1f,
                density = 1f
            )
        )
    }

    @Test
    fun rightEdgeMirrorsInwardDistanceWithoutChangingOption() {
        val left = hitTestBackGestureOption(
            side = EdgeSide.LEFT,
            startX = 0f,
            startY = 300f,
            touchX = 120f,
            touchY = 260f,
            progress = 1f,
            density = 1f
        )
        val right = hitTestBackGestureOption(
            side = EdgeSide.RIGHT,
            startX = 1080f,
            startY = 300f,
            touchX = 960f,
            touchY = 260f,
            progress = 1f,
            density = 1f
        )

        assertEquals(left, right)
    }

    @Test
    fun optionLayoutScalesWithDensityAndKeepsBoxesSeparated() {
        val layout = backGestureOptionLayout(progress = 1f, density = 2f)
        val upper = layout.box(BackGestureOption.FunctionOne)
        val lower = layout.box(BackGestureOption.FunctionTwo)

        assertTrue(upper.widthPx > 0f)
        assertEquals(144f, upper.widthPx, 0.001f)
        assertEquals(88f, upper.heightPx, 0.001f)
        assertTrue(upper.centerYOffsetPx < lower.centerYOffsetPx)
        assertTrue(upper.centerYOffsetPx + upper.heightPx / 2f < lower.centerYOffsetPx - lower.heightPx / 2f)
    }

    @Test
    fun invalidDensityAndOutsidePointsNeverSelectAnOption() {
        assertEquals(
            BackGestureOption.None,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 300f,
                touchX = 120f,
                touchY = 260f,
                progress = 1f,
                density = 0f
            )
        )
        assertEquals(
            BackGestureOption.None,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 300f,
                touchX = 30f,
                touchY = 260f,
                progress = 1f,
                density = 1f
            )
        )
    }

    @Test
    fun hitTestingUsesTheSameViewportClampAsTheRenderedPanels() {
        assertEquals(
            BackGestureOption.FunctionOne,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 20f,
                touchX = 112f,
                touchY = 22f,
                progress = 1f,
                density = 1f,
                viewportHeightPx = 144f
            )
        )
        assertEquals(
            BackGestureOption.FunctionTwo,
            hitTestBackGestureOption(
                side = EdgeSide.LEFT,
                startX = 0f,
                startY = 124f,
                touchX = 112f,
                touchY = 122f,
                progress = 1f,
                density = 1f,
                viewportHeightPx = 144f
            )
        )
    }
}
