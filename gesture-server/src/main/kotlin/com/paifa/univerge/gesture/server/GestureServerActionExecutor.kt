package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.paifa.univerge.core.gesture.GestureEventLedger
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData

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
    private val service: AccessibilityService
) {
    private val terminalLedger = GestureEventLedger()

    fun execute(action: GestureAction, data: GestureData): Boolean {
        if (!terminalLedger.acceptGestureId(data.gestureId)) return false
        runCatching {
            when (action) {
                GestureAction.None -> Unit
                GestureAction.Back -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                GestureAction.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                GestureAction.Recents -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                GestureAction.Notifications -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                GestureAction.QuickSettings -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                GestureAction.Screenshot -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
                GestureAction.LockScreen -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
                GestureAction.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
                GestureAction.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
                is GestureAction.LaunchApp -> launch(action.packageName)
                GestureAction.ExpandFloatingChat,
                GestureAction.CollapseFloatingChat,
                GestureAction.PlayVideo -> forwardToMainProcess(action, data)
            }
        }.onFailure { error ->
            Log.w(TAG, "gesture action failed action=${action.id} id=${data.gestureId}", error)
        }
        return true
    }

    private fun adjustVolume(direction: Int) {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun launch(packageName: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

    private fun forwardToMainProcess(action: GestureAction, data: GestureData) {
        service.sendBroadcast(
            Intent(ACTION_MAIN_PROCESS_GESTURE)
                .setPackage(service.packageName)
                .putExtra(EXTRA_ACTION_ID, action.id)
                .putExtra(EXTRA_GESTURE_ID, data.gestureId)
        )
    }

    private companion object {
        const val TAG = "GestureServer"
    }
}

const val ACTION_MAIN_PROCESS_GESTURE = "com.paifa.univerge.action.GESTURE_SERVER_ACTION"
const val EXTRA_ACTION_ID = "gesture_action_id"
const val EXTRA_GESTURE_ID = "gesture_id"
