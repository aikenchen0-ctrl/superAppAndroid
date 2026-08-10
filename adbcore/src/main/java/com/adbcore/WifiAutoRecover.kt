package com.adbcore

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 持续监听 WIFI 上线事件,首次能成功拉起 server 后自动取消注册。
 *
 * 行为细节:
 *   - 调用 [armOnce] 后注册一个 [ConnectivityManager.NetworkCallback]
 *     (TRANSPORT_WIFI),同一个 Application 进程内部幂等。
 *   - WIFI 一旦 [ onAvailable ],启动一个**可取消的协程**做 ADB server 拉起尝试,
 *     用指数退避 5s → 10s → 20s → ... 5 min 封顶,**直到成功**为止。
 *   - 协程在 WIFI 断开 ([ onLost ]) 时立即取消(没 WIFI 重试无意义),WIFI 重连后
 *     [ onAvailable ] 再次触发,重新启动一个新的尝试协程。
 *   - 只在最终成功时调用 [disarm]。注册失败 / 调用方主动调用 [disarm] 也会清理。
 *
 * 使用场景:重启后 [AdbBootReceiver] 在 LOCKED_BOOT_COMPLETED 时拉 server 通常失败
 * (用户没解锁、WIFI 没连、adbd-wireless 没监听),回退后由这个工具接管。哪怕用户两小时
 * 后才连 WIFI,只要 Application 进程还在,这里就能立刻接管。
 *
 * 注意:Application 进程被系统杀掉时 callback 会自动注销,这是 Android 行为不可避免。
 * 但只要 [AdbCore.armKeepAlive] 注册的统一保活脚本还在 server 进程里跑,该脚本会
 * 用 `am startservice` 把 host 进程拉回来,Application 重建时 [armOnce] 会被
 * 再调用一次,链路自愈。
 */
object WifiAutoRecover {

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var attemptJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val INITIAL_RETRY_DELAY_MS = 5_000L
    private const val MAX_RETRY_DELAY_MS = 5 * 60_000L  // 5 minutes

    @Synchronized
    fun armOnce(context: Context) {
        if (callback != null) {
            AdbCoreLog.i("WifiAutoRecover: already armed, skip")
            return
        }
        if (!AdbCore.isPaired()) {
            AdbCoreLog.i("WifiAutoRecover: skip (device not paired)")
            return
        }
        if (AdbCore.isServerAlive()) {
            AdbCoreLog.i("WifiAutoRecover: skip (server already alive)")
            return
        }

        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: run {
            AdbCoreLog.w("WifiAutoRecover: ConnectivityManager unavailable")
            return
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AdbCoreLog.i("WifiAutoRecover: WiFi available")
                // 某些 ROM 在 WiFi 断或重启时会把 adb_wifi_enabled 置 0。
                // 这里趁机把它重写为 1 + 永不过期 — 我们已自给 WRITE_SECURE_SETTINGS,
                // 这一步是免费且关键的兜底,失败也不影响后面 startAttempt。
                if (AdbWirelessSettings.enable(appContext)) {
                    AdbCoreLog.i("WifiAutoRecover: re-armed adb_wifi_enabled=1")
                }
                startAttempt(appContext)
            }

            override fun onLost(network: Network) {
                AdbCoreLog.i("WifiAutoRecover: WiFi lost; cancelling current recover attempt")
                attemptJob?.cancel()
                attemptJob = null
            }
        }

        try {
            cm.registerNetworkCallback(request, cb)
            callback = cb
            AdbCoreLog.i("WifiAutoRecover: armed (persistent, exponential backoff up to 5min)")
        } catch (t: Throwable) {
            AdbCoreLog.e("WifiAutoRecover: registerNetworkCallback failed", t)
        }
    }

    /**
     * 启动一个新的尝试协程。如果上一个还在跑会先 cancel。失败时按指数退避无限重试,
     * 直到成功(disarm)或者协程被外部 cancel(WIFI 断 / 进程退出 / 主动 disarm)。
     */
    private fun startAttempt(context: Context) {
        attemptJob?.cancel()
        attemptJob = scope.launch {
            var nextDelayMs = INITIAL_RETRY_DELAY_MS
            var attempt = 0

            while (isActive) {
                attempt++

                // server 在尝试前可能已经被别的路径(用户主动 exec、watchdog)拉起来了
                if (AdbCore.isServerAlive()) {
                    AdbCoreLog.i("WifiAutoRecover: server became alive externally, disarm")
                    disarm(context)
                    return@launch
                }

                val r = AdbCore.ensureServerRunning(waitMs = 10_000)
                if (r.isSuccess) {
                    AdbCoreLog.i("WifiAutoRecover: server up on attempt $attempt, disarm")
                    disarm(context)
                    return@launch
                }

                val msg = r.exceptionOrNull()?.message ?: "unknown"
                val nextDelaySec = nextDelayMs / 1000
                AdbCoreLog.i("WifiAutoRecover: attempt $attempt failed: $msg; retry in ${nextDelaySec}s")

                delay(nextDelayMs.milliseconds)
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    @Synchronized
    fun disarm(context: Context) {
        attemptJob?.cancel()
        attemptJob = null

        val cb = callback ?: return
        runCatching {
            context.applicationContext
                .getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(cb)
        }
        callback = null
        AdbCoreLog.i("WifiAutoRecover: disarmed")
    }
}
