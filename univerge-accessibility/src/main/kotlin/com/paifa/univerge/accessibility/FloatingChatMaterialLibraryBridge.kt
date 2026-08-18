package com.paifa.univerge.accessibility

object FloatingChatMaterialLibraryBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatMaterialLibrary()
    }
}
