package com.paifa.univerge.app

import com.paifa.univerge.app.buildAccessibilityEnableCommands
import com.paifa.univerge.app.mergeAccessibilityServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityKeepAliveTest {
    @Test
    fun mergeServiceNamePreservesExistingEntriesAndAddsCurrentOnce() {
        val merged = mergeAccessibilityServices(
            current = "other.pkg/OtherService:com.paifa.univerge/com.paifa.univerge.accessibility.UbikiAccessibilityService:",
            service = "com.paifa.univerge/com.paifa.univerge.accessibility.UbikiAccessibilityService"
        )

        assertEquals(
            "other.pkg/OtherService:com.paifa.univerge/com.paifa.univerge.accessibility.UbikiAccessibilityService:",
            merged
        )
    }

    @Test
    fun buildEnableCommandsUseActualApplicationAndServiceComponent() {
        val commands = buildAccessibilityEnableCommands(
            packageName = "com.paifa.univerge",
            serviceClassName = "com.paifa.univerge.accessibility.UbikiAccessibilityService",
            currentServices = "other.pkg/OtherService:"
        )

        assertEquals("settings put secure accessibility_enabled 1", commands.first())
        assertTrue(commands.last().contains("com.paifa.univerge/com.paifa.univerge.accessibility.UbikiAccessibilityService"))
        assertTrue(commands.last().endsWith(":"))
    }
}
