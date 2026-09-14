package com.paifa.univerge.accessibility

import android.content.Context
import android.view.MotionEvent
import com.paifa.univerge.heavydrag.android.HeavyDragRuntime
import com.paifa.univerge.heavydrag.android.HeavyTouchClassifierGateway
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeavyDragRuntimeProviderTest {
    private val registrations = mutableListOf<HeavyDragRuntimeProvider.Registration>()

    @After
    fun tearDown() {
        registrations.asReversed().forEach { it.close() }
        registrations.clear()
    }

    @Test
    fun acquiredLeaseClosesRuntimeExactlyOnce() {
        val gateway = RecordingGateway()
        val runtime = HeavyDragRuntime(HeavyDragCoordinator(), gateway)
        registrations += HeavyDragRuntimeProvider.install(
            HeavyDragRuntimeProvider.Factory { runtime }
        )

        val lease = HeavyDragRuntimeProvider.acquire(testContext())

        assertSame(runtime, lease?.runtime)
        lease?.close()
        lease?.close()
        assertEquals(1, gateway.closeCount)
    }

    @Test
    fun closingAnOlderRegistrationDoesNotRemoveAReplacementFactory() {
        val firstRuntime = HeavyDragRuntime(HeavyDragCoordinator(), RecordingGateway())
        val secondRuntime = HeavyDragRuntime(HeavyDragCoordinator(), RecordingGateway())
        val first = HeavyDragRuntimeProvider.install(
            HeavyDragRuntimeProvider.Factory { firstRuntime }
        )
        registrations += first
        registrations += HeavyDragRuntimeProvider.install(
            HeavyDragRuntimeProvider.Factory { secondRuntime }
        )

        first.close()

        val lease = HeavyDragRuntimeProvider.acquire(testContext())
        assertSame(secondRuntime, lease?.runtime)
        lease?.close()
    }

    @Test
    fun noInstalledFactoryLeavesRuntimeOptional() {
        assertNull(HeavyDragRuntimeProvider.acquire(testContext()))
    }

    @Test
    fun failedFactoryLeavesRuntimeOptionalInsteadOfBreakingTheHost() {
        registrations += HeavyDragRuntimeProvider.install(
            HeavyDragRuntimeProvider.Factory { error("classifier unavailable") }
        )

        assertNull(HeavyDragRuntimeProvider.acquire(testContext()))
    }

    private fun testContext(): Context = org.robolectric.RuntimeEnvironment.getApplication()

    private class RecordingGateway : HeavyTouchClassifierGateway {
        var closeCount: Int = 0

        override fun setListener(listener: HeavyTouchClassifierGateway.Listener?) = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() {
            closeCount += 1
        }

        override fun handleMotionEvent(
            event: MotionEvent,
            width: Int,
            height: Int,
            gestureId: Long
        ): Boolean = true
    }
}
