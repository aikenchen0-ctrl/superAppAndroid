package com.adbcore

import android.content.Context
import com.adbcore.adb.AdbKey
import com.adbcore.adb.AdbMdns
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * 库内部共享的常量和 helper。这里集中后,各模块只引入这一个文件,
 * 避免常量字符串在多处同步漂移、helper 在多处复制粘贴。
 *
 * 全部 internal,对调用方不可见。
 */
internal const val ADB_KEY_NAME = "adbcore"
internal const val LOCAL_HOST = "127.0.0.1"

/**
 * 构造跟 KeyStore 绑定的 [AdbKey]。所有走 ADB 协议的入口(配对、连接、TCP/IP
 * 切换)都用这个 helper 拿同一把 RSA。
 */
internal fun newAdbKey(context: Context): AdbKey =
    AdbKey(KeyStorage.getKeyStore(context), ADB_KEY_NAME)

/**
 * mDNS 端口发现:订阅 [type] 服务([AdbMdns.TLS_CONNECT] / [AdbMdns.TLS_PAIRING])
 * 的端口广告,拿到 > 0 的端口就立即返回。
 *
 * 超时返回 null,**不抛异常**。订阅会在协程被 cancel 或函数返回前 `mdns.stop()`。
 */
internal suspend fun discoverMdnsPort(
    context: Context,
    type: String,
    timeoutMs: Long = 3_000
): Int? = withTimeoutOrNull(timeoutMs.milliseconds) {
    suspendCancellableCoroutine { cont: CancellableContinuation<Int> ->
        val mdns = AdbMdns(context, type) { p ->
            if (p > 0 && cont.isActive) cont.resume(p)
        }
        mdns.start()
        cont.invokeOnCancellation { mdns.stop() }
    }
}
