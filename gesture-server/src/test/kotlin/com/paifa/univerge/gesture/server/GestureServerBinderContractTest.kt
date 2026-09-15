package com.paifa.univerge.gesture.server

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GestureServerBinderContractTest {
    @Test
    fun binderExposesStableIpcInterfaceForCrossProcessClients() {
        assertTrue(
            "GestureServerBinder must implement the generated cross-process contract",
            IGestureServer.Stub::class.java.isAssignableFrom(GestureServerBinder::class.java)
        )
    }

    @Test
    fun remoteProxyPreservesSnapshotRoundTripAcrossBinderBoundary() {
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val server = GestureServerBinder(runtime)
        val proxy = IGestureServer.Stub.asInterface(RemoteBinder(server.asBinder()))
        val source = GestureServerSnapshot(
            version = 7L,
            density = 3f,
            screenWidthDp = 360f,
            screenHeightDp = 800f
        )

        assertTrue(proxy.applySnapshotRemote(source.toBundle()))
        assertTrue(proxy.currentVersionRemote() == source.version)
        assertTrue(
            GestureServerSnapshot.fromBundle(proxy.currentSnapshotRemote()) == source
        )
    }

    private class RemoteBinder(private val target: IBinder) : Binder() {
        override fun queryLocalInterface(descriptor: String): IInterface? = null

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean = target.transact(code, data, reply, flags)
    }
}
