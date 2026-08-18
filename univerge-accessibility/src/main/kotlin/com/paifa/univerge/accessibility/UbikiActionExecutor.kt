package com.paifa.univerge.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData

internal class UbikiActionExecutor(
    private val service: AccessibilityService,
    private val isHapticFeedbackEnabled: () -> Boolean,
    private val floatingChatOverlayController: FloatingChatOverlayController? = null,
    private val videoDemoOverlayController: VideoDemoOverlayController? = null
) {
    fun execute(action: GestureAction, data: GestureData) {
        if (action == GestureAction.None) return
        Log.d(TAG, "execute action=${action.id} start=(${data.startX},${data.startY}) end=(${data.endX},${data.endY})")
        performHapticFeedback()

        when (action) {
            GestureAction.Back -> {
                if (videoDemoOverlayController?.dismissIfShowing() == true) {
                    return
                }
                if (floatingChatOverlayController?.dismissPreviewOrSheet() != true) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
            GestureAction.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            GestureAction.Recents -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.Notifications -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QuickSettings -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.Screenshot -> takeScreenshot()
            GestureAction.LockScreen -> lockScreen()
            GestureAction.VolumeUp -> adjustMusicVolume(AudioManager.ADJUST_RAISE)
            GestureAction.VolumeDown -> adjustMusicVolume(AudioManager.ADJUST_LOWER)
            GestureAction.ExpandFloatingChat -> floatingChatOverlayController?.expand()
            GestureAction.CollapseFloatingChat -> floatingChatOverlayController?.collapse()
            GestureAction.PlayVideo -> videoDemoOverlayController?.showOrToggle()
            is GestureAction.LaunchApp -> launchApp(action.packageName)
            GestureAction.None -> Unit
        }
    }

    fun performHapticFeedback() {
        if (isHapticFeedbackEnabled()) vibrate()
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun launchApp(packageName: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(service, R.string.ubiki_launch_app_failed, Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
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
    }
}
