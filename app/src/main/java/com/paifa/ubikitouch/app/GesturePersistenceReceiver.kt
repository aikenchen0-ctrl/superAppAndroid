package com.paifa.ubikitouch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.paifa.ubikitouch.accessibility.UbikiGesturePersistence

class GesturePersistenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in RestorationActions) return
        Log.d(TAG, "received persistence restore action=$action")
        UbikiGesturePersistence.handleRecoveryBroadcast(context, action)
    }

    private companion object {
        const val TAG = "GesturePersistence"
        val RestorationActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            UbikiGesturePersistence.ACTION_RECOVER_GESTURE
        )
    }
}
