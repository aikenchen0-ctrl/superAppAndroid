package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.provider.Settings
import android.util.Log

internal class NativeBackGestureTakeoverController(
    private val context: Context
) {
    fun applyTakeover(): NativeBackGestureTakeoverResult {
        val resolver = context.contentResolver
        val requested = nativeBackGestureTakeoverSettings()
        val before = requested.associate { setting ->
            setting.key to Settings.Secure.getString(resolver, setting.key)
        }
        val changedKeys = mutableListOf<String>()
        return try {
            requested.forEach { setting ->
                if (before[setting.key] != setting.value) {
                    Settings.Secure.putString(resolver, setting.key, setting.value)
                    changedKeys += setting.key
                }
            }
            val after = requested.associate { setting ->
                setting.key to Settings.Secure.getString(resolver, setting.key)
            }
            val applied = requested.all { setting -> after[setting.key] == setting.value }
            NativeBackGestureTakeoverResult(
                applied = applied,
                changedKeys = changedKeys,
                before = before,
                after = after
            ).also { result ->
                Log.d(TAG, "native back takeover result=$result")
            }
        } catch (error: SecurityException) {
            Log.w(
                TAG,
                "native back takeover requires android.permission.WRITE_SECURE_SETTINGS; grant it with adb for this debug build",
                error
            )
            NativeBackGestureTakeoverResult(
                applied = false,
                changedKeys = changedKeys,
                before = before,
                after = before,
                errorMessage = error.message
            )
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
    }
}

internal data class NativeBackGestureTakeoverResult(
    val applied: Boolean,
    val changedKeys: List<String>,
    val before: Map<String, String?>,
    val after: Map<String, String?>,
    val errorMessage: String? = null
)

internal data class SecureSettingOverride(
    val key: String,
    val value: String
)

internal fun nativeBackGestureTakeoverSettings(): List<SecureSettingOverride> {
    return listOf(
        SecureSettingOverride(SETTING_BACK_GESTURE_INSET_SCALE_LEFT, SECURE_SETTING_DISABLED),
        SecureSettingOverride(SETTING_BACK_GESTURE_INSET_SCALE_RIGHT, SECURE_SETTING_DISABLED),
        SecureSettingOverride(SETTING_COLOROS_NAVIGATION_MODE, COLOROS_BOTTOM_GESTURE_ONLY)
    )
}

private const val SETTING_BACK_GESTURE_INSET_SCALE_LEFT = "back_gesture_inset_scale_left"
private const val SETTING_BACK_GESTURE_INSET_SCALE_RIGHT = "back_gesture_inset_scale_right"
private const val SETTING_COLOROS_NAVIGATION_MODE = "hide_navigationbar_enable"
private const val SECURE_SETTING_DISABLED = "0"
private const val COLOROS_BOTTOM_GESTURE_ONLY = "2"
