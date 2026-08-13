package com.paifa.ubikitouch.accessibility

object FloatingChatMaterialLibraryBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatMaterialLibrary()
    }
}
