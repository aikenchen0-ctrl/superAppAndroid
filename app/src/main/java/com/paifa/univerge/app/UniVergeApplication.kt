package com.paifa.univerge.app

import android.app.Application
import android.os.Build
import com.adbcore.AdbCore
import com.adbcore.WifiAutoRecover
import com.paifa.univerge.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.univerge.accessibility.UniVergePreferences

class UniVergeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbCore.init(this)
        FloatingChatBlinkVoiceBridge.registerFullscreenCaptureHost(
            starter = FloatingChatBlinkVoiceOverlayHost::show,
            closer = FloatingChatBlinkVoiceOverlayHost::dismissImmediately
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && UniVergePreferences(this).accessibilityKeepAliveEnabled) {
            WifiAutoRecover.armOnce(this)
        }
    }
}
