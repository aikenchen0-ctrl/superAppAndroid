package com.paifa.univerge.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.gesture.server.GestureServerSnapshot
import com.paifa.univerge.gesture.server.GestureServerZone
import com.paifa.univerge.gesture.server.IGestureServer

/** Low-frequency config bridge. Motion events never pass through this class. */
internal class GestureServerSnapshotPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val revisionPreferences = appContext.getSharedPreferences(
        REVISION_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private var connection: ServiceConnection? = null
    private var binder: IGestureServer? = null
    private var pending: GestureServerSnapshot? = null

    fun publish(snapshot: GestureServerSnapshot) {
        if (pending == null || snapshot.version >= pending!!.version) {
            pending = snapshot
        }
        val activeBinder = binder
        val pendingSnapshot = pending
        if (activeBinder != null && pendingSnapshot != null && send(activeBinder, pendingSnapshot)) {
            if (pending?.version == pendingSnapshot.version) pending = null
            return
        }
        bindIfNeeded()
    }

    fun close() {
        connection?.let { activeConnection ->
            runCatching { appContext.unbindService(activeConnection) }
        }
        connection = null
        binder = null
        pending = null
    }

    fun revisionFor(configVersion: Long): Long {
        return nextRevision(configVersion, geometry = null)
    }

    /**
     * Returns a monotonically increasing revision for config and display state.
     * A rotation or density change must invalidate the server snapshot even when
     * the user configuration version did not change.
     */
    fun revisionFor(
        configVersion: Long,
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
        inputEnabled: Boolean = true,
        floatingChatOwnsSurface: Boolean = false
    ): Long = nextRevision(
        configVersion,
        Geometry(
            widthPx = screenWidthPx.coerceAtLeast(0),
            heightPx = screenHeightPx.coerceAtLeast(0),
            densityBits = (density.takeIf { it.isFinite() && it > 0f } ?: 1f).toBits(),
            inputEnabled = inputEnabled,
            floatingChatOwnsSurface = floatingChatOwnsSurface
        )
    )

    private fun nextRevision(configVersion: Long, geometry: Geometry?): Long {
        val storedRevision = revisionPreferences.getLong(KEY_REVISION, 0L)
        val requestedConfig = configVersion.coerceAtLeast(1L)
        val storedConfig = revisionPreferences.getLong(KEY_CONFIG_VERSION, NO_CONFIG)
        val configChanged = storedConfig == NO_CONFIG || storedConfig != requestedConfig
        // Bump the transport revision when recognition semantics change even
        // if the user's persisted preference version did not change. Without
        // this, a server restored from an older snapshot can reject the new
        // default bindings as stale and keep the old gesture contract.
        val behaviorSchemaChanged = revisionPreferences.getInt(
            KEY_BEHAVIOR_SCHEMA_VERSION,
            NO_BEHAVIOR_SCHEMA_VERSION
        ) != BEHAVIOR_SCHEMA_VERSION
        val geometryKnown = revisionPreferences.getBoolean(KEY_GEOMETRY_KNOWN, false)
        val geometryChanged = geometry != null && (
                !geometryKnown ||
                revisionPreferences.getInt(KEY_WIDTH_PX, Int.MIN_VALUE) != geometry.widthPx ||
                revisionPreferences.getInt(KEY_HEIGHT_PX, Int.MIN_VALUE) != geometry.heightPx ||
                revisionPreferences.getInt(KEY_DENSITY_BITS, 0) != geometry.densityBits ||
                revisionPreferences.getBoolean(KEY_INPUT_ENABLED, true) != geometry.inputEnabled ||
                revisionPreferences.getBoolean(KEY_FLOATING_CHAT_OWNS_SURFACE, false) !=
                geometry.floatingChatOwnsSurface
            )
        val shouldAdvance = storedRevision <= 0L || configChanged || geometryChanged || behaviorSchemaChanged
        val revision = if (shouldAdvance) {
            maxOf(requestedConfig, storedRevision.saturatingIncrement())
        } else {
            storedRevision
        }
        val editor = revisionPreferences.edit()
            .putLong(KEY_REVISION, revision)
            .putLong(KEY_CONFIG_VERSION, requestedConfig)
            .putInt(KEY_BEHAVIOR_SCHEMA_VERSION, BEHAVIOR_SCHEMA_VERSION)
        if (geometry != null) {
            editor
                .putBoolean(KEY_GEOMETRY_KNOWN, true)
                .putInt(KEY_WIDTH_PX, geometry.widthPx)
                .putInt(KEY_HEIGHT_PX, geometry.heightPx)
                .putInt(KEY_DENSITY_BITS, geometry.densityBits)
                .putBoolean(KEY_INPUT_ENABLED, geometry.inputEnabled)
                .putBoolean(KEY_FLOATING_CHAT_OWNS_SURFACE, geometry.floatingChatOwnsSurface)
        }
        // A transient storage failure must not tear down the accessibility
        // service; the in-memory SharedPreferences state is still current and
        // apply() provides a best-effort asynchronous retry.
        if (!runCatching { editor.commit() }.getOrDefault(false)) {
            editor.apply()
        }
        return revision
    }

    private fun bindIfNeeded() {
        if (connection != null) return
        lateinit var nextConnection: ServiceConnection
        nextConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (connection !== nextConnection) return
                binder = IGestureServer.Stub.asInterface(service)
                pending?.let { snapshot ->
                    if (send(snapshot)) {
                        if (pending?.version == snapshot.version) pending = null
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (connection !== nextConnection) return
                binder = null
                connection = null
            }

            override fun onBindingDied(name: ComponentName) {
                if (connection !== nextConnection) return
                binder = null
                connection = null
            }
        }
        val intent = Intent(appContext, com.paifa.univerge.gesture.server.GestureServerBinderService::class.java)
        connection = nextConnection
        val bound = runCatching {
            appContext.bindService(intent, nextConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound && connection === nextConnection) {
            connection = null
            binder = null
        }
    }

    private fun send(snapshot: GestureServerSnapshot): Boolean {
        val activeBinder = binder ?: return false
        return send(activeBinder, snapshot)
    }

    private fun send(activeBinder: IGestureServer, snapshot: GestureServerSnapshot): Boolean =
        try {
            // A false result is a real server rejection, so leave the pending
            // snapshot intact. Exceptions indicate a dead Binder and should
            // release the binding so the next publish can reconnect.
            activeBinder.applySnapshotRemote(snapshot.toBundle())
        } catch (_: android.os.RemoteException) {
            invalidateBinder(activeBinder)
            false
        } catch (_: RuntimeException) {
            invalidateBinder(activeBinder)
            false
        }

    private fun invalidateBinder(activeBinder: IGestureServer) {
        if (binder === activeBinder) {
            binder = null
            connection = null
        }
    }

    private data class Geometry(
        val widthPx: Int,
        val heightPx: Int,
        val densityBits: Int,
        val inputEnabled: Boolean,
        val floatingChatOwnsSurface: Boolean
    )

    private companion object {
        const val REVISION_PREFERENCES = "gesture_server_publisher"
        const val KEY_REVISION = "revision"
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_GEOMETRY_KNOWN = "geometry_known"
        const val KEY_WIDTH_PX = "width_px"
        const val KEY_HEIGHT_PX = "height_px"
        const val KEY_DENSITY_BITS = "density_bits"
        const val KEY_INPUT_ENABLED = "input_enabled"
        const val KEY_FLOATING_CHAT_OWNS_SURFACE = "floating_chat_owns_surface"
        const val KEY_BEHAVIOR_SCHEMA_VERSION = "behavior_schema_version"
        const val BEHAVIOR_SCHEMA_VERSION = 2
        const val NO_BEHAVIOR_SCHEMA_VERSION = 0
        const val NO_CONFIG = Long.MIN_VALUE
    }
}

private fun Long.saturatingIncrement(): Long =
    if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1L

internal fun gestureServerSnapshotFromPreferences(
    preferences: UniVergePreferences,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    revision: Long,
    inputEnabled: Boolean = true,
    floatingChatOwnsSurface: Boolean = false
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
        shortPullDistanceDp = preferences.shortPullThresholdDp.toFloat(),
        longPullDistanceDp = preferences.longPullThresholdDp.toFloat(),
        holdDurationMs = 500L,
        inputEnabled = inputEnabled,
        floatingChatOwnsSurface = floatingChatOwnsSurface,
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
