package com.paifa.univerge.accessibility

object FloatingChatOpenApiBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatOpenApiWorkbench()
    }
}
