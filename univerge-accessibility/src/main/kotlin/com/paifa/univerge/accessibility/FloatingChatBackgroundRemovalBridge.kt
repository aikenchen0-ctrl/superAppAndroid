package com.paifa.univerge.accessibility

object FloatingChatBackgroundRemovalBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatBackgroundRemoval()
    }

    fun notifyClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatBackgroundRemovalClosed()
    }
}
