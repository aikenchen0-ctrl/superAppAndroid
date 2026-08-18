package com.paifa.univerge.accessibility

object FloatingChatMaterialLibraryBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatMaterialLibrary()
    }
}
