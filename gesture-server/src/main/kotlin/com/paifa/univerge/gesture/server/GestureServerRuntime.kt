package com.paifa.univerge.gesture.server

import java.util.concurrent.CopyOnWriteArrayList

/** Persistence boundary kept independent from SharedPreferences for deterministic tests. */
interface GestureServerSnapshotStore {
    fun read(): GestureServerSnapshot?
    fun write(snapshot: GestureServerSnapshot)
}

data class GestureServerApplyResult(
    val accepted: Boolean,
    val reason: String,
    val snapshot: GestureServerSnapshot?
)

/** Owns the last valid snapshot and survives Binder disconnects and service recreation. */
class GestureServerRuntime(
    private val store: GestureServerSnapshotStore,
    private val onSnapshotApplied: (GestureServerSnapshot) -> Unit = {}
) {
    private val stateLock = Any()
    private val persistenceLock = Any()
    private val dispatchLock = Any()
    private var current: GestureServerSnapshot? = null
    private var started = false
    private val snapshotListeners = CopyOnWriteArrayList<(GestureServerSnapshot) -> Unit>()

    val snapshot: GestureServerSnapshot?
        get() = synchronized(stateLock) { current }

    fun start(): GestureServerSnapshot? {
        return synchronized(stateLock) {
            if (!started) {
                started = true
                current = store.read()?.takeIf(GestureServerSnapshot::isValid)
            }
            current
        }
    }

    /** Registers a low-frequency snapshot listener and immediately replays the current value. */
    fun addSnapshotListener(listener: (GestureServerSnapshot) -> Unit): AutoCloseable {
        val replay = synchronized(stateLock) {
            snapshotListeners += listener
            current
        }
        replay?.let { snapshot -> runCatching { listener(snapshot) } }
        return AutoCloseable {
            synchronized(stateLock) {
                snapshotListeners.remove(listener)
            }
        }
    }

    fun apply(next: GestureServerSnapshot): GestureServerApplyResult {
        start()
        if (!next.isValid()) {
            return GestureServerApplyResult(false, "invalid_snapshot", snapshot)
        }

        val normalized = next.copy(
            leftZones = next.leftZones.toList(),
            rightZones = next.rightZones.toList(),
            leftActions = next.leftActions.toMap(),
            rightActions = next.rightActions.toMap(),
            bottomActions = next.bottomActions.toMap()
        )
        val accepted = synchronized(stateLock) {
            val previous = current
            if (previous != null && next.version <= previous.version) {
                null
            } else {
                normalized.also { current = it }
            }
        }

        if (accepted == null) {
            return GestureServerApplyResult(false, "stale_snapshot", snapshot)
        }

        persistIfCurrent(accepted)
        dispatchIfCurrent(accepted)
        return GestureServerApplyResult(true, "accepted", accepted)
    }

    /** Binder loss is intentionally non-destructive: keep the last valid local snapshot. */
    fun onBinderDisconnected(): GestureServerSnapshot? = snapshot ?: start()

    private fun persistIfCurrent(accepted: GestureServerSnapshot) {
        synchronized(persistenceLock) {
            val stillCurrent = synchronized(stateLock) {
                current?.version == accepted.version
            }
            if (stillCurrent) {
                store.write(accepted)
            }
        }
    }

    private fun dispatchIfCurrent(accepted: GestureServerSnapshot) {
        synchronized(dispatchLock) {
            val stillCurrent = synchronized(stateLock) {
                current?.version == accepted.version
            }
            if (!stillCurrent) return

            runCatching { onSnapshotApplied(accepted) }
            val listeners = synchronized(stateLock) { snapshotListeners.toList() }
            listeners.forEach { listener ->
                runCatching { listener(accepted) }
            }
        }
    }
}

class InMemoryGestureServerSnapshotStore(
    initial: GestureServerSnapshot? = null
) : GestureServerSnapshotStore {
    private var value = initial

    override fun read(): GestureServerSnapshot? = value

    override fun write(snapshot: GestureServerSnapshot) {
        value = snapshot
    }
}
