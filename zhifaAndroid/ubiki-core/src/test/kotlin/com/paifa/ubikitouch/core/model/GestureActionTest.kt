package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionTest {
    @Test
    fun parsesSystemActionIds() {
        assertEquals(GestureAction.Back, GestureAction.fromId("back"))
        assertEquals(GestureAction.QuickSettings, GestureAction.fromId("quick_settings"))
        assertEquals(GestureAction.VolumeUp, GestureAction.fromId("volume_up"))
        assertEquals(GestureAction.VolumeDown, GestureAction.fromId("volume_down"))
        assertEquals(GestureAction.ExpandFloatingChat, GestureAction.fromId("expand_floating_chat"))
        assertEquals(GestureAction.CollapseFloatingChat, GestureAction.fromId("collapse_floating_chat"))
    }

    @Test
    fun parsesLaunchAppIds() {
        val action = GestureAction.fromId("launch_app:com.android.settings")
        assertTrue(action is GestureAction.LaunchApp)
        assertEquals("com.android.settings", (action as GestureAction.LaunchApp).packageName)
    }

    @Test
    fun returnsNoneForUnknownIds() {
        assertEquals(GestureAction.None, GestureAction.fromId("unknown"))
    }

    @Test
    fun volumeActionsAreAvailableAsSystemActions() {
        assertTrue(GestureActionCatalog.systemActions.contains(GestureAction.VolumeUp))
        assertTrue(GestureActionCatalog.systemActions.contains(GestureAction.VolumeDown))
        assertTrue(GestureActionCatalog.systemActions.contains(GestureAction.ExpandFloatingChat))
        assertTrue(GestureActionCatalog.systemActions.contains(GestureAction.CollapseFloatingChat))
    }
}
