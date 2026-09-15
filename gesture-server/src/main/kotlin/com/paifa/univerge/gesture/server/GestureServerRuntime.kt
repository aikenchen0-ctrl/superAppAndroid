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
    private var current: GestureServerSnapshot? = null
    private var started = false
    private val snapshotListeners = CopyOnWriteArrayList<(GestureServerSnapshot) -> Unit>()

    val snapshot: GestureServerSnapshot?
        get() = current

    fun start(): GestureServerSnapshot? {
        if (!started) {
            started = true
            current = store.read()?.takeIf(GestureServerSnapshot::isValid)
        }
        return current
    }

    /** Registers a low-frequency snapshot listener and immediately replays the current value. */
    fun addSnapshotListener(listener: (GestureServerSnapshot) -> Unit): AutoCloseable {
        snapshotListeners += listener
        current?.let { snapshot -> runCatching { listener(snapshot) } }
        return AutoCloseable { snapshotListeners.remove(listener) }
    }

    fun apply(next: GestureServerSnapshot): GestureServerApplyResult {
        start()
        if (!next.isValid()) {
            return GestureServerApplyResult(false, "invalid_snapshot", current)
        }
        val previous = current
        if (previous != null && next.version <= previous.version) {
            return GestureServerApplyResult(false, "stale_snapshot", previous)
        }
        current = next.copy(
            leftZones = next.leftZones.toList(),
            rightZones = next.rightZones.toList(),
            leftActions = next.leftActions.toMap(),
            rightActions = next.rightActions.toMap(),
            bottomActions = next.bottomActions.toMap()
        )
        store.write(current!!)
        onSnapshotApplied(current!!)
        snapshotListeners.forEach { listener ->
            runCatching { listener(current!!) }
        }
        return GestureServerApplyResult(true, "accepted", current)
    }

    /** Binder loss is intentionally non-destructive: keep the last valid local snapshot. */
    fun onBinderDisconnected(): GestureServerSnapshot? = current ?: start()
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
