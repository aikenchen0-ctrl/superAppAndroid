package com.paifa.univerge.app

import android.app.Application
import android.os.Build
import com.adbcore.AdbCore
import com.adbcore.WifiAutoRecover
import com.paifa.univerge.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.univerge.heavydrag.android.HeavyDragRuntime
import com.paifa.univerge.accessibility.HeavyDragRuntimeProvider
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragPolicy
import com.paifa.univerge.app.heavydrag.AispectHeavyTouchBridge
import com.paifa.univerge.accessibility.UniVergePreferences

class UniVergeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HeavyDragRuntimeProvider.install { context ->
            val classifier = AispectHeavyTouchBridge(context)
            HeavyDragRuntime(
                coordinator = HeavyDragCoordinator(
                    policy = HeavyDragPolicy(minHeavyConfidence = 0.55f)
                ),
                classifier = classifier
            )
        }
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
