package com.paifa.ubikitouch.accessibility.floatingchat.shell

import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatEffect
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MediaPlatformPort

internal class AndroidFloatingChatRuntimePorts(
    private val mediaPort: MediaPlatformPort
) {
    fun handle(effect: FloatingChatEffect): Boolean {
        return when (effect) {
            is FloatingChatEffect.OpenDocument -> mediaPort.openDocument(effect.messageId)
            is FloatingChatEffect.OpenMediaPicker -> {
                mediaPort.openPicker(effect.kind)
                true
            }
            FloatingChatEffect.OpenCamera -> {
                mediaPort.openCamera()
                true
            }
            FloatingChatEffect.CloseMediaPreview -> {
                mediaPort.closePreview()
                true
            }
            is FloatingChatEffect.RequestPermission,
            is FloatingChatEffect.ShowMessage -> false
        }
    }
}
