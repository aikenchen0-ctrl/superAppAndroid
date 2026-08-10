package com.adbcore

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 进程保活用的占位 Service。**没有任何 UI,不会切前台,也不显示通知**。
 *
 * 为什么需要这个:
 *   - 用户清后台 / 系统优化把 host App 进程清掉之后,业务断流(LiveData / coroutine
 *     都死了),即便 server 进程还活着也得不到反馈。
 *   - 我们之前用的 `am start <Activity>` 能把进程拉起,但代价是 Activity 进前台
 *     用户能看到 — 不是想要的行为。
 *   - 改用 `am startservice <Service>` 拉起这个 Service,**只起进程不起界面**。
 *
 * Android 12+ 普通 App 后台 startService 受限,但 server 是 ADB shell uid (2000),
 * 启动 Service 不受 `BackgroundServiceStartNotAllowedException` 约束 —
 * Shizuku 用的是同一手法。
 *
 * Manifest merger 会把这个 Service 自动合并到使用 :adbcore 的 host App,
 * host 端无需手动声明,也不用知道 component 名。
 */
class AdbKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 啥都不做。Service 被拉起的副作用是 host App 进程被拉到运行状态,这就是我们
     * 唯一要的目的。返回 [START_STICKY] 让 AMS 在被杀后也尝试自动重启,作为
     * 第二道保险(但不依赖 — 主路径仍是 server 周期 `am startservice`)。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
