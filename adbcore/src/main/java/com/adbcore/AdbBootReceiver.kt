package com.adbcore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * On boot:
 *   1. Re-enable wireless debugging via [AdbWirelessSettings] (requires WRITE_SECURE_SETTINGS).
 *   2. Initialize AdbCore so the persisted task store is wired up.
 *   3. Eagerly bring up the server through ADB so persisted script loops
 *      can resume immediately, before any business code in the host app
 *      even starts.
 *
 * Works during LOCKED_BOOT_COMPLETED thanks to directBootAware="true" + a
 * device-protected SharedPreferences for the ADB key.
 *
 * Note: at LOCKED_BOOT_COMPLETED time the user has typically NOT yet
 * unlocked the device, which means WiFi often is not connected yet.
 * That's expected — we try loopback/persisted TCP ports first, schedule a
 * few short boot retries, then rely on [WifiAutoRecover] (host App side)
 * or the next [AdbCore.exec] call to lazily bring the server back up.
 */
class AdbBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBootAction = Intent.ACTION_LOCKED_BOOT_COMPLETED == action ||
                Intent.ACTION_BOOT_COMPLETED == action
        val isRetryAction = ACTION_BOOT_RETRY == action
        if (!isBootAction && !isRetryAction) return

        if (myUserId() > 0) return

        if (!AdbWirelessSettings.enable(context)) {
            AdbCoreLog.w("BootReceiver: WRITE_SECURE_SETTINGS not granted; skip auto-restore.")
            return
        }
        AdbCoreLog.i("BootReceiver: wireless debugging re-enabled.")

        // Eagerly bring up the server + restore persisted script loops. If boot is still
        // early and adbd has not started listening on loopback yet, schedule a few
        // lightweight retry broadcasts instead of waiting inside this receiver forever.
        val retryIndex = if (isRetryAction) intent.getIntExtra(EXTRA_BOOT_RETRY_INDEX, 0) else 0
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AdbCore.init(context)
                val result = withTimeoutOrNull(10.seconds) {
                    AdbCore.ensureServerRunning(waitMs = 8_000)
                }
                when {
                    result == null -> AdbCoreLog.i(
                        "BootReceiver: server not reachable yet (likely no WiFi/mDNS); " +
                            "will schedule a short boot retry."
                    )
                    result.isFailure -> {
                        val msg = result.exceptionOrNull()?.message ?: "no msg"
                        AdbCoreLog.i("BootReceiver: ensureServerRunning skipped: $msg")
                    }
                    else -> {
                        cancelBootRetry(context)
                        AdbCoreLog.i("BootReceiver: server back up after boot.")
                        return@launch
                    }
                }
                scheduleBootRetry(context, retryIndex)
            } catch (t: Throwable) {
                AdbCoreLog.i("BootReceiver: ensureServerRunning skipped (${t.message ?: "no msg"})")
                scheduleBootRetry(context, retryIndex)
            } finally {
                pending.finish()
            }
        }
    }

    private fun myUserId(): Int = runCatching {
        val handle: UserHandle = Process.myUserHandle()
        (UserHandle::class.java.getMethod("getIdentifier").invoke(handle) as Int)
    }.getOrDefault(0)

    private fun scheduleBootRetry(context: Context, retryIndex: Int) {
        if (retryIndex >= BOOT_RETRY_DELAYS_MS.size) {
            AdbCoreLog.i("BootReceiver: boot retry budget exhausted; waiting for WiFiAutoRecover/user action.")
            return
        }
        val delayMs = BOOT_RETRY_DELAYS_MS[retryIndex]
        val pi = bootRetryPendingIntent(context, retryIndex + 1)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        AdbCoreLog.i("BootReceiver: scheduled retry ${retryIndex + 1}/${BOOT_RETRY_DELAYS_MS.size} in ${delayMs / 1000}s")
    }

    private fun cancelBootRetry(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(bootRetryPendingIntent(context, 0))
    }

    private fun bootRetryPendingIntent(context: Context, retryIndex: Int): PendingIntent {
        val intent = Intent(context, AdbBootReceiver::class.java)
            .setAction(ACTION_BOOT_RETRY)
            .putExtra(EXTRA_BOOT_RETRY_INDEX, retryIndex)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BOOT_RETRY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val ACTION_BOOT_RETRY = "com.adbcore.action.BOOT_RETRY"
        private const val EXTRA_BOOT_RETRY_INDEX = "boot_retry_index"
        private const val REQUEST_BOOT_RETRY = 0xADBC
        private val BOOT_RETRY_DELAYS_MS = longArrayOf(10_000L, 20_000L, 40_000L)
    }
}
