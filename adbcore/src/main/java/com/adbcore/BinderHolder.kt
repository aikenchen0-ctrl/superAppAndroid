package com.adbcore

import android.os.IBinder
import com.adbcore.server.IAdbCoreService

/**
 * Holds the live binder to adbcore_server inside the host app process.
 * Updated when [AdbCoreProvider] receives a sendBinder call.
 */
internal object BinderHolder {

    @Volatile
    private var serviceBinder: IBinder? = null

    @Volatile
    private var service: IAdbCoreService? = null

    private val deathRecipient = IBinder.DeathRecipient {
        AdbCoreLog.w("server binder died")
        service = null
        serviceBinder = null
    }

    fun set(binder: IBinder?) {
        if (binder == null) {
            clear()
            return
        }
        if (binder == serviceBinder) return

        clear()
        serviceBinder = binder
        service = IAdbCoreService.Stub.asInterface(binder)
        runCatching { binder.linkToDeath(deathRecipient, 0) }
            .onFailure { AdbCoreLog.w("linkToDeath failed", it) }
        AdbCoreLog.i("server binder received and bound")
    }

    fun clear() {
        val old = serviceBinder
        serviceBinder = null
        service = null
        if (old != null) {
            runCatching { old.unlinkToDeath(deathRecipient, 0) }
        }
    }

    fun get(): IAdbCoreService? = service?.takeIf { isAlive() }

    fun isAlive(): Boolean {
        val b = serviceBinder ?: return false
        return runCatching { b.pingBinder() }.getOrDefault(false)
    }
}
