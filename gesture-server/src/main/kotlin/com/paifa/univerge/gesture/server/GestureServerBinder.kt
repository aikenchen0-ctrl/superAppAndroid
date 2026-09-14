package com.paifa.univerge.gesture.server

import android.os.Binder
import android.os.Bundle

/** Narrow Binder surface: callers can only submit/read versioned snapshots. */
class GestureServerBinder(
    private val runtime: GestureServerRuntime
) : Binder() {
    fun applySnapshot(bundle: Bundle?): GestureServerApplyResult {
        val snapshot = GestureServerSnapshot.fromBundle(bundle)
            ?: return GestureServerApplyResult(false, "malformed_snapshot", runtime.snapshot)
        return runtime.apply(snapshot)
    }

    fun currentSnapshot(): Bundle? = runtime.snapshot?.toBundle()

    fun currentVersion(): Long = runtime.snapshot?.version ?: 0L

    fun binderDisconnected(): Bundle? = runtime.onBinderDisconnected()?.toBundle()
}
