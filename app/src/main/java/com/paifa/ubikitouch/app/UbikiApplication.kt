package com.paifa.ubikitouch.app

import android.app.Application
import android.os.Build
import com.adbcore.AdbCore
import com.adbcore.WifiAutoRecover
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.ubikitouch.accessibility.UbikiPreferences

class UbikiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbCore.init(this)
        FloatingChatBlinkVoiceBridge.registerFullscreenCaptureHost(
            starter = FloatingChatBlinkVoiceOverlayHost::show,
            closer = FloatingChatBlinkVoiceOverlayHost::dismissImmediately
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && UbikiPreferences(this).accessibilityKeepAliveEnabled) {
            WifiAutoRecover.armOnce(this)
        }
    }
}
