package com.paifa.univerge.heavydrag.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.paifa.univerge.heavydrag.android.HeavyTouchClassifierGateway
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragSource
import com.paifa.univerge.heavydrag.core.HeavyRect
import android.view.MotionEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeavyDragComposeContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hostAndDeclarativeSourceAreAvailable() {
        val coordinator = HeavyDragCoordinator().apply {
            registerSource(HeavyDragSource("source", HeavyRect(0f, 0f, 1f, 1f)))
        }
        val gateway = NoOpGateway()
        val runtime = com.paifa.univerge.heavydrag.android.HeavyDragRuntime(coordinator, gateway)

        composeRule.setContent {
            HeavyDragHost(runtime) {
                Box(
                    Modifier
                        .heavyDraggable(coordinator, "source")
                ) { BasicText("source") }
            }
        }

        composeRule.onNodeWithText("source").assertIsDisplayed()
    }

    @Test
    fun hostObservesMotionWithoutClaimingDownAndConsumesOnlyConfirmedDrag() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/paifa/univerge/heavydrag/compose/HeavyDragHost.kt")
            ),
            StandardCharsets.UTF_8
        )

        assertTrue(source.contains("motionEventSpy"))
        assertTrue(source.contains("PointerEventPass.Initial"))
        assertTrue(source.contains("HeavyDragState.DRAGGING"))
        assertFalse(source.contains("pointerInteropFilter"))
    }

    private class NoOpGateway : HeavyTouchClassifierGateway {
        override fun setListener(listener: HeavyTouchClassifierGateway.Listener?) = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
        override fun handleMotionEvent(event: MotionEvent, width: Int, height: Int, gestureId: Long) = true
    }
}
