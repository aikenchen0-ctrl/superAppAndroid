package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGestureCoreBridgeTest {
    @Test
    fun sideEventsUseCoreRecognizerAndCaptureSnapshotUntilUp() {
        val initial = nativeCoreConfig(GestureAction.Back, version = 1L)
        val next = nativeCoreConfig(GestureAction.Home, version = 2L)
        val bridge = NativeGestureCoreBridge(initial)

        assertTrue(bridge.onDown(8f, 160f, 0L) is NativeCoreSignal.Side)
        val preview = bridge.onMove(64f, 160f, 20L)
        assertEquals(GestureAction.Back, (preview as NativeCoreSignal.Side).signalPreview().action)

        bridge.updateConfig(next)
        val commit = bridge.onUp(64f, 160f, 40L)
        assertEquals(GestureAction.Back, (commit as NativeCoreSignal.Side).signalCommit().action)

        assertTrue(bridge.onDown(8f, 160f, 100L) is NativeCoreSignal.Side)
        val nextCommit = bridge.onUp(64f, 160f, 140L)
        assertEquals(GestureAction.Home, (nextCommit as NativeCoreSignal.Side).signalCommit().action)
    }

    @Test
    fun subThresholdMoveDoesNotEndTheActiveNativeTransaction() {
        val bridge = NativeGestureCoreBridge(nativeCoreConfig(GestureAction.Back, version = 1L))

        bridge.onDown(8f, 160f, 0L)
        assertEquals(
            com.paifa.univerge.core.gesture.runtime.GestureSignal.Ignored,
            (bridge.onMove(18f, 160f, 10L) as NativeCoreSignal.Side).signal
        )
        assertTrue(bridge.onMove(64f, 160f, 20L) is NativeCoreSignal.Side)
        assertTrue(bridge.onUp(64f, 160f, 30L) is NativeCoreSignal.Side)
    }

    @Test
    fun validUpCommitsEvenWhenNoMoveEventWasDelivered() {
        val bridge = NativeGestureCoreBridge(nativeCoreConfig(GestureAction.Back, version = 1L))

        bridge.onDown(8f, 160f, 0L)
        val commit = bridge.onUp(64f, 160f, 30L)
        assertEquals(
            com.paifa.univerge.core.model.GestureAction.Back,
            (commit as NativeCoreSignal.Side).signalCommit().action
        )
    }

    private fun nativeCoreConfig(action: GestureAction, version: Long): NativeEdgeGestureConfig =
        NativeEdgeGestureConfig(
            screenWidthPx = 400,
            screenHeightPx = 800,
            density = 1f,
            leftConfigs = listOf(
                EdgeZoneConfig(
                    side = EdgeSide.LEFT,
                    zoneId = 0,
                    topInsetPercent = 0,
                    bottomInsetPercent = 0,
                    thicknessDp = 24,
                    edgeInsetDp = 0
                )
            ),
            rightConfigs = emptyList(),
            shortThresholdPx = 24f,
            longThresholdPx = 96f,
            snapshotVersion = version,
            sideActions = mapOf(
                EdgeSide.LEFT to mapOf(GestureType.PULL_INWARD_SHORT to action)
            )
        )
}

private fun NativeCoreSignal.signalPreview() =
    (this as NativeCoreSignal.Side).signal as com.paifa.univerge.core.gesture.runtime.GestureSignal.Preview

private fun NativeCoreSignal.signalCommit() =
    (this as NativeCoreSignal.Side).signal as com.paifa.univerge.core.gesture.runtime.GestureSignal.Commit
