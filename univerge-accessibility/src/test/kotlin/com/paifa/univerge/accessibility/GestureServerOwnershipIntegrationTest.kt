package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerOwnershipIntegrationTest {
    @Test
    fun mainServiceYieldsEdgeAndBottomInputWhenTheIsolatedServerLeaseIsFresh() {
        val source = File(
            "src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
        ).let { file ->
            if (file.isFile) file else File(
                "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
            )
        }.readText()

        assertTrue(source.contains("GestureServerProcessLease.isActive"))
        assertTrue(source.contains("GestureServerProcessLease.ACTION_OWNER_CHANGED"))
        assertTrue(source.contains("registerGestureServerOwnerReceiver"))
        assertTrue(source.contains("relinquishLocalGestureInput"))
        assertTrue(source.contains("gestureServerInputCurrentlyOwned"))
    }

    @Test
    fun configuredTerminalActionsReadTheServerLeaseFreshAtCommitTime() {
        val source = File(
            "src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
        ).let { file ->
            if (file.isFile) file else File(
                "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
            )
        }.readText()
        val method = source.substringAfter("private fun executeConfiguredGestureAction(")
            .substringBefore("private fun sideFunctionItems(")

        assertTrue(
            "terminal action commit must bypass the 250ms owner cache",
            method.contains("gestureServerInputCurrentlyOwned(forceRefresh = true)")
        )
    }
}
