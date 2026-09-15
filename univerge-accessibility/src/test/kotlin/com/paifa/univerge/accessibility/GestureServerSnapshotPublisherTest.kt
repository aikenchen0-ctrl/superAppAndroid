package com.paifa.univerge.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import com.paifa.univerge.gesture.server.GestureServerSnapshot
import com.paifa.univerge.gesture.server.GestureServerZone
import com.paifa.univerge.gesture.server.IGestureServer
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GestureServerSnapshotPublisherTest {
    @Before
    fun clearPublisherState() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("gesture_server_publisher", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun rejectedAidlSnapshotRemainsPendingUntilAReconnectAcceptsIt() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)
        val server = RecordingServer(acceptSnapshots = false)
        val source = snapshot(1L)

        publisher.publish(source)
        context.connect(server)
        server.acceptSnapshots = true
        context.reconnect()

        assertEquals(2, server.received.size)
        assertEquals(source, server.received.last())
    }

    @Test
    fun serviceDisconnectClearsBindingSoTheNextPublishCanRebind() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)
        val firstServer = RecordingServer(acceptSnapshots = true)
        val secondServer = RecordingServer(acceptSnapshots = true)

        publisher.publish(snapshot(1L))
        context.connect(firstServer)
        context.disconnect()
        publisher.publish(snapshot(2L))
        context.connect(secondServer)

        assertEquals(2, context.bindCount)
        assertEquals(1, secondServer.received.size)
        assertEquals(2L, secondServer.received.single()?.version)
    }

    @Test
    fun synchronousServiceConnectionCallbackIsHandled() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val server = RecordingServer(acceptSnapshots = true)
        context.synchronousServer = server
        val publisher = GestureServerSnapshotPublisher(context)

        publisher.publish(snapshot(3L))

        assertEquals(listOf(3L), server.received.map { it?.version })
    }

    @Test
    fun anOlderPublishCannotOvertakeANewerPendingSnapshot() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val server = RecordingServer(acceptSnapshots = false)
        val publisher = GestureServerSnapshotPublisher(context)

        publisher.publish(snapshot(2L))
        context.connect(server)
        publisher.publish(snapshot(1L))

        assertEquals(2L, server.received.last()?.version)
    }

    @Test
    fun transportFailureInvalidatesTheDeadBindingSoTheNextPublishCanRetry() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)
        val deadServer = RecordingServer(acceptSnapshots = true, throwOnApply = true)

        publisher.publish(snapshot(1L))
        context.connect(deadServer)
        publisher.publish(snapshot(2L))

        assertEquals(2, context.bindCount)
    }

    @Test
    fun displayGeometryAndDensityChangesAdvanceThePublisherRevision() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)

        val portrait = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f
        )
        val landscape = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 2400,
            screenHeightPx = 1080,
            density = 3f
        )
        val sameLandscape = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 2400,
            screenHeightPx = 1080,
            density = 3f
        )
        val newDensity = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 2400,
            screenHeightPx = 1080,
            density = 2.75f
        )

        assertTrue(landscape > portrait)
        assertEquals(landscape, sameLandscape)
        assertTrue(newDensity > landscape)
    }

    @Test
    fun floatingChatSurfaceOwnershipChangesAdvanceThePublisherRevision() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)
        val common = mapOf(
            "configVersion" to 10L,
            "screenWidthPx" to 1080,
            "screenHeightPx" to 2400,
            "density" to 3f
        )

        val collapsed = publisher.revisionFor(
            configVersion = common.getValue("configVersion") as Long,
            screenWidthPx = common.getValue("screenWidthPx") as Int,
            screenHeightPx = common.getValue("screenHeightPx") as Int,
            density = common.getValue("density") as Float,
            floatingChatOwnsSurface = false
        )
        val expanded = publisher.revisionFor(
            configVersion = common.getValue("configVersion") as Long,
            screenWidthPx = common.getValue("screenWidthPx") as Int,
            screenHeightPx = common.getValue("screenHeightPx") as Int,
            density = common.getValue("density") as Float,
            floatingChatOwnsSurface = true
        )
        val sameExpanded = publisher.revisionFor(
            configVersion = common.getValue("configVersion") as Long,
            screenWidthPx = common.getValue("screenWidthPx") as Int,
            screenHeightPx = common.getValue("screenHeightPx") as Int,
            density = common.getValue("density") as Float,
            floatingChatOwnsSurface = true
        )
        val collapsedAgain = publisher.revisionFor(
            configVersion = common.getValue("configVersion") as Long,
            screenWidthPx = common.getValue("screenWidthPx") as Int,
            screenHeightPx = common.getValue("screenHeightPx") as Int,
            density = common.getValue("density") as Float,
            floatingChatOwnsSurface = false
        )

        assertTrue(expanded > collapsed)
        assertEquals(expanded, sameExpanded)
        assertTrue(collapsedAgain > expanded)
    }

    @Test
    fun reenablingRuntimeInputAdvancesThePublisherRevisionAgain() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)

        val enabled = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = true
        )
        val disabled = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = false
        )
        val reenabled = publisher.revisionFor(
            configVersion = 10L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = true
        )

        assertTrue(disabled > enabled)
        assertTrue(reenabled > disabled)
    }

    @Test
    fun runtimeInputGateDisableIsStableAfterTheFirstTransition() {
        val context = BindingContext(RuntimeEnvironment.getApplication())
        val publisher = GestureServerSnapshotPublisher(context)

        val enabled = publisher.revisionFor(
            configVersion = 12L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = true
        )
        val disabled = publisher.revisionFor(
            configVersion = 12L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = false
        )
        val sameDisabled = publisher.revisionFor(
            configVersion = 12L,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            inputEnabled = false
        )

        assertTrue(disabled > enabled)
        assertEquals(disabled, sameDisabled)
    }

    private fun snapshot(version: Long) = GestureServerSnapshot(
        version = version,
        density = 1f,
        screenWidthDp = 400f,
        screenHeightDp = 800f,
        leftZones = listOf(GestureServerZone(0, true, 0f, 800f, 24f))
    )

    private class BindingContext(base: Context) : ContextWrapper(base) {
        var bindCount: Int = 0
            private set
        private var connection: ServiceConnection? = null
        var synchronousServer: IGestureServer? = null

        override fun getApplicationContext(): Context = this

        override fun bindService(intent: Intent, serviceConnection: ServiceConnection, flags: Int): Boolean {
            bindCount += 1
            connection = serviceConnection
            synchronousServer?.let { server ->
                serviceConnection.onServiceConnected(
                    ComponentName(this, "com.paifa.univerge.gesture.server.GestureServerBinderService"),
                    server.asBinder()
                )
            }
            return true
        }

        override fun unbindService(serviceConnection: ServiceConnection) {
            if (connection === serviceConnection) connection = null
        }

        fun connect(server: IGestureServer) {
            val active = connection ?: error("no pending connection")
            currentServer = server
            active.onServiceConnected(
                ComponentName(this, "com.paifa.univerge.gesture.server.GestureServerBinderService"),
                server.asBinder()
            )
        }

        fun reconnect() = connect(currentServer ?: error("no server"))

        fun disconnect() {
            connection?.onServiceDisconnected(
                ComponentName(this, "com.paifa.univerge.gesture.server.GestureServerBinderService")
            )
        }

        private var currentServer: IGestureServer? = null
    }

    private class RecordingServer(
        var acceptSnapshots: Boolean,
        private val throwOnApply: Boolean = false
    ) : IGestureServer.Stub() {
        val received = CopyOnWriteArrayList<GestureServerSnapshot?>()

        override fun applySnapshotRemote(snapshot: Bundle?): Boolean {
            if (throwOnApply) throw IllegalStateException("simulated dead binder")
            received += GestureServerSnapshot.fromBundle(snapshot)
            return acceptSnapshots
        }

        override fun currentSnapshotRemote(): Bundle? = null
        override fun currentVersionRemote(): Long = received.lastOrNull()?.version ?: 0L
        override fun binderDisconnectedRemote(): Bundle? = null
    }
}
