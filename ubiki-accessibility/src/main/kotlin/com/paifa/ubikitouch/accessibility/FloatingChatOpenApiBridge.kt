package com.paifa.ubikitouch.accessibility

object FloatingChatOpenApiBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatOpenApiWorkbench()
    }
}
