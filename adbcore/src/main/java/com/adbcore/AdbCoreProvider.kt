package com.adbcore

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Receives the server binder sent by adbcore_server. Must be declared in the
 * host app's manifest (via the library's manifest merge) with
 * authority = "${applicationId}.adbcore" and exported = true.
 *
 * The "exported = true" is required because the call is initiated by the
 * server process running as uid SHELL (2000), which is a different uid from
 * the host app.
 */
class AdbCoreProvider : ContentProvider() {

    companion object {
        const val METHOD_SEND_BINDER = "sendBinder"
        const val EXTRA_BINDER = "binder"
    }

    override fun onCreate(): Boolean {
        AdbCoreLog.i("AdbCoreProvider onCreate")
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // server 默认 5s 一次刷新 binder,这里走 D 级,避免淹没 logcat
        AdbCoreLog.d("AdbCoreProvider call(method=$method, arg=$arg, extras has binder=${extras?.containsKey(EXTRA_BINDER)})")
        if (method == METHOD_SEND_BINDER && extras != null) {
            extras.classLoader = javaClass.classLoader
            val binder = extras.getBinder(EXTRA_BINDER)
            if (binder == null) {
                AdbCoreLog.w("AdbCoreProvider: extras has no binder!")
            }
            BinderHolder.set(binder)
            return Bundle()
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
