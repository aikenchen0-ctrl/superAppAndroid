package com.paifa.univerge.accessibility

object FloatingChatVoicePermissionBridge {
    fun requestRecordAudioPermission() {
        UniVergeAccessibilityService.instance?.requestFloatingChatVoicePermission()
    }

    fun deliverRecordAudioPermission(granted: Boolean) {
        UniVergeAccessibilityService.instance?.onFloatingChatVoicePermissionResult(granted)
    }
}
