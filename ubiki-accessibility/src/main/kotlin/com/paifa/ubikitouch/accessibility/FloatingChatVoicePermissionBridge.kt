package com.paifa.ubikitouch.accessibility

object FloatingChatVoicePermissionBridge {
    fun requestRecordAudioPermission() {
        UbikiAccessibilityService.instance?.requestFloatingChatVoicePermission()
    }

    fun deliverRecordAudioPermission(granted: Boolean) {
        UbikiAccessibilityService.instance?.onFloatingChatVoicePermissionResult(granted)
    }
}
