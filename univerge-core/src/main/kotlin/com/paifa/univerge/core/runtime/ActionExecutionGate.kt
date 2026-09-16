package com.paifa.univerge.core.runtime

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Linearizes lifecycle shutdown against the start of a side effect.
 *
 * Closing the gate rejects permits that have not started yet. A permit acquired
 * before close represents in-flight work and remains valid until it is released;
 * close therefore never waits for a slow platform call.
 */
class ActionExecutionGate : AutoCloseable {
    private val lock = Any()
    private var accepting = true
    private var inFlight = 0

    fun tryAcquire(): Permit? = synchronized(lock) {
        if (!accepting) {
            null
        } else {
            inFlight += 1
            Permit()
        }
    }

    override fun close() {
        synchronized(lock) {
            accepting = false
        }
    }

    private fun release() {
        synchronized(lock) {
            check(inFlight > 0) { "execution permit released without acquisition" }
            inFlight -= 1
        }
    }

    inner class Permit internal constructor() : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) {
                release()
            }
        }
    }
}
