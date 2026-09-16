package com.paifa.univerge.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.paifa.univerge.core.runtime.ActionExecutionGate
import com.paifa.univerge.core.runtime.ActionDispatchQueue
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class UniVergeActionExecutor(
    private val service: AccessibilityService,
    private val isHapticFeedbackEnabled: () -> Boolean,
    private val floatingChatOverlayController: FloatingChatOverlayController? = null,
    private val videoDemoOverlayController: VideoDemoOverlayController? = null,
    private val performGlobalAction: (Int) -> Boolean = { action ->
        service.performGlobalAction(action)
    },
    private val backOverrideAvailable: () -> Boolean = {
        videoDemoOverlayController?.hasBackOverride() == true ||
            floatingChatOverlayController?.hasBackOverride() == true
    },
    private val dismissBackOverride: () -> Boolean = {
        if (videoDemoOverlayController?.dismissIfShowing() == true) {
            true
        } else {
            floatingChatOverlayController?.dismissPreviewOrSheet() == true
        }
    },
    dispatchExecutor: Executor = Executors.newSingleThreadExecutor {
        Thread(it, "UniVerge-actions").apply { isDaemon = true }
    },
    /** Kept for source compatibility; terminal actions intentionally share one lane. */
    @Suppress("UNUSED_PARAMETER") launchDispatchExecutor: Executor? = null,
    localDispatchExecutor: Executor? = null
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val actionExecutionGate = ActionExecutionGate()
    @Volatile
    private var acceptingActions = true
    private val localExecutor = localDispatchExecutor ?: Executor { command -> mainHandler.post(command) }
    private val localActionQueue = ActionDispatchQueue<LocalAction>(
        executor = localExecutor,
        action = { request ->
            if (acceptingActions) {
                executeLocalAction(request)
            } else {
                notifyRejected(request.onRejected)
            }
        },
        maxPending = 4,
        onClose = { shutdownExecutor(localExecutor) }
    )
    private val systemActionQueue = ActionDispatchQueue<SystemAction>(
        executor = dispatchExecutor,
        action = { request ->
            if (acceptingActions) {
                executeSystemAction(request)
            } else {
                notifyRejected(request.onRejected)
            }
        },
        maxPending = 8,
        onClose = { shutdownExecutor(dispatchExecutor) }
    )

    fun execute(
        action: GestureAction,
        data: GestureData,
        onDispatched: (() -> Unit)? = null,
        onRejected: (() -> Unit)? = null
    ): Boolean {
        if (action == GestureAction.None) {
            if (!acceptingActions) {
                notifyRejected(onRejected)
                return false
            }
            onDispatched?.invoke()
            return true
        }
        Log.d(TAG, "execute action=${action.id} start=(${data.startX},${data.startY}) end=(${data.endX},${data.endY})")

        when (action) {
            GestureAction.Back -> {
                return dispatchSystemAction(action, data, onDispatched, onRejected)
            }
            GestureAction.ExpandFloatingChat,
            GestureAction.CollapseFloatingChat,
            GestureAction.PlayVideo -> {
                return dispatchLocalAction(action, data, onDispatched, onRejected)
            }
            else -> return dispatchSystemAction(action, data, onDispatched, onRejected)
        }
    }

    override fun close() {
        acceptingActions = false
        actionExecutionGate.close()
        localActionQueue.close()
        systemActionQueue.close()
    }

    private fun dispatchLocalAction(
        action: GestureAction,
        data: GestureData,
        onDispatched: (() -> Unit)?,
        onRejected: (() -> Unit)?
    ): Boolean {
        val accepted = localActionQueue.submit(
            LocalAction(
                action = action,
                data = data,
                onDispatched = onDispatched,
                onRejected = onRejected
            )
        )
        if (!accepted) {
            Log.w(TAG, "local action queue rejected action=${action.id} id=${data.gestureId}")
            notifyRejected(onRejected)
        }
        return accepted
    }

    private fun dispatchSystemAction(
        action: GestureAction,
        data: GestureData,
        onDispatched: (() -> Unit)?,
        onRejected: (() -> Unit)?,
        resolveBackOverride: Boolean = true
    ): Boolean {
        val accepted = systemActionQueue.submit(
            SystemAction(
                action = action,
                data = data,
                enqueuedAtElapsed = SystemClock.uptimeMillis(),
                onDispatched = onDispatched,
                onRejected = onRejected,
                resolveBackOverride = resolveBackOverride
            )
        )
        if (!accepted) {
            Log.w(TAG, "system action queue rejected action=${action.id} id=${data.gestureId}")
            notifyRejected(onRejected)
        }
        return accepted
    }

    private fun notifyRejected(onRejected: (() -> Unit)?) {
        runCatching { onRejected?.invoke() }
            .onFailure { error -> Log.w(TAG, "action rejection callback failed", error) }
    }

    private fun executeLocalAction(request: LocalAction) {
        if (!acceptingActions) {
            notifyRejected(request.onRejected)
            return
        }
        runCatching {
            performHapticFeedback()
            val permit = actionExecutionGate.tryAcquire() ?: return@runCatching false
            try {
                when (request.action) {
                    GestureAction.ExpandFloatingChat -> floatingChatOverlayController?.expand()
                    GestureAction.CollapseFloatingChat -> floatingChatOverlayController?.collapse()
                    GestureAction.PlayVideo -> videoDemoOverlayController?.showOrToggle()
                    else -> Unit
                }
                true
            } finally {
                permit.close()
            }
        }.onSuccess {
            if (it) {
                request.onDispatched?.invoke()
            } else {
                notifyRejected(request.onRejected)
            }
        }.onFailure { error ->
            Log.w(
                TAG,
                "local gesture action failed action=${request.action.id} id=${request.data.gestureId}",
                error
            )
            notifyRejected(request.onRejected)
        }
    }

    private fun executeSystemAction(request: SystemAction) {
        val action = request.action
        val data = request.data
        val startedAt = SystemClock.uptimeMillis()
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "[DEBUG-action-timing] start action=${action.id} id=${data.gestureId} " +
                "queueWaitMs=${(startedAt - request.enqueuedAtElapsed).coerceAtLeast(0L)}"
            )
        }
        if (action == GestureAction.Back && request.resolveBackOverride) {
            resolveBackAction(request, startedAt)
            return
        }
        if (executePlatformAction(request)) {
            finishSystemAction(request, startedAt)
        } else {
            rejectSystemAction(request, startedAt)
        }
    }

    private fun resolveBackAction(request: SystemAction, startedAt: Long) {
        // Controller state and WindowManager mutations belong to the main thread.
        // Resolve them after the input callback has returned; the platform Back
        // call can stay on this main-thread hop when no local override consumes it.
        mainHandler.post {
            if (!acceptingActions) {
                notifyRejected(request.onRejected)
                return@post
            }
            val permit = actionExecutionGate.tryAcquire()
            if (permit == null) {
                notifyRejected(request.onRejected)
                return@post
            }
            val consumed = runCatching {
                try {
                    backOverrideAvailable() && dismissBackOverride()
                } finally {
                    permit.close()
                }
            }.onFailure { error ->
                Log.w(TAG, "back override resolution failed", error)
            }.getOrDefault(false)
            if (consumed) {
                performHapticFeedback()
                finishSystemAction(request, startedAt)
                return@post
            }
            // The request has already left the input callback and the
            // override decision is main-thread-only. Calling the platform
            // action here avoids a guaranteed main -> worker round trip for
            // the common no-override Back path.
            if (executePlatformAction(request)) {
                finishSystemAction(request, startedAt)
            } else {
                rejectSystemAction(request, startedAt)
            }
        }
    }

    private fun executePlatformAction(request: SystemAction): Boolean {
        val action = request.action
        val data = request.data
        return runCatching {
            if (!acceptingActions) return@runCatching false
            performHapticFeedback()
            val permit = actionExecutionGate.tryAcquire() ?: return@runCatching false
            try {
                when (action) {
                    GestureAction.Back -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    GestureAction.Home -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    GestureAction.Recents -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    GestureAction.Notifications -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    GestureAction.QuickSettings -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    GestureAction.Screenshot -> takeScreenshot()
                    GestureAction.LockScreen -> lockScreen()
                    GestureAction.VolumeUp -> {
                        adjustMusicVolume(AudioManager.ADJUST_RAISE)
                        true
                    }
                    GestureAction.VolumeDown -> {
                        adjustMusicVolume(AudioManager.ADJUST_LOWER)
                        true
                    }
                    is GestureAction.LaunchApp -> launchApp(action.packageName, data.gestureId)
                    GestureAction.None,
                    GestureAction.ExpandFloatingChat,
                    GestureAction.CollapseFloatingChat,
                    GestureAction.PlayVideo -> true
                }
            } finally {
                permit.close()
            }
        }.onFailure { error ->
            Log.w(TAG, "system gesture action failed action=${action.id} id=${data.gestureId}", error)
        }.getOrDefault(false)
    }

    private fun rejectSystemAction(request: SystemAction, startedAt: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "[DEBUG-action-timing] rejected action=${request.action.id} id=${request.data.gestureId} " +
                    "durationMs=${(SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)}"
            )
        }
        notifyRejected(request.onRejected)
    }

    private fun finishSystemAction(request: SystemAction, startedAt: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "[DEBUG-action-timing] end action=${request.action.id} id=${request.data.gestureId} " +
                    "durationMs=${(SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)}"
            )
        }
        request.onDispatched?.let { callback ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback()
            } else {
                mainHandler.post(callback)
            }
        }
    }

    fun performHapticFeedback() {
        runCatching {
            if (isHapticFeedbackEnabled()) vibrate()
        }.onFailure { error ->
            Log.w(TAG, "haptic preference unavailable", error)
        }
    }

    private fun takeScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
        return false
    }

    private fun lockScreen(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
        return false
    }

    private fun launchApp(packageName: String, gestureId: Long): Boolean {
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
        if (intent == null) {
            mainHandler.post {
                Toast.makeText(service, R.string.ubiki_launch_app_failed, Toast.LENGTH_SHORT).show()
            }
            return false
        }
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[DEBUG-launch-trace] thread=${Thread.currentThread().name} $message")
        }
    }

    private fun adjustMusicVolume(direction: Int) {
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun vibrate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                check(manager.defaultVibrator.hasVibrator()) { "device has no vibrator" }
                manager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(HAPTIC_FEEDBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                check(vibrator.hasVibrator()) { "device has no vibrator" }
                vibrator.vibrate(
                    VibrationEffect.createOneShot(HAPTIC_FEEDBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "haptic feedback unavailable", error)
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val HAPTIC_FEEDBACK_DURATION_MS = 40L

        fun shutdownExecutor(executor: Executor) {
            (executor as? ExecutorService)?.shutdown()
        }
    }

    private data class SystemAction(
        val action: GestureAction,
        val data: GestureData,
        val enqueuedAtElapsed: Long,
        val onDispatched: (() -> Unit)? = null,
        val onRejected: (() -> Unit)? = null,
        val resolveBackOverride: Boolean = true
    )

    private data class LocalAction(
        val action: GestureAction,
        val data: GestureData,
        val onDispatched: (() -> Unit)?,
        val onRejected: (() -> Unit)?
    )
}
