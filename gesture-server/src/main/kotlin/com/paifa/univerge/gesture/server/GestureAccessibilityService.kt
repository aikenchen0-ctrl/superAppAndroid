package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Parcel
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Base64
import android.view.accessibility.AccessibilityEvent

/** Process-isolated host. It deliberately has no dependency on chat/video/Compose controllers. */
class GestureAccessibilityService : AccessibilityService() {
    private lateinit var runtime: GestureServerRuntime
    private lateinit var overlayController: GestureServerOverlayController
    private lateinit var actionExecutor: GestureServerActionExecutor
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceConnected = false
    private val leaseHeartbeat = object : Runnable {
        override fun run() {
            if (serviceConnected && ::overlayController.isInitialized && overlayController.hasInputSurface) {
                GestureServerProcessLease.heartbeat(this@GestureAccessibilityService)
            } else {
                GestureServerProcessLease.release(this@GestureAccessibilityService)
            }
            mainHandler.postDelayed(this, LEASE_HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runtime = GestureServerRuntimeHost.runtime(this)
        runtime.start()
        actionExecutor = GestureServerActionExecutor(this)
        overlayController = GestureServerOverlayController(
            service = this,
            windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager,
            onAction = actionExecutor::execute,
            onInputSurfaceChanged = { refreshLease() },
            onInputSurfaceWillChange = { available ->
                if (available) {
                    GestureServerProcessLease.acquire(this)
                } else {
                    GestureServerProcessLease.release(this)
                }
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnected = true
        runtime.start()
        overlayController.start(runtime)
        mainHandler.removeCallbacks(leaseHeartbeat)
        mainHandler.post(leaseHeartbeat)
        mainHandler.post { refreshLease() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            Log.isLoggable(TAG, Log.DEBUG)
        ) {
            Log.d(
                TAG,
                "[DEBUG-launch-trace] phase=window-event package=${event.packageName ?: ""} " +
                    "class=${event.className ?: ""} eventTime=${event.eventTime} " +
                    "uptime=${SystemClock.uptimeMillis()}"
            )
        }
    }

    override fun onInterrupt() {
        if (::overlayController.isInitialized) {
            overlayController.cancelActiveGesture()
        }
    }

    override fun onDestroy() {
        serviceConnected = false
        mainHandler.removeCallbacks(leaseHeartbeat)
        if (::actionExecutor.isInitialized) actionExecutor.close()
        if (::overlayController.isInitialized) overlayController.close()
        GestureServerProcessLease.release(this)
        super.onDestroy()
    }

    private fun refreshLease() {
        if (serviceConnected && ::overlayController.isInitialized && overlayController.hasInputSurface) {
            GestureServerProcessLease.acquire(this)
        } else {
            GestureServerProcessLease.release(this)
        }
    }

    private companion object {
        const val TAG = "GestureServer"
        const val LEASE_HEARTBEAT_INTERVAL_MS = 1_000L
    }
}

/** Regular bound service exposes Binder because AccessibilityService.onBind is final. */
class GestureServerBinderService : android.app.Service() {
    override fun onBind(intent: Intent?) = GestureServerRuntimeHost.binder(this)
}

private object GestureServerRuntimeHost {
    private const val PREFERENCES_NAME = "gesture_server_snapshot"
    private var runtime: GestureServerRuntime? = null
    private var binder: GestureServerBinder? = null

    @Synchronized
    fun runtime(context: Context): GestureServerRuntime {
        return runtime ?: GestureServerRuntime(
            SharedPreferencesSnapshotStore(
                context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
            )
        ).also { runtime = it }
    }

    @Synchronized
    fun binder(context: Context): GestureServerBinder {
        return binder ?: GestureServerBinder(runtime(context)).also { binder = it }
    }
}

private class SharedPreferencesSnapshotStore(
    private val preferences: SharedPreferences
) : GestureServerSnapshotStore {
    override fun read(): GestureServerSnapshot? {
        val encoded = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                GestureServerSnapshot.fromBundle(
                    parcel.readBundle(GestureServerSnapshot::class.java.classLoader)
                )
            } finally {
                parcel.recycle()
            }
        }.getOrNull()
    }

    override fun write(snapshot: GestureServerSnapshot) {
        val parcel = Parcel.obtain()
        try {
            snapshot.toBundle().writeToParcel(parcel, 0)
            preferences.edit()
                .putString(KEY_PAYLOAD, Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP))
                .apply()
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        const val KEY_PAYLOAD = "snapshot"
    }
}
