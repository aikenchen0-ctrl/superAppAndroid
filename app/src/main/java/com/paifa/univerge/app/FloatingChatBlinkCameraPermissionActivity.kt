package com.paifa.univerge.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import com.paifa.univerge.accessibility.FloatingChatBlinkVoiceBridge

/** System-only camera permission gateway. On success it immediately opens the M3 fullscreen overlay. */
class FloatingChatBlinkCameraPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            finishWithOverlay()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            finishWithOverlay()
        } else {
            FloatingChatBlinkVoiceBridge.notifyCaptureClosed()
            finish()
        }
    }

    private fun finishWithOverlay() {
        if (!FloatingChatBlinkVoiceBridge.requestFullscreenCapture()) {
            FloatingChatBlinkVoiceBridge.notifyCaptureClosed()
        }
        finish()
    }

    private companion object {
        const val REQUEST_CAMERA = 9301
    }
}
