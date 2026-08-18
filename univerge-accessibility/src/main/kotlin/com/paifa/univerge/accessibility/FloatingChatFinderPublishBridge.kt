package com.paifa.univerge.accessibility

object FloatingChatFinderPublishBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatFinderPublish()
    }
}
