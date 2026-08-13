package com.paifa.ubikitouch.accessibility

object FloatingChatFinderPublishBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatFinderPublish()
    }
}
