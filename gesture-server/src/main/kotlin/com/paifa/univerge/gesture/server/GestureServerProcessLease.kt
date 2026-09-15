package com.paifa.univerge.gesture.server

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import java.io.File

data class GestureServerLease(
    val pid: Int,
    val heartbeatElapsedRealtime: Long
)

internal fun isGestureServerLeaseFresh(
    lease: GestureServerLease,
    nowElapsedRealtime: Long,
    timeoutMs: Long
): Boolean {
    if (timeoutMs <= 0L || nowElapsedRealtime < lease.heartbeatElapsedRealtime) return false
    return nowElapsedRealtime - lease.heartbeatElapsedRealtime < timeoutMs
}

/** Small cross-process lease used to prevent the main service from owning the same edges. */
object GestureServerProcessLease {
    const val ACTION_OWNER_CHANGED = "com.paifa.univerge.action.GESTURE_SERVER_OWNER_CHANGED"
    private const val FILE_NAME = "gesture_server_input_lease"
    private const val LEASE_TIMEOUT_MS = 3_000L

    fun acquire(context: Context) {
        write(context.applicationContext, GestureServerLease(Process.myPid(), SystemClock.elapsedRealtime()))
        notifyOwnerChanged(context)
    }

    fun heartbeat(context: Context) {
        val appContext = context.applicationContext
        val current = read(appContext)
        if (current?.pid != Process.myPid()) {
            acquire(appContext)
        } else {
            write(appContext, current.copy(heartbeatElapsedRealtime = SystemClock.elapsedRealtime()))
        }
    }

    fun release(context: Context) {
        val appContext = context.applicationContext
        val current = read(appContext)
        if (shouldNotifyGestureServerRelease(current, Process.myPid())) {
            val deleted = runCatching { leaseFile(appContext).delete() }.getOrDefault(false)
            if (deleted) notifyOwnerChanged(appContext)
        }
    }

    fun isActive(
        context: Context,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val lease = read(context.applicationContext) ?: return false
        return isGestureServerLeaseFresh(lease, nowElapsedRealtime, LEASE_TIMEOUT_MS)
    }

    private fun read(context: Context): GestureServerLease? {
        val value = runCatching { leaseFile(context).readText().trim() }.getOrNull() ?: return null
        val parts = value.split(',')
        if (parts.size != 2) return null
        return GestureServerLease(
            pid = parts[0].toIntOrNull() ?: return null,
            heartbeatElapsedRealtime = parts[1].toLongOrNull() ?: return null
        )
    }

    private fun write(context: Context, lease: GestureServerLease) {
        val target = leaseFile(context)
        val temporary = File(target.parentFile, "${target.name}.${lease.pid}.tmp")
        runCatching {
            temporary.writeText("${lease.pid},${lease.heartbeatElapsedRealtime}")
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private fun leaseFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun notifyOwnerChanged(context: Context) {
        runCatching {
            context.sendBroadcast(Intent(ACTION_OWNER_CHANGED).setPackage(context.packageName))
        }
    }
}

internal fun shouldNotifyGestureServerRelease(
    lease: GestureServerLease?,
    currentPid: Int
): Boolean = lease?.pid == currentPid
