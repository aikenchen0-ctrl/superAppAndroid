package com.adbcore

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.adbcore.adb.AdbClient
import com.adbcore.adb.AdbMdns
import com.adbcore.adb.AdbPairingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Library facade for adbcore.
 *
 *  - First-time setup:
 *      AdbCore.init(this)
 *      AdbCore.pair(pairingPort, pairCode)
 *      AdbCore.grantSelfWriteSecureSettings()
 *      AdbCore.setBootAutoStart(true)
 *
 *  - Daily use (the server is brought up lazily on first call):
 *      AdbCore.exec("pm grant com.foo android.permission.READ_LOGS")
 *      AdbCore.startScript("watchdog", "if ! pidof com.you ...; then am start ...; fi", 10_000)
 *
 *  - On boot, AdbBootReceiver re-enables wireless debugging. The server is
 *    re-launched lazily on the next exec() / startScript() call (or eagerly
 *    if you call ensureServerRunning()).
 */
// "unused" 是因为 :adbcore 是个待发布的 facade 库;这些公开 API 是给
// host App 调用的,demo `:app` 不一定全部都用得到。
@Suppress("unused", "MemberVisibilityCanBePrivate")
object AdbCore {

    @SuppressLint("StaticFieldLeak")
    @Volatile private var appContext: Context? = null

    @Volatile private var taskStore: TaskStore? = null

    /**
     * 外部喂进来的 ADB connect 端口提示。所有需要 mDNS 自动发现的入口
     * (ensureServerRunning / execViaAdb / ServerStarter)在 connectPort = null
     * 时会优先用这个值,避免 mDNS 探测带来的几秒延迟和超时风险。
     *
     * 典型来源:Android 系统"使用配对码配对设备"页面直接显示了 IP:Port,
     * 调用方只需调用 [setKnownConnectPort] 把端口告诉库即可。
     */
    @Volatile private var knownConnectPort: Int? = null

    private const val KEY_PAIR_OK = "pair_ok"
    private const val KEY_KNOWN_CONNECT_PORT = "known_connect_port"

    /** 库内部使用的 taskId,避免跟使用方自定义的任务冲突 */
    private const val INTERNAL_TASK_KEEPALIVE = "__adbcore_keepalive"

    /** assets 中的脚本资源路径。 */
    private const val ASSET_KEEPALIVE = "adbcore_keepalive.sh"
    private const val ASSET_TCPIP_VIA_SETPROP = "adbcore_tcpip_via_setprop.sh"

    /** Default exec timeout (5 seconds). */
    private const val DEFAULT_EXEC_TIMEOUT_MS = 5_000L

    data class TcpipRecoveryStatus(
        val port: Int,
        val reachable: Boolean,
        val persistentPort: Int?
    ) {
        val persistsAcrossBoot: Boolean
            get() = persistentPort == port
    }

