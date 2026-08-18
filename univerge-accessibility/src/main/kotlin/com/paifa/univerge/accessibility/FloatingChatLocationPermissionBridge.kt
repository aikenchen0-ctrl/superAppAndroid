package com.paifa.univerge.accessibility

object FloatingChatLocationPermissionBridge {
    fun requestLocationPermission() {
        UniVergeAccessibilityService.instance?.requestFloatingChatLocationPermission()
    }

    fun deliverLocationPermission(granted: Boolean) {
        UniVergeAccessibilityService.instance?.onFloatingChatLocationPermissionResult(granted)
    }
}
