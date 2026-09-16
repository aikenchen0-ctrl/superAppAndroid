package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.paifa.univerge.core.gesture.GestureEventLedger
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.runtime.ActionExecutionGate
import com.paifa.univerge.core.runtime.ActionDispatchQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class GestureServerActionKind {
    None,
    GlobalBack,
    GlobalHome,
    GlobalRecents,
    GlobalNotifications,
    GlobalQuickSettings,
    GlobalScreenshot,
    GlobalLockScreen,
    VolumeUp,
    VolumeDown,
    LaunchApp,
    ForwardToMainProcess
}

internal fun gestureServerActionKind(action: GestureAction): GestureServerActionKind = when (action) {
    GestureAction.None -> GestureServerActionKind.None
    GestureAction.Back -> GestureServerActionKind.GlobalBack
    GestureAction.Home -> GestureServerActionKind.GlobalHome
    GestureAction.Recents -> GestureServerActionKind.GlobalRecents
    GestureAction.Notifications -> GestureServerActionKind.GlobalNotifications
    GestureAction.QuickSettings -> GestureServerActionKind.GlobalQuickSettings
    GestureAction.Screenshot -> GestureServerActionKind.GlobalScreenshot
    GestureAction.LockScreen -> GestureServerActionKind.GlobalLockScreen
    GestureAction.VolumeUp -> GestureServerActionKind.VolumeUp
    GestureAction.VolumeDown -> GestureServerActionKind.VolumeDown
    is GestureAction.LaunchApp -> GestureServerActionKind.LaunchApp
    GestureAction.ExpandFloatingChat,
    GestureAction.CollapseFloatingChat,
    GestureAction.PlayVideo -> GestureServerActionKind.ForwardToMainProcess
}