    /** Must be called once before any other API (e.g. in Application.onCreate()). */
    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    val app = context.applicationContext
                    appContext = app
                    taskStore = TaskStore(app)
                    knownConnectPort = readPersistedConnectPort(app)
                }
            }
        }
    }

    private fun requireContext(): Context = appContext
        ?: error("AdbCore.init(context) must be called before using AdbCore.")

    private fun requireStore(): TaskStore = taskStore
        ?: error("AdbCore.init(context) must be called before using AdbCore.")

    // =========================================================
    //  Pairing
    // =========================================================

    /**
     * One-time pairing with the device's wireless-debugging endpoint.
     * After success the RSA public key is permanently registered with adbd
     * and subsequent connections need only the connect port.
     */
    suspend fun pair(
        pairingPort: Int,
        pairCode: String,
        host: String = LOCAL_HOST
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            AdbPairingClient(host, pairingPort, pairCode, newAdbKey(requireContext())).use {
                if (!it.start()) error("ADB pairing failed")
            }
            // 只有真正完成 SPAKE2 握手才打标 — RSA key 在 AdbKey() 构造时就会写入
            // SharedPreferences,不能拿它当 "is paired" 的依据。
            KeyStorage.getPrefs(requireContext()).edit { putBoolean(KEY_PAIR_OK, true) }
        }
    }

    // =========================================================
    //  Server lifecycle
    // =========================================================

    /**
     * Returns whether the server binder is currently alive (cheap local ping,
     * no network).
     */
    fun isServerAlive(): Boolean = BinderHolder.isAlive()

    /**
     * Bring the server up if it is not already alive.
     *
     * If [waitMs] > 0, blocks (cooperatively in the coroutine) until the
     * server has registered its binder via AdbCoreProvider, or the timeout
     * elapses.
     */
    suspend fun ensureServerRunning(
        connectPort: Int? = null,
        waitMs: Long = 15_000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isServerAlive()) return@withContext Result.success(Unit)

        val context = requireContext()
        val preferredPorts = if (connectPort == null) recoveryConnectPorts(context) else emptyList()
        val launchResult = ServerStarter.launchViaAdb(
            context = context,
            connectPort = connectPort,
            preferredPorts = preferredPorts
        )
        if (launchResult.isFailure) return@withContext launchResult

        if (waitMs <= 0) return@withContext Result.success(Unit)

        val ok = withTimeoutOrNull(waitMs.milliseconds) {
            while (!isServerAlive()) delay(100.milliseconds)
            true
        } == true

        if (ok) {
            // Server up — restore persisted tasks
            restorePersistedTasksAsync()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Server did not register binder within ${waitMs}ms"))
        }
    }

    /**
     * 软关:把 server 进程干掉、清掉所有保活脚本,**保留** RSA 身份 / 配对状态 /
     * BootReceiver 自启 / `adb_wifi_enabled` 设置。下次只要再调 [startup]
     * (或任何 [exec] / [ensureServerRunning])就能直接拉起来,不用重新配对。
     *
     * 调用顺序刻意如下:
     *   1. `stopAllScripts()` — 让 server 自己先把循环任务停掉、清持久化记录
     *   2. `kill -9 <serverPid> &` — server 自杀;`&` 让命令立刻 detach,
     *      kill 命令 fork 后我们 binder 调用本身可能在中途被 SIGKILL 切断,
     *      catch 掉无视即可
     *   3. 等最多 [killWaitMs] 让 binder linkToDeath callback 触发,
     *      [BinderHolder] 自动 clear。如果超时,主动 clear 一下保险
     *
     * 不会动:RSA 私钥、KEY_PAIR_OK 标志、`adb_wifi_enabled`、BootReceiver 状态、
     * `knownConnectPort`(端口 hint 仍保留方便下次免 mDNS 探测)。
     *
     * 如果你要"硬重置"(撤销配对、关自启),自己做:
     * ```
     * AdbCore.shutdown()
     * AdbCore.setBootAutoStart(false)
     * KeyStorage.getPrefs(ctx).edit().clear().apply()
     * AdbCore.exec("settings put global adb_wifi_enabled 0")  // 但这通常已经死了
     * ```
     */
    suspend fun shutdown(killWaitMs: Long = 2_000): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val service = BinderHolder.get()
            if (service == null) {
                AdbCoreLog.i("shutdown: server not alive, nothing to do")
                return@runCatching
            }

            // 1. 让 server 自己停掉所有循环任务(也会清持久化)
            runCatching { service.stopAllScripts() }
                .onFailure { AdbCoreLog.w("shutdown: stopAllScripts failed", it) }
            // 顺手清掉 TaskStore 里的持久化记录,以防 server 没干净
            runCatching { requireStore().clear() }

            // 2. server 自杀。`&` 让 kill detach,exec 本身被 SIGKILL 截断
            //    的话上面的 runCatching 直接吞掉。
            val pid = runCatching { service.pid }.getOrNull()
            if (pid != null && pid > 0) {
                runCatching { service.exec("kill -9 $pid &", 1_000) }
                AdbCoreLog.i("shutdown: sent kill to server pid=$pid")
            } else {
                AdbCoreLog.w("shutdown: could not get server pid")
            }

            // 3. 等 linkToDeath 触发,最多 killWaitMs
            withTimeoutOrNull(killWaitMs.milliseconds) {
                while (isServerAlive()) delay(80.milliseconds)
            }
            // 保险:如果 binder 死亡通知没来,手动 clear,避免下次 isAlive
            // 以为还活着然后 binder 调用直接 hang
            BinderHolder.clear()
            AdbCoreLog.i("shutdown: complete (server killed, scripts cleared)")
        }
    }

    /**
     * 拉起 ADB 能力。已配对就直接通过 ADB 通道拉 server 起来,**未配对会失败**
     * (此时调用方应该走 [pair])。
     *
     * 等价于 `if (isPaired()) ensureServerRunning()`,只是把"未配对"显式包成
     * 一个错误,UI 可以借此提示用户去走配对。
     */
    suspend fun startup(waitMs: Long = 15_000): Result<Unit> = withContext(Dispatchers.IO) {
        if (isServerAlive()) return@withContext Result.success(Unit)
        if (!isPaired()) {
            return@withContext Result.failure(
                IllegalStateException("device is not paired — run pair() first")
            )
        }
        ensureServerRunning(waitMs = waitMs)
    }

    private fun restorePersistedTasksAsync() {
        val service = BinderHolder.get() ?: return
        val running = runCatching { service.runningScriptIds().toSet() }.getOrDefault(emptySet())
        requireStore().all().forEach { task ->
            if (task.taskId !in running) {
                runCatching {
                    service.startScript(task.taskId, task.script, task.intervalMs)
                    AdbCoreLog.i("restored task ${task.taskId}")
                }
            }
        }
    }

    // =========================================================
    //  Execute commands (3 modes)
    // =========================================================

    /**
     * Recommended path: try server first; if the server is dead, transparently
     * relaunch it via ADB and retry once.
     */
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isServerAlive()) {
            val r = execViaServer(command, timeoutMs)
            if (r.isSuccess) return@withContext r
            // server died between ping and call — fall through to relaunch
        }

        ensureServerRunning(waitMs = 5_000)
            .onFailure { return@withContext Result.failure(it) }

        execViaServer(command, timeoutMs)
    }

    /** Strictly through binder; fails if the server is not alive. */
    suspend fun execViaServer(
        command: String,
        timeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS
    ): Result<String> = withContext(Dispatchers.IO) {
        val service = BinderHolder.get()
            ?: return@withContext Result.failure(IllegalStateException("Server is not alive"))
        runCatching { service.exec(command, timeoutMs) }
    }

    /** Strictly through ADB; never touches the server. Slower but bypasses any binder issues. */
    suspend fun execViaAdb(
        command: String,
        connectPort: Int? = null,
        host: String = LOCAL_HOST,
        discoverTimeoutMs: Long = 3000
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val realPort = connectPort
                ?: knownConnectPort
                ?: discoverMdnsPort(requireContext(), AdbMdns.TLS_CONNECT, discoverTimeoutMs)
                ?: error("Failed to discover ADB connect port via mDNS within $discoverTimeoutMs ms")
            val output = StringBuilder()
            AdbClient(host, realPort, newAdbKey(requireContext())).use { client ->
                client.connect()
                client.shellCommand(command) { bytes -> output.append(String(bytes)) }
            }
            output.toString()
        }
    }

    // =========================================================
    //  Script loops (multi-task, persisted, auto-restored)
    // =========================================================

    /**
     * Start a recurring shell script in the server. Uses the same
     * fork-wait-sleep pattern as `while true; do ...; sleep N; done`,
     * but is managed by the server so stopScript can interrupt it cleanly.
     *
     * @param taskId            unique id; same id second time will return false
     *                          unless you stopScript first
     * @param script            shell snippet passed to `sh -c`
     * @param intervalMs        sleep between iterations (0 = no pause)
     * @param survivesAppDeath  if true (default) the task is persisted and
     *                          re-registered on host-app cold start / boot;
     *                          if false the task lives only as long as the
     *                          server process happens to run
     */
    suspend fun startScript(
        taskId: String,
        script: String,
        intervalMs: Long = 5_000,
        survivesAppDeath: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureServerRunning(waitMs = 5_000)
            .onFailure { return@withContext Result.failure(it) }

        val service = BinderHolder.get()
            ?: return@withContext Result.failure(IllegalStateException("Server is not alive"))

        runCatching {
            val ok = service.startScript(taskId, script, intervalMs)
            if (!ok && taskId in service.runningScriptIds()) {
                // already running with same id — treat as success (idempotent)
            } else if (!ok) {
                error("server rejected startScript")
            }
            requireStore().put(TaskStore.Task(taskId, script, intervalMs, survivesAppDeath))
        }
    }

    suspend fun stopScript(taskId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val service = BinderHolder.get()
        runCatching {
            val stopped = service?.stopScript(taskId) ?: false
            requireStore().remove(taskId)
            stopped
        }
    }

    suspend fun stopAllScripts(): Result<Unit> = withContext(Dispatchers.IO) {
        val service = BinderHolder.get()
        runCatching {
            service?.stopAllScripts()
            requireStore().all().forEach { requireStore().remove(it.taskId) }
        }
    }

    /** Snapshot of task ids currently running in the server. */
    fun runningScriptIds(): Set<String> {
        val service = BinderHolder.get() ?: return emptySet()
        return runCatching { service.runningScriptIds().toSet() }.getOrDefault(emptySet())
    }

    // =========================================================
    //  Convenience
    // =========================================================

    suspend fun grantSelfWriteSecureSettings(): Result<Unit> =
        grantPermission(requireContext().packageName, Manifest.permission.WRITE_SECURE_SETTINGS)

    suspend fun grantPermission(packageName: String, permission: String): Result<Unit> =
        exec("pm grant $packageName $permission").map { /* discard */ }

    suspend fun forceStopApp(packageName: String): Result<Unit> =
        exec("am force-stop $packageName").map { }

    // =========================================================
    //  State / configuration
    // =========================================================

    /**
     * True iff [pair] has previously completed successfully. RSA key生成本身不算 —
     * 必须真正完成一次 SPAKE2 握手才打标。
     */
    fun isPaired(): Boolean = KeyStorage.getPrefs(requireContext()).getBoolean(KEY_PAIR_OK, false)

    /**
     * 提示库:已知 ADB connect 端口是 [port],可直接用,免去 mDNS 探测的几秒延迟。
     * 传 null / 非法值则清除提示。
     */
    fun setKnownConnectPort(port: Int?) {
        val normalized = port?.takeIf { it in 1..65535 }
        knownConnectPort = normalized
        val context = appContext ?: return
        KeyStorage.getPrefs(context).edit {
            if (normalized == null) {
                remove(KEY_KNOWN_CONNECT_PORT)
            } else {
                putInt(KEY_KNOWN_CONNECT_PORT, normalized)
            }
        }
    }

    fun getKnownConnectPort(): Int? = knownConnectPort

    fun getPersistedConnectPort(): Int? = readPersistedConnectPort(requireContext())

    fun getOfflineRecoveryCandidatePorts(): List<Int> = recoveryConnectPorts(requireContext())

    // =========================================================
    //  Unified keep-alive (wireless debug + tcpip + host process)
    // =========================================================

    /**
     * 启动 server 端的**统一保活脚本**,三件事一并打理(详见
     * `:adbcore/src/main/assets/adbcore_keepalive.sh`):
     *
     *   1. 持续把 `adb_wifi_enabled` 写回 1 + `adb_allowed_connection_time` 写 0,
     *      对抗用户手动关 / ROM 节能策略关
     *   2. 持续把 `service.adb.tcp.port` 写回 [tcpPort],best-effort —
     *      OPPO 等 ROM 上 SELinux 拒写,失败无害
     *   3. 周期 `am startservice` 拉起 host App 的 [AdbKeepAliveService] —
     *      无界面,只把进程拉到运行态,不切前台不显示通知
     *
     * **设计要点**:
     *   - 脚本本体在 assets 目录,**开发者可直接编辑 `.sh` 文件调整保活策略**,
     *     无需改 Kotlin 代码。host App 启动这个保活时把脚本读出来 + 模板替换 +
     *     发给 server 跑
     *   - 所有命令都包了 `2>/dev/null`,脚本最后 `exit 0`,server 永远不会
     *     因为单条命令失败进入异常重试节奏
     *
     * **零配置**:[AdbKeepAliveService] 和 manifest 声明都在 `:adbcore` 里,
     * manifest merger 自动合并到 host App。调用方只需一行 [armKeepAlive]。
     *
     * @param context 任意 context(取其 `packageName`)
     * @param intervalMs 两次执行间的间隔。默认 5 秒。
     * @param tcpPort 写入 `service.adb.tcp.port` 的端口,默认 [DEFAULT_TCPIP_PORT]。
     */
    suspend fun armKeepAlive(
        context: Context,
        intervalMs: Long = 5_000,
        tcpPort: Int = DEFAULT_TCPIP_PORT
    ): Result<Unit> {
        require(tcpPort in 1..65535) { "tcpPort out of range: $tcpPort" }
        val script = AssetScripts.load(
            context, ASSET_KEEPALIVE,
            "HOST_PKG" to context.packageName,
            "HOST_SERVICE" to AdbKeepAliveService::class.java.name,
            "TCP_PORT" to tcpPort.toString()
        )
        return startScript(INTERNAL_TASK_KEEPALIVE, script, intervalMs)
    }

    /** 停止 [armKeepAlive] 注册的循环任务。 */
    suspend fun disarmKeepAlive(): Result<Boolean> =
        stopScript(INTERNAL_TASK_KEEPALIVE)

    // =========================================================
    //  TCP-IP mode adbd (best-effort)
    // =========================================================

    /**
     * 尝试让 adbd 切到 "USB + TCP" 模式,监听 `0.0.0.0:port`。这样**断 WiFi 后**
     * 仍能从 `127.0.0.1:port` 连到 adbd,等同于摆脱了对 WLAN 接口的依赖。
     *
     * 两种实现路径,按顺序尝试:
     *
     *   **路径 A — ADB 协议 service**(优先,更可能成功):
     *      直接通过 ADB 协议给 adbd 发一个 `tcpip:<port>` service 请求 ——
     *      就是电脑端 `adb tcpip <port>` 的实现方式。adbd 收到后用**自己的**
     *      selinux domain 调 setprop + 重启自己,绕过 SHELL uid 的限制。
     *      在限制了 SHELL 写 system property 的定制 ROM 上,这条路成功率更高。
     *
     *   **路径 B — SHELL 端 setprop**(fallback):
     *      `setprop service.adb.tcp.port <port> && stop adbd && start adbd`。
     *      大多数 ROM 会因 SELinux 拒绝 SHELL 写该 property,但 AOSP 原生
     *      或部分轻量 ROM 上仍可成功。
     *
     * 验证方式:adbd 重启后等 2s,再用 [AdbClient] 连 `127.0.0.1:port` 看
     * TCP 握手 + ADB connect 是否成功。成功才算真的生效。
     *
     * 行为约定:
     *   - 成功 → 把 [port] 喂给 [setKnownConnectPort],之后所有 mDNS 探测的
     *           入口会优先用这个固定端口,跳过 mDNS。返回 `Result.success(true)`。
     *   - 两条路径都失败 → 返回 `Result.success(false)`,**不抛异常**。
     *           主链路(无线调试 + mDNS)完全不受影响。
     */
    suspend fun tryEnableTcpipAdb(port: Int = DEFAULT_TCPIP_PORT): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(port in 1..65535) { "port out of range: $port" }

                // 已经设过相同端口的快速路径
                if (verifyTcpipPortReachable(port)) {
                    setKnownConnectPort(port)
                    // 即使 quick-path 命中,也补一刀 persist.* — 想让端口跨重启也保留
                    runCatching { exec("setprop persist.adb.tcp.port $port", timeoutMs = 3_000) }
                    return@runCatching true
                }

                // ===== 路径 A: ADB 协议直接请求 tcpip: service =====
                // adbd 用自己的 selinux 域改 service.adb.tcp.port + 重启自己。
                // 注意:adbd 内部代码只写 service.*,不写 persist.*,所以这条路径成功后
                // 重启会丢失 TCP 模式 — 我们后面再单独补 persist.* 试图持久化。
                val protocolOk = enableTcpipViaAdbProtocol(port)

                // ===== 路径 B: SHELL setprop service.* fallback =====
                // 脚本在 assets/adbcore_tcpip_via_setprop.sh,内联命令外置便于编辑。
                val ok = if (protocolOk) true else {
                    val script = AssetScripts.load(
                        requireContext(), ASSET_TCPIP_VIA_SETPROP,
                        "TCP_PORT" to port.toString()
                    )
                    val setpropR = exec(script, timeoutMs = 5_000)
                    if (setpropR.isFailure) false
                    else {
                        delay(2.seconds)
                        verifyTcpipPortReachable(port)
                    }
                }

                // ===== 路径 C(无论 A/B 是否成功):尝试写 persist.adb.tcp.port =====
                // 让 adbd **重启后**也保持 TCP 模式监听。
                //   - 大多数定制 ROM 会 SELinux 拒 SHELL 写 persist.*
                //   - 部分 AOSP-near ROM 接受
                //   - 部分 ROM 即使 persist.* 写成功了,新版 adbd 启动时也不读它
                // 都是 best-effort,失败无害。
                runCatching {
                    exec("setprop persist.adb.tcp.port $port", timeoutMs = 3_000)
                }

                if (ok) setKnownConnectPort(port)
                ok
            }
        }

    /**
     * 用现成的 ADB 通道发 `tcpip:<port>` service 请求。adbd 收到后会:
     *   - 以自己的 sepolicy 权限调 setprop service.adb.tcp.port=port
     *   - 重启自己以应用新配置
     *
     * 当前连接会被 adbd 关闭,这是预期的(协议中正常路径),所以 IOException
     * 被 catch 吞掉。重启后等 2s 让 adbd 重新监听,然后用 [verifyTcpipPortReachable]
     * 真正连一次 `127.0.0.1:port` 验证。
     */
    private suspend fun enableTcpipViaAdbProtocol(port: Int): Boolean = withContext(Dispatchers.IO) {
        val context = requireContext()
        val knownOrDiscoveredPort = knownConnectPort
            ?: discoverMdnsPort(context, AdbMdns.TLS_CONNECT, 3_000)
            ?: return@withContext false

        runCatching {
            AdbClient(LOCAL_HOST, knownOrDiscoveredPort, newAdbKey(context)).use { client ->
                client.connect()
                runCatching { client.openService("tcpip:$port", null) }
            }
        }

        delay(2.seconds)  // adbd 重启 + 重新监听需要时间
        verifyTcpipPortReachable(port)
    }

    /**
     * 真去连一次 `127.0.0.1:port` 看 ADB 协议握手是否成功。这是验证
     * "adbd 是否真在该端口监听"的最可靠方式。
     */
    private suspend fun verifyTcpipPortReachable(port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            AdbClient(LOCAL_HOST, port, newAdbKey(requireContext())).use { client ->
                client.connect()
                true
            }
        }.getOrDefault(false)
    }

    suspend fun checkTcpipRecovery(port: Int = DEFAULT_TCPIP_PORT): Result<TcpipRecoveryStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(port in 1..65535) { "port out of range: $port" }
                val reachable = if (isPaired()) verifyTcpipPortReachable(port) else false
                val persistentPort = readPersistentTcpipPort(port, reachable)
                TcpipRecoveryStatus(port, reachable, persistentPort)
            }
        }

    private suspend fun readPersistentTcpipPort(port: Int, reachable: Boolean): Int? {
        val raw = when {
            isServerAlive() -> execViaServer("getprop persist.adb.tcp.port", timeoutMs = 3_000).getOrNull()
            reachable -> execViaAdb(
                command = "getprop persist.adb.tcp.port",
                connectPort = port,
                discoverTimeoutMs = 500
            ).getOrNull()
            else -> null
        }
        return raw?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    private fun recoveryConnectPorts(context: Context): List<Int> =
        listOfNotNull(
            knownConnectPort,
            readPersistedConnectPort(context),
            DEFAULT_TCPIP_PORT
        ).filter { it in 1..65535 }.distinct()

    private fun readPersistedConnectPort(context: Context): Int? =
        KeyStorage.getPrefs(context)
            .getInt(KEY_KNOWN_CONNECT_PORT, -1)
            .takeIf { it in 1..65535 }

    /** 默认的 tcpip 端口。避开 5555(很多 ROM / 工具会占用)。 */
    const val DEFAULT_TCPIP_PORT: Int = 6088

    fun setBootAutoStart(enabled: Boolean) {
        val context = requireContext()
        val pm = context.packageManager
        val component = ComponentName(context, AdbBootReceiver::class.java)
        pm.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Right after pairing, if WRITE_SECURE_SETTINGS is already granted, flip
     * wireless-debugging on so we don't depend on the user staying on the
     * system Pair screen.
     *
     * @return true if granted and writes succeeded.
     */
    fun ensureWirelessAdbEnabled(): Boolean = AdbWirelessSettings.enable(requireContext())

    // =========================================================
    //  mDNS discovery (still useful for non-boot scenarios)
    // =========================================================

    suspend fun discoverConnectPort(timeoutMs: Long = 3_000): Int? =
        discoverMdnsPort(requireContext(), AdbMdns.TLS_CONNECT, timeoutMs)

    suspend fun discoverPairingPort(timeoutMs: Long = 3_000): Int? =
        discoverMdnsPort(requireContext(), AdbMdns.TLS_PAIRING, timeoutMs)
}
