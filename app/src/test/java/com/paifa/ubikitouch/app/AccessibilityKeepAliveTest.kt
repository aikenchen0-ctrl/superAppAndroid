package com.paifa.ubikitouch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityKeepAliveTest {
    @Test
    fun mergeServiceNamePreservesExistingEntriesAndAddsCurrentOnce() {
        val merged = mergeAccessibilityServices(
            current = "other.pkg/OtherService:com.paifa.ubikitouch/com.paifa.ubikitouch.accessibility.UbikiAccessibilityService:",
            service = "com.paifa.ubikitouch/com.paifa.ubikitouch.accessibility.UbikiAccessibilityService"
        )

        assertEquals(
            "other.pkg/OtherService:com.paifa.ubikitouch/com.paifa.ubikitouch.accessibility.UbikiAccessibilityService:",
            merged
        )
    }

    @Test
    fun buildEnableCommandsUseActualApplicationAndServiceComponent() {
        val commands = buildAccessibilityEnableCommands(
            packageName = "com.paifa.ubikitouch",
            serviceClassName = "com.paifa.ubikitouch.accessibility.UbikiAccessibilityService",
            currentServices = "other.pkg/OtherService:"
        )

        assertEquals("settings put secure accessibility_enabled 1", commands.first())
        assertTrue(commands.last().contains("com.paifa.ubikitouch/com.paifa.ubikitouch.accessibility.UbikiAccessibilityService"))
        assertTrue(commands.last().endsWith(":"))
    }
}
