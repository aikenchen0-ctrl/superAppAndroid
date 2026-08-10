package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarPressResult
import com.paifa.ubikitouch.accessibility.floatingchat.components.classifyAvatarPress
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarPressClassifierTest {
    @Test
    fun shortPhysicalPressRemainsAClickEvenWhenDispatchWasDelayed() {
        val result = classifyAvatarPress(
            durationMillis = 60,
            travelDistancePx = 1f,
            touchSlopPx = 24f,
            longPressTimeoutMillis = 500,
            cancelled = false
        )

        assertEquals(AvatarPressResult.Click, result)
    }

    @Test
    fun stationaryPressAtTheLongPressThresholdOpensTheEditor() {
        val result = classifyAvatarPress(
            durationMillis = 500,
            travelDistancePx = 0f,
            touchSlopPx = 24f,
            longPressTimeoutMillis = 500,
            cancelled = false
        )

        assertEquals(AvatarPressResult.LongClick, result)
    }

    @Test
    fun scrollingOrCancellationDoesNotClickAnAvatar() {
        assertEquals(
            AvatarPressResult.None,
            classifyAvatarPress(80, 25f, 24f, 500, cancelled = false)
        )
        assertEquals(
            AvatarPressResult.None,
            classifyAvatarPress(80, 0f, 24f, 500, cancelled = true)
        )
    }
}
