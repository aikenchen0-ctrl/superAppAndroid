package com.adbcore

import android.content.Context
import com.adbcore.adb.AdbClient
import com.adbcore.adb.AdbMdns
import java.io.File

/**
 * 通过 ADB 通道启动 adbcore_server 进程。
 *
 * 命令行长这样:
 *   /data/.../libadbcore.so --apk=<host.apk> --package=<host.pkg> --authority=<host.adbcore>
 *
 * libadbcore.so 是个 ELF 可执行(伪装成 .so),在 ADB shell 上下文里 fork+execve
 * `app_process` 跑 [ com.adbcore.server.AdbCoreServerMain ]。
 */
internal object ServerStarter {

    fun authority(context: Context): String = "${context.packageName}.adbcore"

    /**
     * The shell command we hand to adbd. It runs libadbcore.so which fork+execs
     * app_process to bring up the server (uid 2000).
     */
    fun command(context: Context): String {
        val starter = File(context.applicationInfo.nativeLibraryDir, "libadbcore.so").absolutePath
        val apk = context.applicationInfo.sourceDir
        return "$starter --apk=$apk --package=${context.packageName} --authority=${authority(context)}"
    }

    /**
     * Launch the server through ADB: connect → shellCommand(command) → close.
     * Does not wait for the server to actually attach — that happens
     * asynchronously when AdbCoreProvider receives the binder.
     */
    suspend fun launchViaAdb(
        context: Context,
        connectPort: Int? = null,
        host: String = LOCAL_HOST,
        discoverTimeoutMs: Long = 3_000,
        preferredPorts: List<Int> = emptyList()
    ): Result<Unit> = runCatching {
        if (connectPort != null) {
            launchOnPort(context, host, connectPort)
            return@runCatching
        }

        var lastPreferredError: Throwable? = null
        for (port in preferredPorts.distinct()) {
            val result = runCatching { launchOnPort(context, host, port) }
            if (result.isSuccess) {
                AdbCoreLog.i("starter: launched via preferred port $port")
                return@runCatching
            }
            lastPreferredError = result.exceptionOrNull()
            AdbCoreLog.i("starter: preferred port $port failed: ${lastPreferredError?.message ?: "unknown"}")
        }

        val realPort = discoverMdnsPort(context, AdbMdns.TLS_CONNECT, discoverTimeoutMs)
            ?: error(buildString {
                append("ADB connect port not found via mDNS within $discoverTimeoutMs ms")
                if (preferredPorts.isNotEmpty()) {
                    append(" (preferred ports tried: ${preferredPorts.distinct().joinToString()}")
                    lastPreferredError?.message?.let { append("; last error: $it") }
                    append(")")
                }
            })
        launchOnPort(context, host, realPort)
    }

    private fun launchOnPort(context: Context, host: String, port: Int) {
        AdbClient(host, port, newAdbKey(context)).use { client ->
            client.connect()
            client.shellCommand(command(context)) { bytes ->
                AdbCoreLog.i("starter: ${String(bytes).trim()}")
            }
        }
    }
}
