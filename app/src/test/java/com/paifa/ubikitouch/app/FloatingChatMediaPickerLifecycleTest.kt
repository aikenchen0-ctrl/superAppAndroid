package com.paifa.ubikitouch.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingChatMediaPickerLifecycleTest {
    @Test
    fun pickedMediaProcessingKeepsPickerAliveUntilDeliveryCompletes() {
        val calls = mutableListOf<String>()
        val lifecycle = FloatingChatMediaPickerLifecycle(
            notifyPickerClosed = { calls += "notifyClosed" },
            finishPicker = { calls += "finish" }
        )

        lifecycle.startPickedMediaProcessing()
        lifecycle.onDestroy(isChangingConfigurations = false)

        assertEquals(emptyList<String>(), calls)

        lifecycle.completePickedMediaProcessing {
            calls += "deliver"
        }

        assertEquals(listOf("notifyClosed", "deliver", "finish"), calls)
    }

    @Test
    fun cancelNotifiesClosedBeforeFinishingOnce() {
        val calls = mutableListOf<String>()
        val lifecycle = FloatingChatMediaPickerLifecycle(
            notifyPickerClosed = { calls += "notifyClosed" },
            finishPicker = { calls += "finish" }
        )

        lifecycle.cancelPicker()
        lifecycle.onDestroy(isChangingConfigurations = false)

        assertEquals(listOf("notifyClosed", "finish"), calls)
    }
}
