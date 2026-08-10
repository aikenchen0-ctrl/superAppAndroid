package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureDefaultActionTest {
    @Test
    fun mapsVerticalSwipesToFloatingChatActionsByDefault() {
        assertEquals(GestureAction.ExpandFloatingChat, GestureDefaultAction.forGesture(GestureType.SWIPE_UP))
        assertEquals(GestureAction.CollapseFloatingChat, GestureDefaultAction.forGesture(GestureType.SWIPE_DOWN))
    }

    @Test
    fun keepsExistingNavigationDefaults() {
        assertEquals(GestureAction.Back, GestureDefaultAction.forGesture(GestureType.PULL_INWARD))
        assertEquals(GestureAction.Back, GestureDefaultAction.forGesture(GestureType.PULL_INWARD_SHORT))
        assertEquals(GestureAction.Home, GestureDefaultAction.forGesture(GestureType.PULL_INWARD_LONG))
    }

    @Test
    fun tapDoubleTapAndLongPressHaveNoDefaultAction() {
        assertEquals(GestureAction.None, GestureDefaultAction.forGesture(GestureType.TAP))
        assertEquals(GestureAction.None, GestureDefaultAction.forGesture(GestureType.DOUBLE_TAP))
        assertEquals(GestureAction.None, GestureDefaultAction.forGesture(GestureType.LONG_PRESS))
    }
}