/** Executes terminal actions in the isolated service process without retrying them. */
internal class GestureServerActionExecutor(
    private val service: AccessibilityService,
    private val performGlobalAction: (Int) -> Boolean = { action ->
        service.performGlobalAction(action)
    },
    dispatchExecutor: Executor = Executors.newSingleThreadExecutor {
        Thread(it, "GestureServer-actions").apply { isDaemon = true }
    },
    /** Kept for source compatibility; terminal actions intentionally share one lane. */
    @Suppress("UNUSED_PARAMETER") launchDispatchExecutor: Executor? = null,
    private val dispatchMainProcessAction: (Intent, (Boolean) -> Unit) -> Boolean = { intent, onResult ->
        service.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, resultIntent: Intent?) {
                    onResult(resultCode == Activity.RESULT_OK)
                }
            },
            Handler(Looper.getMainLooper()),
            Activity.RESULT_CANCELED,
            null,
            null
        )
        true
    }
) : AutoCloseable {
    private val terminalLedger = GestureEventLedger()
    private val actionExecutionGate = ActionExecutionGate()
    @Volatile
    private var acceptingActions = true
    private val actionQueue = ActionDispatchQueue<PendingAction>(
        executor = dispatchExecutor,
        action = { pending ->
            if (acceptingActions) {
                executeNow(pending)
            } else {
                terminalLedger.releaseGestureId(pending.data.gestureId)
            }
        },
        maxPending = 8,
        onClose = { shutdownExecutor(dispatchExecutor) }
    )
    fun execute(action: GestureAction, data: GestureData): Boolean {
        if (!terminalLedger.acceptGestureId(data.gestureId)) return false
        val accepted = actionQueue.submit(
            PendingAction(
                action = action,
                data = data,
                enqueuedAtElapsed = SystemClock.uptimeMillis()
            )
        )
        if (!accepted) {
            Log.w(TAG, "gesture action queue rejected action=${action.id} id=${data.gestureId}")
            terminalLedger.releaseGestureId(data.gestureId)
        }
        return accepted
    }

    override fun close() {
        acceptingActions = false
        actionExecutionGate.close()
        actionQueue.close()
    }

    private fun executeNow(pending: PendingAction) {
        val permit = actionExecutionGate.tryAcquire()
        if (permit == null) {
            terminalLedger.releaseGestureId(pending.data.gestureId)
            Log.w(TAG, "gesture action rejected after service close action=${pending.action.id} id=${pending.data.gestureId}")
            return
        }
        val action = pending.action
        val data = pending.data
        try {
            val startedAt = SystemClock.uptimeMillis()
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                    TAG,
                    "[DEBUG-action-timing] start action=${action.id} id=${data.gestureId} " +
                        "queueWaitMs=${(startedAt - pending.enqueuedAtElapsed).coerceAtLeast(0L)}"
                )
            }
            val succeeded = runCatching {
                when (action) {
                    GestureAction.None -> true
                    GestureAction.Back -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    GestureAction.Home -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    GestureAction.Recents -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    GestureAction.Notifications -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    GestureAction.QuickSettings -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    GestureAction.Screenshot -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    } else false
                    GestureAction.LockScreen -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    } else false
                    GestureAction.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
                    GestureAction.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
                    is GestureAction.LaunchApp -> launch(action.packageName, data.gestureId)
                    GestureAction.ExpandFloatingChat,
                    GestureAction.CollapseFloatingChat,
                    GestureAction.PlayVideo -> forwardToMainProcess(action, data)
                }
            }.onFailure { error ->
                Log.w(TAG, "gesture action failed action=${action.id} id=${data.gestureId}", error)
            }.also {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                        TAG,
                        "[DEBUG-action-timing] end action=${action.id} id=${data.gestureId} " +
                            "durationMs=${(SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)}"
                    )
                }
            }.getOrDefault(false)
            if (!succeeded) {
                terminalLedger.releaseGestureId(data.gestureId)
                Log.w(TAG, "gesture action rejected action=${action.id} id=${data.gestureId}")
            }
        } finally {
            permit.close()
        }
    }

    private fun adjustVolume(direction: Int): Boolean {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun launch(packageName: String, gestureId: Long): Boolean {
        val resolveStartedAt = SystemClock.uptimeMillis()
        traceLaunch(
            "resolve-start package=$packageName id=$gestureId uptime=$resolveStartedAt"
        )
        val intent = runCatching {
            service.packageManager.getLaunchIntentForPackage(packageName)
        }.onFailure { error ->
            traceLaunch(
                "resolve-error package=$packageName id=$gestureId " +
                    "durationMs=${(SystemClock.uptimeMillis() - resolveStartedAt).coerceAtLeast(0L)} " +
                    "error=${error.javaClass.simpleName}"
            )
        }.getOrElse { throw it }
        traceLaunch(
            "resolve-end package=$packageName id=$gestureId " +
                "durationMs=${(SystemClock.uptimeMillis() - resolveStartedAt).coerceAtLeast(0L)} " +
                "resolved=${intent != null}"
        )
        if (intent == null) return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val startActivityStartedAt = SystemClock.uptimeMillis()
        traceLaunch(
            "startActivity-start package=$packageName id=$gestureId uptime=$startActivityStartedAt"
        )
        return try {
            service.startActivity(intent)
            traceLaunch(
                "startActivity-end package=$packageName id=$gestureId " +
                    "durationMs=${(SystemClock.uptimeMillis() - startActivityStartedAt).coerceAtLeast(0L)}"
            )
            true
        } catch (error: Throwable) {
            traceLaunch(
                "startActivity-error package=$packageName id=$gestureId " +
                    "durationMs=${(SystemClock.uptimeMillis() - startActivityStartedAt).coerceAtLeast(0L)} " +
                    "error=${error.javaClass.simpleName}"
            )
            throw error
        }
    }

    private fun traceLaunch(message: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "[DEBUG-launch-trace] thread=${Thread.currentThread().name} $message")
        }
    }

    private fun forwardToMainProcess(action: GestureAction, data: GestureData): Boolean {
        val gestureId = data.gestureId
        val intent = Intent(ACTION_MAIN_PROCESS_GESTURE)
                .setPackage(service.packageName)
                .putExtra(EXTRA_ACTION_ID, action.id)
                .putExtra(EXTRA_GESTURE_ID, gestureId)
        return runCatching {
            dispatchMainProcessAction(intent) { accepted ->
                if (!accepted) {
                    terminalLedger.releaseGestureId(gestureId)
                    Log.w(
                        TAG,
                        "main process rejected forwarded action=${action.id} id=$gestureId"
                    )
                }
            }
        }.onFailure { error ->
            terminalLedger.releaseGestureId(gestureId)
            Log.w(TAG, "failed to forward action=${action.id} id=$gestureId", error)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "GestureServer"

        fun shutdownExecutor(executor: Executor) {
            (executor as? ExecutorService)?.shutdown()
        }
    }

    private data class PendingAction(
        val action: GestureAction,
        val data: GestureData,
        val enqueuedAtElapsed: Long
    )
}

const val ACTION_MAIN_PROCESS_GESTURE = "com.paifa.univerge.action.GESTURE_SERVER_ACTION"
const val EXTRA_ACTION_ID = "gesture_action_id"
const val EXTRA_GESTURE_ID = "gesture_id"
const val ACTION_RESULT_ACCEPTED = Activity.RESULT_OK
const val ACTION_RESULT_REJECTED = Activity.RESULT_CANCELED
