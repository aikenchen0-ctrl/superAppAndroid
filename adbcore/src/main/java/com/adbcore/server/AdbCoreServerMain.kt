@file:JvmName("AdbCoreServerMain")

package com.adbcore.server

import android.ddm.DdmHandleAppName
import android.os.Looper
import android.util.Log
import kotlin.system.exitProcess

/**
 * Entry point of `adbcore_server`.
 *
 * Started by libadbcore.so (via app_process). Runs as uid 2000 (adb shell)
 * with init as its parent process, fully detached from the host app process.
 *
 * Workflow:
 *   1. Set the cosmetic process name (visible in `ps`).
 *   2. Read the host package name + provider authority from the system
 *      properties injected by starter.cpp:
 *        -Dadbcore.host.package=<host pkg>
 *        -Dadbcore.host.authority=<authority>
 *   3. Create the binder, send it to the host app's ContentProvider.
 *   4. Looper.loop() — keep the process alive servicing binder calls.
 */
fun main(args: Array<String>) {
    DdmHandleAppName.setAppName("adbcore_server", 0)

    Log.i(TAG, "adbcore_server pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()}")
    Log.i(TAG, "args=${args.toList()}")

    // adbcore_server 是从 app_process 启动的裸进程,没有 ActivityThread 帮忙
    // 准备主 Looper —— 必须自己显式 prepareMainLooper,这样 binder 才能引用主线程。
    // Java 端标记 deprecated 是给普通 App 用的(已经有主 Looper),裸进程入口
    // 不在这条规则之内,Shizuku 也是同样用法。
    @Suppress("DEPRECATION")
    Looper.prepareMainLooper()

    val hostPackage = System.getProperty("adbcore.host.package").orEmpty()
    var hostAuthority = System.getProperty("adbcore.host.authority").orEmpty()
    if (hostAuthority.isEmpty() && hostPackage.isNotEmpty()) {
        hostAuthority = "$hostPackage.adbcore"
    }

    Log.i(TAG, "hostPackage=$hostPackage hostAuthority=$hostAuthority")

    val binder = ServerBinderImpl()
    val sent = SendBinderHelper.send(hostAuthority, binder)
    if (!sent) {
        Log.e(TAG, "Failed to send binder to host. Will keep retrying periodically below.")
    }

    // 周期性重发 binder。这是为了应对一种典型场景:
    //   - server 进程从启动那一刻把 binder 推给 host App 的 ContentProvider
    //   - 但 push 是 ONE-SHOT,后面 server 不会自动再推
    //   - 一旦 host App 进程被系统/用户杀掉(LMK / 清后台 / 用户主动 kill),
    //     重启后的 host App 是 NEW process,新的 BinderHolder 是空的
    //   - 此时 host App 调 exec 会判 isServerAlive=false,试图走 ADB 通道
    //     重新拉 server,然而无 WiFi 时这条路是死的
    //
    // 让 server 隔一段时间就再调一次 IContentProvider.call("sendBinder", ...)
    // (幂等),host App 端的 ContentProvider 收到就更新 BinderHolder,App
    // 立即恢复使用 — **完全不依赖 ADB 通道**,无 WiFi 也能用。
    //
    // 节奏:稳定时 5s 一次;失败后 3s 重试(可能是 host 进程刚死正在重启)。
    startPeriodicBinderResend(hostAuthority, binder)

    Log.i(TAG, "entering Looper.loop() — server is up")
    Looper.loop()

    Log.i(TAG, "Looper exited; server going down")
    exitProcess(0)
}

/**
 * 后台 daemon 线程,周期性重新把 binder 推到 host App 的 ContentProvider。
 * 让 App 进程被杀重启后能在最多 5 秒内自动恢复,无需走 ADB 通道。
 */
private fun startPeriodicBinderResend(authority: String, binder: android.os.IBinder) {
    Thread {
        var lastOk = true
        while (!Thread.currentThread().isInterrupted) {
            try {
                // 上一轮成功就 5 秒一次;上一轮失败就 3 秒重试(可能 host 进程刚死正在重启)
                Thread.sleep(if (lastOk) 5_000L else 3_000L)
                lastOk = SendBinderHelper.send(authority, binder)
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                Log.w(TAG, "periodic binder resend errored", t)
                lastOk = false
            }
        }
    }.apply {
        isDaemon = true
        name = "adbcore-binder-resend"
        start()
    }
}

private const val TAG = "AdbCoreServer"
