package com.adbcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * `Settings.Global` 写"无线调试三件套"的统一入口。
 *
 * 这件事在 [AdbBootReceiver]、[WifiAutoRecover]、[AdbCore.ensureWirelessAdbEnabled]
 * 三处都要做;之前是各自手抄一份,任何字段改动都得三处同步。这里集中。
 *
 * 三件事:
 *   - `adb_wifi_enabled = 1`              打开"无线调试"开关
 *   - `Settings.Global.ADB_ENABLED = 1`   全局 ADB 开关(USB ADB 也走这个)
 *   - `adb_allowed_connection_time = 0`   设备授权永不过期
 *
 * 全部都需要 `WRITE_SECURE_SETTINGS`。host App 没拿到这个权限时方法返回 false,
 * 不抛异常 — 这个权限的获取是 `AdbCore.grantSelfWriteSecureSettings()` 的事,
 * 跟本 helper 解耦。
 */
internal object AdbWirelessSettings {

    /**
     * @return true if WRITE_SECURE_SETTINGS granted **and** all three writes
     *   completed without exception. false otherwise.
     */
    fun enable(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return runCatching {
            val cr = context.contentResolver
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
        }.isSuccess
    }
}
