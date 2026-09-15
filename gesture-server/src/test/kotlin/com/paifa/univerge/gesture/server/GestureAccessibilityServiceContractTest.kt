package com.paifa.univerge.gesture.server

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureAccessibilityServiceContractTest {
    @Test
    fun accessibilityServiceOwnsTheServerOverlayLifecycleAndLease() {
        val source = File(
            "src/main/kotlin/com/paifa/univerge/gesture/server/GestureAccessibilityService.kt"
        ).let { file ->
            if (file.isFile) file else File(
                "gesture-server/src/main/kotlin/com/paifa/univerge/gesture/server/GestureAccessibilityService.kt"
            )
        }.readText()

        assertTrue(source.contains("GestureServerOverlayController"))
        assertTrue(source.contains("GestureServerProcessLease.acquire"))
        assertTrue(source.contains("GestureServerProcessLease.release"))
        assertTrue(source.contains("onInputSurfaceChanged"))
        assertTrue(source.contains("serviceConnected && ::overlayController.isInitialized && overlayController.hasInputSurface"))
        assertTrue(source.contains("cancelActiveGesture"))
        assertFalse(source.contains("UniVergeAccessibilityService"))
    }

    @Test
    fun overlayInputDoesNotStartBeforeAccessibilityConnectionIsReady() {
        val source = File(
            "src/main/kotlin/com/paifa/univerge/gesture/server/GestureAccessibilityService.kt"
        ).let { file ->
            if (file.isFile) file else File(
                "gesture-server/src/main/kotlin/com/paifa/univerge/gesture/server/GestureAccessibilityService.kt"
            )
        }.readText()
        val onCreate = source.substringAfter("override fun onCreate()")
            .substringBefore("override fun onServiceConnected()")
        val onConnected = source.substringAfter("override fun onServiceConnected()")
            .substringBefore("override fun onAccessibilityEvent")

        assertFalse(onCreate.contains("overlayController.start(runtime)"))
        assertTrue(onConnected.contains("overlayController.start(runtime)"))
    }
}
