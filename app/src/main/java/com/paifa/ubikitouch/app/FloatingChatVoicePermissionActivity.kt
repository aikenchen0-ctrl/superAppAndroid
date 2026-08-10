package com.paifa.ubikitouch.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import com.paifa.ubikitouch.accessibility.FloatingChatVoicePermissionBridge

class FloatingChatVoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            FloatingChatVoicePermissionBridge.deliverRecordAudioPermission(true)
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            FloatingChatVoicePermissionBridge.deliverRecordAudioPermission(
                grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            )
        }
        finish()
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 9201
    }
}
