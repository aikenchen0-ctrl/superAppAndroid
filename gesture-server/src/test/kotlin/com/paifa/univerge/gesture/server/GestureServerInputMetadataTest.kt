package com.paifa.univerge.gesture.server

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerInputMetadataTest {
    @Test
    fun overlayServerDoesNotRegisterASecondRawMotionEventOwner() {
        val xml = File(
            "src/main/res/xml/gesture_server_accessibility_service.xml"
        ).let { file ->
            if (file.isFile) file else File(
                "gesture-server/src/main/res/xml/gesture_server_accessibility_service.xml"
            )
        }.readText()

        assertTrue(xml.contains("android:canPerformGestures=\"true\""))
        assertFalse(xml.contains("flagSendMotionEvents"))
    }
}
