package com.paifa.univerge.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import com.paifa.univerge.accessibility.FloatingChatMediaPickerBridge

/**
 * 仅承担系统相机权限申请，获准后立即交给全屏悬浮拍照页。
 * 测试流程：首次点击右侧“拍摄照片”允许权限，确认不会显示 Activity 或底部取消对话框。
 */
class FloatingChatPhotoCameraPermissionActivity : Activity() {
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
            FloatingChatMediaPickerBridge.notifyPickerClosed()
            finish()
        }
    }

    private fun finishWithOverlay() {
        if (!FloatingChatPhotoOverlayHost.show()) {
            FloatingChatMediaPickerBridge.notifyPickerClosed()
        }
        finish()
    }

    private companion object {
        const val REQUEST_CAMERA = 9401
    }
}
