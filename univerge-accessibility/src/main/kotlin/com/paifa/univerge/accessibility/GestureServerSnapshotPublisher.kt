package com.paifa.univerge.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.gesture.server.GestureServerBinder
import com.paifa.univerge.gesture.server.GestureServerSnapshot
import com.paifa.univerge.gesture.server.GestureServerZone
import com.paifa.univerge.core.model.GestureType

/** Low-frequency config bridge. Motion events never pass through this class. */
internal class GestureServerSnapshotPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val revisionPreferences = appContext.getSharedPreferences(
        REVISION_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private var connection: ServiceConnection? = null
    private var binder: GestureServerBinder? = null
    private var pending: GestureServerSnapshot? = null

    fun publish(snapshot: GestureServerSnapshot) {
        pending = snapshot
        val activeBinder = binder
        if (activeBinder != null) {
            activeBinder.applySnapshot(snapshot.toBundle())
            pending = null
            return
        }
        bindIfNeeded()
    }

    fun close() {
        val activeConnection = connection ?: return
        runCatching { appContext.unbindService(activeConnection) }
        connection = null
        binder = null
    }

    fun revisionFor(configVersion: Long): Long {
        val stored = revisionPreferences.getLong(KEY_REVISION, 0L)
        val requested = configVersion.coerceAtLeast(1L)
        val revision = maxOf(stored, requested)
        if (revision != stored) {
            revisionPreferences.edit().putLong(KEY_REVISION, revision).apply()
        }
        return revision
    }

    private fun bindIfNeeded() {
        if (connection != null) return
        val nextConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service as? GestureServerBinder
                pending?.let { snapshot ->
                    binder?.applySnapshot(snapshot.toBundle())
                    pending = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                binder = null
            }

            override fun onBindingDied(name: ComponentName) {
                binder = null
                connection = null
            }
        }
        val intent = Intent(appContext, com.paifa.univerge.gesture.server.GestureServerBinderService::class.java)
        if (appContext.bindService(intent, nextConnection, Context.BIND_AUTO_CREATE)) {
            connection = nextConnection
        }
    }

    private companion object {
        const val REVISION_PREFERENCES = "gesture_server_publisher"
        const val KEY_REVISION = "revision"
    }
}

internal fun gestureServerSnapshotFromPreferences(
    preferences: UniVergePreferences,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    revision: Long
): GestureServerSnapshot {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val widthDp = screenWidthPx.coerceAtLeast(0) / safeDensity
    val heightDp = screenHeightPx.coerceAtLeast(0) / safeDensity
    fun zone(config: EdgeZoneConfig): GestureServerZone {
        val sanitized = config.sanitized()
        return GestureServerZone(
            zoneId = sanitized.zoneId,
            enabled = sanitized.enabled,
            startDp = heightDp * sanitized.topInsetPercent / 100f,
            lengthDp = heightDp * (100 - sanitized.topInsetPercent - sanitized.bottomInsetPercent) / 100f,
            thicknessDp = sanitized.thicknessDp.toFloat(),
            edgeInsetDp = sanitized.edgeInsetDp.toFloat()
        )
    }
    return GestureServerSnapshot(
        version = revision.coerceAtLeast(1L),
        density = safeDensity,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        leftZones = preferences.edgeConfigs(EdgeSide.LEFT).map(::zone),
        rightZones = preferences.edgeConfigs(EdgeSide.RIGHT).map(::zone),
        bottomWidthDp = preferences.bottomGestureBarWidthDp.toFloat(),
        bottomHeightDp = bottomGestureBarTouchHeightDp().toFloat(),
        leftActions = GestureType.entries.associate { gesture ->
            gesture.id to preferences.actionFor(EdgeSide.LEFT, gesture).id
        },
        rightActions = GestureType.entries.associate { gesture ->
            gesture.id to preferences.actionFor(EdgeSide.RIGHT, gesture).id
        },
        bottomActions = mapOf(
            GestureType.TAP.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.Tap).id,
            GestureType.SWIPE_UP.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeUp).id,
            GestureType.SWIPE_UP_HOLD.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeUpHold).id,
            GestureType.SWIPE_LEFT.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeHorizontal).id,
            GestureType.SWIPE_RIGHT.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeHorizontal).id,
            GestureType.LONG_PRESS.id to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.LongPress).id
        )
    )
}
