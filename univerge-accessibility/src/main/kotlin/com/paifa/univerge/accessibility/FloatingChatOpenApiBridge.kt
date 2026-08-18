package com.paifa.univerge.accessibility

object FloatingChatOpenApiBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatOpenApiWorkbench()
    }
}
