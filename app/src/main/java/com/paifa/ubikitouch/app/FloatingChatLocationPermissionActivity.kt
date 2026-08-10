package com.paifa.ubikitouch.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import com.paifa.ubikitouch.accessibility.FloatingChatLocationPermissionBridge

class FloatingChatLocationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasLocationPermission()) {
            FloatingChatLocationPermissionBridge.deliverLocationPermission(true)
            finish()
            return
        }
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            FloatingChatLocationPermissionBridge.deliverLocationPermission(
                grantResults.any { result -> result == PackageManager.PERMISSION_GRANTED }
            )
        }
        finish()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REQUEST_LOCATION = 9301
    }
}
