package com.paifa.univerge.gesture.server

import android.os.Bundle

/** Narrow Binder surface: callers can only submit/read versioned snapshots. */
class GestureServerBinder(
    private val runtime: GestureServerRuntime
) : IGestureServer.Stub() {
    fun applySnapshot(bundle: Bundle?): GestureServerApplyResult {
        val snapshot = GestureServerSnapshot.fromBundle(bundle)
            ?: return GestureServerApplyResult(false, "malformed_snapshot", runtime.snapshot)
        return runtime.apply(snapshot)
    }

    fun currentSnapshot(): Bundle? = runtime.snapshot?.toBundle()

    fun currentVersion(): Long = runtime.snapshot?.version ?: 0L

    fun binderDisconnected(): Bundle? = runtime.onBinderDisconnected()?.toBundle()

    override fun applySnapshotRemote(snapshot: Bundle?): Boolean =
        applySnapshot(snapshot).accepted

    override fun currentSnapshotRemote(): Bundle? = currentSnapshot()

    override fun currentVersionRemote(): Long = currentVersion()

    override fun binderDisconnectedRemote(): Bundle? = binderDisconnected()
}
