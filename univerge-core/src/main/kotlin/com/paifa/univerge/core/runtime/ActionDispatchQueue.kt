package com.paifa.univerge.core.runtime

import java.util.concurrent.Executor

/**
 * Accepts a terminal action without running it on the input callback stack.
 * The executor and consumer are deliberately injected so platform adapters can
 * choose their own lifecycle and tests can observe dispatch deterministically.
 */
class ActionDispatchQueue<T>(
    private val executor: Executor,
    private val action: (T) -> Unit,
    private val onClose: () -> Unit = {},
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val cancelPendingOnClose: Boolean = false
) : AutoCloseable {
    init {
        require(maxPending > 0) { "maxPending must be positive" }
    }

    private var accepting = true
    private var cancelPending = false
    private var pendingCount = 0
    private val submitLock = Any()

    fun submit(value: T): Boolean {
        synchronized(submitLock) {
            if (!accepting) return false
            if (pendingCount >= maxPending) return false
            pendingCount += 1
            return runCatching {
                executor.execute {
                    try {
                        val shouldRun = synchronized(submitLock) { !cancelPending }
                        if (shouldRun) action(value)
                    } finally {
                        synchronized(submitLock) {
                            pendingCount -= 1
                        }
                    }
                }
            }.onFailure {
                pendingCount -= 1
            }.isSuccess
        }
    }

    override fun close() {
        val shouldClose = synchronized(submitLock) {
            if (!accepting) {
                false
            } else {
                accepting = false
                cancelPending = cancelPendingOnClose
                true
            }
        }
        if (shouldClose) {
            onClose()
        }
    }

    private companion object {
        const val DEFAULT_MAX_PENDING = 16
    }
}
