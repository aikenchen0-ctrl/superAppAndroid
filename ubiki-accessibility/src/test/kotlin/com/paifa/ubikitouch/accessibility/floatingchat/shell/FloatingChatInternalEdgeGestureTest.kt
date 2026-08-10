package com.paifa.ubikitouch.accessibility.floatingchat.shell

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingChatInternalEdgeGestureTest {
    @Test
    fun expandedChatHitTestUsesInsetVerticalRangeEnabledAndFirstMatchingZone() {
        val first = EdgeZoneConfig(
            side = EdgeSide.RIGHT,
            zoneId = 0,
            edgeInsetDp = 10,
            topInsetPercent = 0,
            bottomInsetPercent = 50
        )
        val second = EdgeZoneConfig(
            side = EdgeSide.RIGHT,
            zoneId = 1,
            edgeInsetDp = 48,
            topInsetPercent = 50,
            bottomInsetPercent = 0
        )
        val disabled = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 2,
            enabled = false,
            edgeInsetDp = 0,
            topInsetPercent = 0,
            bottomInsetPercent = 0
        )

        assertEquals(
            0,
            floatingChatEdgeZoneForPosition(
                x = 1050f,
                y = 200f,
                width = 1080f,
                height = 2400f,
                touchTargetPx = 24f,
                density = 1f,
                leftConfigs = listOf(disabled),
                rightConfigs = listOf(first, second)
            )?.zoneId
        )
        assertEquals(
            1,
            floatingChatEdgeZoneForPosition(
                x = 1010f,
                y = 2000f,
                width = 1080f,
                height = 2400f,
                touchTargetPx = 24f,
                density = 1f,
                leftConfigs = listOf(disabled),
                rightConfigs = listOf(first, second)
            )?.zoneId
        )
        assertNull(
            floatingChatEdgeZoneForPosition(
                x = 1050f,
                y = 1300f,
                width = 1080f,
                height = 2400f,
                touchTargetPx = 24f,
                density = 1f,
                leftConfigs = listOf(disabled),
                rightConfigs = listOf(first, second)
            )
        )
        assertNull(
            floatingChatEdgeZoneForPosition(
                x = 10f,
                y = 200f,
                width = 1080f,
                height = 2400f,
                touchTargetPx = 24f,
                density = 1f,
                leftConfigs = listOf(disabled),
                rightConfigs = listOf(first, second)
            )
        )
    }
}
