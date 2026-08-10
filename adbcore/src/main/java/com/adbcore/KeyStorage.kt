package com.adbcore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.adbcore.adb.AdbKeyStore
import com.adbcore.adb.PreferenceAdbKeyStore

/**
 * Persistent storage for the ADB RSA key.
 *
 * Uses Device Protected Storage so the key remains accessible during the
 * `LOCKED_BOOT_COMPLETED` phase (i.e. before the user unlocks the device),
 * which is required to restore the ADB connection on boot.
 */
internal object KeyStorage {

    private const val PREF_NAME = "adbcore_settings"

    fun getPrefs(context: Context): SharedPreferences {
        val storageContext: Context = context.createDeviceProtectedStorageContext()
        // Wrap to safely handle the rare case where the storage is briefly unavailable.
        val safeContext = object : ContextWrapper(storageContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                return try {
                    super.getSharedPreferences(name, mode)
                } catch (_: IllegalStateException) {
                    EmptySharedPreferences()
                }
            }
        }
        return safeContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getKeyStore(context: Context): AdbKeyStore = PreferenceAdbKeyStore(getPrefs(context))
}
