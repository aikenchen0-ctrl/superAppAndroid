package com.paifa.ubikitouch.accessibility

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class UbikiGestureKeepAliveService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            if (!UbikiGesturePersistence.isAccessibilityServiceEnabled(this@UbikiGestureKeepAliveService)) {
                Log.d(TAG, "accessibility disabled, stop keep alive")
                UbikiGesturePersistence.cancelRecoveryWatchdog(this@UbikiGestureKeepAliveService)
                stopSelf()
                return
            }
            UbikiGesturePersistence.scheduleRecoveryWatchdog(this@UbikiGestureKeepAliveService)
            UbikiAccessibilityService.instance?.requestOverlayRecoveryCheck()
            mainHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "keep alive created")
        startForeground()
        UbikiGesturePersistence.scheduleRecoveryWatchdog(this)
        scheduleHealthCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "keep alive start flags=$flags startId=$startId")
        startForeground()
        UbikiGesturePersistence.scheduleRecoveryWatchdog(this)
        scheduleHealthCheck()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "task removed, schedule recovery watchdog")
        if (UbikiGesturePersistence.isAccessibilityServiceEnabled(this)) {
            UbikiGesturePersistence.scheduleRecoveryWatchdog(this, TASK_REMOVED_RECOVERY_DELAY_MS)
            UbikiAccessibilityService.instance?.requestOverlayRecoveryCheck()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "keep alive destroyed")
        mainHandler.removeCallbacks(healthCheckRunnable)
        if (UbikiGesturePersistence.isAccessibilityServiceEnabled(this)) {
            UbikiGesturePersistence.scheduleRecoveryWatchdog(this, SERVICE_DESTROYED_RECOVERY_DELAY_MS)
        } else {
            UbikiGesturePersistence.cancelRecoveryWatchdog(this)
        }
        UbikiGesturePersistence.stopForeground(this)
        super.onDestroy()
    }

    private fun startForeground() {
        runCatching {
            UbikiGesturePersistence.startKeepAliveForeground(this)
        }.onFailure {
            Log.w(TAG, "failed to start keep alive foreground", it)
        }
    }

    private fun scheduleHealthCheck() {
        mainHandler.removeCallbacks(healthCheckRunnable)
        mainHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS)
    }

    private companion object {
        const val TAG = "UbikiKeepAlive"
        const val HEALTH_CHECK_INTERVAL_MS = 10_000L
        const val TASK_REMOVED_RECOVERY_DELAY_MS = 1_500L
        const val SERVICE_DESTROYED_RECOVERY_DELAY_MS = 3_000L
    }
}
