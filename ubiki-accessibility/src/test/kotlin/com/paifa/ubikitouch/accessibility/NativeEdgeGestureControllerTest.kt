package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEdgeGestureControllerTest {
    @Test
    fun nativeRectsUseTheInsetOfEachZoneOnTheSameSide() {
        val first = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 0,
            edgeInsetDp = 12,
            topInsetPercent = 0,
            bottomInsetPercent = 50
        )
        val second = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 1,
            edgeInsetDp = 48,
            topInsetPercent = 50,
            bottomInsetPercent = 0
        )

        val rects = nativeTouchInterceptRects(
            NativeEdgeGestureConfig(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                density = 1f,
                leftConfigs = listOf(first, second),
                rightConfigs = emptyList(),
                shortThresholdPx = 24f,
                longThresholdPx = 72f
            ),
            floatingChatExpanded = false
        )

        assertEquals(12, rects.first { it.zoneId == 0 }.left)
        assertEquals(48, rects.first { it.zoneId == 1 }.left)
    }

    @Test
    fun nativeRightRectUsesThatZoneInsetFromTheRightEdge() {
        val config = EdgeZoneConfig(
            side = EdgeSide.RIGHT,
            zoneId = 2,
            edgeInsetDp = 48,
            topInsetPercent = 0,
            bottomInsetPercent = 0
        )

        val rect = nativeTouchInterceptRects(
            NativeEdgeGestureConfig(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                density = 1f,
                leftConfigs = emptyList(),
                rightConfigs = listOf(config),
                shortThresholdPx = 24f,
                longThresholdPx = 72f
            ),
            floatingChatExpanded = false
        ).single()

        assertEquals(1008, rect.left)
        assertEquals(1032, rect.right)
    }

    @Test
    fun fixedFullScreenSurfaceWidthDoesNotReuseWiderNativeConfiguration() {
        val config = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 3,
            thicknessDp = 72,
            topInsetPercent = 0,
            bottomInsetPercent = 0
        )

        val rect = edgeGestureInterceptRects(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 1f,
            configs = listOf(config),
            fixedTouchTargetDp = 24
        ).single()

        assertEquals(0, rect.left)
        assertEquals(24, rect.right)
    }

    @Test
    fun nativeHitAnchorsTheBackWaveAtThePhysicalEdge() {
        val leftIntercept = NativeTouchInterceptRect(
            side = EdgeSide.LEFT,
            zoneId = 0,
            left = 48,
            top = 360,
            right = 120,
            bottom = 1560
        )
        val rightIntercept = NativeTouchInterceptRect(
            side = EdgeSide.RIGHT,
            zoneId = 1,
            left = 960,
            top = 840,
            right = 1032,
            bottom = 2040
        )

        val leftAnchor = backWaveAnchorForNativeIntercept(leftIntercept, screenWidthPx = 1080)
        val rightAnchor = backWaveAnchorForNativeIntercept(rightIntercept, screenWidthPx = 1080)

        assertEquals(0, leftAnchor.edgeX)
        assertEquals(360, leftAnchor.y)
        assertEquals(1200, leftAnchor.height)
        assertEquals(1080, rightAnchor.edgeX)
        assertEquals(840, rightAnchor.y)
        assertEquals(1200, rightAnchor.height)
    }
}
