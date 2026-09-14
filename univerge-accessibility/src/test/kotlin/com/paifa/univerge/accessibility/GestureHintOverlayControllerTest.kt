package com.paifa.univerge.accessibility

import android.view.WindowManager
import java.lang.reflect.Proxy
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class GestureHintOverlayControllerTest {
    @Test
    fun hintWindowNeverReceivesTouchOrFocus() {
        val flags = gestureHintWindowFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun prewarmCreatesAndHidesTheReusableHintWindow() {
        val context = RuntimeEnvironment.getApplication()
        val windowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }
        } as WindowManager
        val controller = GestureHintOverlayController(context, windowManager)

        controller.prewarm()
        controller.hide()
        controller.dispose()
    }
}
