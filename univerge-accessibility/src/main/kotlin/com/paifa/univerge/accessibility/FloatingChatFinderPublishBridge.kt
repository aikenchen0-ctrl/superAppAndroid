package com.paifa.univerge.accessibility

object FloatingChatFinderPublishBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatFinderPublish()
    }
}
