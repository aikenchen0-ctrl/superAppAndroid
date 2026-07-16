package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatEffect
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MediaPickerKind
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MediaPlatformPort
import com.paifa.ubikitouch.accessibility.floatingchat.shell.AndroidFloatingChatRuntimePorts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFloatingChatRuntimePortsTest {
    @Test
    fun mediaEffectsDispatchToPlatformPort() {
        val port = RecordingMediaPlatformPort()
        val runtime = AndroidFloatingChatRuntimePorts(port)

        assertTrue(runtime.handle(FloatingChatEffect.OpenDocument("message-9")))
        assertTrue(runtime.handle(FloatingChatEffect.OpenMediaPicker(MediaPickerKind.Image)))
        assertTrue(runtime.handle(FloatingChatEffect.OpenCamera))
        assertTrue(runtime.handle(FloatingChatEffect.CloseMediaPreview))

        assertEquals(
            listOf("document:message-9", "picker:Image", "camera", "closePreview"),
            port.calls
        )
    }

    @Test
    fun nonMediaEffectIsNotHandled() {
        val runtime = AndroidFloatingChatRuntimePorts(RecordingMediaPlatformPort())

        assertFalse(runtime.handle(FloatingChatEffect.ShowMessage("status")))
    }

    private class RecordingMediaPlatformPort : MediaPlatformPort {
        val calls = mutableListOf<String>()

        override fun openDocument(messageId: String): Boolean {
            calls += "document:$messageId"
            return true
        }

        override fun openPicker(kind: MediaPickerKind) {
            calls += "picker:${kind.name}"
        }

        override fun openCamera() {
            calls += "camera"
        }

        override fun closePreview() {
            calls += "closePreview"
        }
    }
}
