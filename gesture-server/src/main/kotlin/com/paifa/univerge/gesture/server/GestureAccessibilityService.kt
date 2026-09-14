package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Parcel
import android.util.Base64
import android.view.accessibility.AccessibilityEvent

/** Process-isolated host. It deliberately has no dependency on chat/video/Compose controllers. */
class GestureAccessibilityService : AccessibilityService() {
    private lateinit var runtime: GestureServerRuntime

    override fun onCreate() {
        super.onCreate()
        runtime = GestureServerRuntimeHost.runtime(this)
        runtime.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        runtime.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

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
