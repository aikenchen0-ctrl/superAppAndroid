package com.paifa.univerge.accessibility

object FloatingChatBackgroundRemovalBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatBackgroundRemoval()
    }

    fun notifyClosed() {
        UniVergeAccessibilityService.instance?.onFloatingChatBackgroundRemovalClosed()
    }
}
