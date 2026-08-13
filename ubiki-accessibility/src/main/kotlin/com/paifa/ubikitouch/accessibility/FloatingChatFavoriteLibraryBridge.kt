package com.paifa.ubikitouch.accessibility

object FloatingChatFavoriteLibraryBridge {
    fun open() { UbikiAccessibilityService.instance?.requestFloatingChatFavoriteLibrary() }
}
