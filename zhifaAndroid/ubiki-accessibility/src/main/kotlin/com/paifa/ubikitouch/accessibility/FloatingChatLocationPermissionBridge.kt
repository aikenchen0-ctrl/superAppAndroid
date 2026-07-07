package com.paifa.ubikitouch.accessibility

object FloatingChatLocationPermissionBridge {
    fun requestLocationPermission() {
        UbikiAccessibilityService.instance?.requestFloatingChatLocationPermission()
    }

    fun deliverLocationPermission(granted: Boolean) {
        UbikiAccessibilityService.instance?.onFloatingChatLocationPermissionResult(granted)
    }
}
