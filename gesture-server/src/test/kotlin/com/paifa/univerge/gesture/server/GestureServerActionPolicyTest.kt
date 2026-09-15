package com.paifa.univerge.gesture.server

import com.paifa.univerge.core.model.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureServerActionPolicyTest {
    @Test
    fun requestedSystemActionsHaveAnInProcessExecutionKind() {
        assertEquals(GestureServerActionKind.GlobalBack, gestureServerActionKind(GestureAction.Back))
        assertEquals(GestureServerActionKind.GlobalHome, gestureServerActionKind(GestureAction.Home))
        assertEquals(GestureServerActionKind.GlobalRecents, gestureServerActionKind(GestureAction.Recents))
        assertEquals(GestureServerActionKind.GlobalNotifications, gestureServerActionKind(GestureAction.Notifications))
        assertEquals(GestureServerActionKind.GlobalQuickSettings, gestureServerActionKind(GestureAction.QuickSettings))
        assertEquals(GestureServerActionKind.GlobalScreenshot, gestureServerActionKind(GestureAction.Screenshot))
        assertEquals(GestureServerActionKind.GlobalLockScreen, gestureServerActionKind(GestureAction.LockScreen))
        assertEquals(GestureServerActionKind.VolumeUp, gestureServerActionKind(GestureAction.VolumeUp))
        assertEquals(GestureServerActionKind.VolumeDown, gestureServerActionKind(GestureAction.VolumeDown))
        assertEquals(GestureServerActionKind.LaunchApp, gestureServerActionKind(GestureAction.LaunchApp("com.example.app")))
        assertEquals(GestureServerActionKind.None, gestureServerActionKind(GestureAction.None))
    }
}
