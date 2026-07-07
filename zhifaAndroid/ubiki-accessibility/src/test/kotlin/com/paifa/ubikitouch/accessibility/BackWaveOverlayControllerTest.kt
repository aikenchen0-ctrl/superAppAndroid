package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackWaveOverlayControllerTest {
    @Test
    fun edgeBubbleGrowsFromZeroHeightAndSamplesParabola() {
        val hidden = backWaveVisualLayout(
            density = 1f,
            progress = 0f,
            stretchProgress = 0f,
            longDistance = false,
            startY = 320f,
            touchY = 320f
        )
        val middle = backWaveVisualLayout(
            density = 1f,
            progress = 0.5f,
            stretchProgress = 0.5f,
            longDistance = false,
            startY = 320f,
            touchY = 320f
        )
        val full = backWaveVisualLayout(
            density = 1f,
            progress = 1f,
            stretchProgress = 1f,
            longDistance = false,
            startY = 320f,
            touchY = 320f
        )

        assertEquals(0f, hidden.halfHeightPx, 0.001f)
        assertEquals(0f, hidden.alphaProgress, 0.001f)
        assertTrue(middle.halfHeightPx > hidden.halfHeightPx)
        assertTrue(full.halfHeightPx > middle.halfHeightPx)
        assertEquals(1f, full.alphaProgress, 0.001f)

        val points = backWaveSurfacePoints(full, EdgeSide.LEFT, samples = 9)
        assertEquals(9, points.size)
        assertEquals(0f, points.first().x, 0.001f)
        assertEquals(0f, points.last().x, 0.001f)
        assertTrue(points[4].x > points[2].x)
        assertTrue(points[4].x > points[6].x)
        assertEquals(full.centerY, points[4].y, 0.001f)
    }

    @Test
    fun verticalPullWarpsParabolaWithoutMovingAnchor() {
        val upward = backWaveVisualLayout(
            density = 1f,
            progress = 1f,
            stretchProgress = 1f,
            longDistance = false,
            startY = 320f,
            touchY = 224f
        )
        val downward = backWaveVisualLayout(
            density = 1f,
            progress = 1f,
            stretchProgress = 1f,
            longDistance = false,
            startY = 320f,
            touchY = 416f
        )

        assertEquals(320f, upward.centerY, 0.001f)
        assertEquals(320f, downward.centerY, 0.001f)

        val upwardPoints = backWaveSurfacePoints(upward, EdgeSide.LEFT, samples = 9)
        val downwardPoints = backWaveSurfacePoints(downward, EdgeSide.LEFT, samples = 9)
        assertTrue(upwardPoints[2].x > upwardPoints[6].x)
        assertTrue(downwardPoints[6].x > downwardPoints[2].x)
    }

    @Test
    fun longDistanceMakesParabolaDeformationStrongerContinuously() {
        val short = backWaveVisualLayout(
            density = 1f,
            progress = 0.55f,
            stretchProgress = 0.55f,
            longDistance = false,
            startY = 320f,
            touchY = 408f
        )
        val long = backWaveVisualLayout(
            density = 1f,
            progress = 1f,
            stretchProgress = 1f,
            longDistance = true,
            startY = 320f,
            touchY = 408f
        )

        assertTrue(long.bendStrength > short.bendStrength)
        assertTrue(long.depthPx > short.depthPx)
        assertTrue(long.halfHeightPx >= short.halfHeightPx)
    }

    @Test
    fun directionCueIsSmallSubtleAndMirroredOnRightEdge() {
        val layout = backWaveVisualLayout(
            density = 1f,
            progress = 1f,
            stretchProgress = 1f,
            longDistance = true,
            startY = 320f,
            touchY = 248f
        )

        val left = backWaveDirectionCue(layout, EdgeSide.LEFT)
        val right = backWaveDirectionCue(layout, EdgeSide.RIGHT)
        val leftWidth = left.maxOf { it.x } - left.minOf { it.x }
        val leftHeight = left.maxOf { it.y } - left.minOf { it.y }

        assertTrue(leftWidth <= 14f)
        assertTrue(leftHeight <= 18f)
        assertTrue(left[0].x < left[1].x)
        assertTrue(right[0].x > right[1].x)
        assertTrue(left[1].y < layout.centerY)
    }
}
