package com.paifa.univerge.core.sdk

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeGestureSdkContractTest {
    @Test
    fun actionHandlerIsIndependentFromChatOrAndroidTypes() {
        val received = mutableListOf<String>()
        val handler = EdgeGestureActionHandler { action, data ->
            received += "${action.id}:${data.gestureId}"
            true
        }

        assertTrue(handler.execute(GestureAction.Back, GestureData(1f, 2f, 3f, 4f, gestureId = 1L)))
        assertEquals(listOf("back:1"), received)
    }

    @Test
    fun sdkConfigKeepsNativeInputIndependentFromHostSurfaceVisibility() {
        assertTrue(EdgeGestureSdkConfig().keepNativeInputWhileHostSurfaceIsVisible)
    }
}
