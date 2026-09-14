package com.paifa.univerge.accessibility

import android.content.Context
import com.paifa.univerge.heavydrag.android.HeavyDragRuntime

/**
 * 注入重触运行时的应用边界。
 *
 * Accessibility 只依赖通用 [HeavyDragRuntime]，具体分类器由宿主应用通过
 * [install] 注入。租约跟随 Compose 根生命周期，最后一个租约释放后运行时关闭。
 */
object HeavyDragRuntimeProvider {
    fun interface Factory {
        fun create(context: Context): HeavyDragRuntime
    }

    /** 当前工厂的可撤销注册句柄；关闭是幂等的。 */
    class Registration internal constructor(
        private val token: Long
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                val current = installed
                if (current?.token == token) {
                    installed = null
                    closeIfUnused(current)
                }
            }
        }
    }

    /** 跟随一个 Compose/View 宿主的运行时租约；关闭是幂等的。 */
    class Lease internal constructor(
        val runtime: HeavyDragRuntime,
        private val release: () -> Unit
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            synchronized(this) {
                if (closed) return
                closed = true
            }
            release()
        }
    }

    private val lock = Any()
    private var nextToken = 1L
    private var installed: Entry? = null

    /** 安装或替换宿主提供的工厂；旧注册只影响自己的工厂。 */
    fun install(factory: Factory): Registration {
        synchronized(lock) {
            val previous = installed
            if (previous != null) {
                installed = null
                closeIfUnused(previous)
            }
            val entry = Entry(token = nextToken++, factory = factory)
            installed = entry
            return Registration(entry.token)
        }
    }

    /** 没有宿主工厂时返回 null，保证 accessibility 可以无运行时继续工作。 */
    fun acquire(context: Context): Lease? {
        synchronized(lock) {
            val entry = installed ?: return null
            val runtime = entry.runtime ?: runCatching {
                entry.factory.create(context.applicationContext)
            }.getOrNull()?.also { created ->
                entry.runtime = created
            } ?: return null
            entry.leases += 1
            return Lease(runtime) {
                synchronized(lock) {
                    if (entry.leases > 0) entry.leases -= 1
                    closeIfUnused(entry)
                }
            }
        }
    }

    private fun closeIfUnused(entry: Entry) {
        if (entry.leases != 0) return
        val runtime = entry.runtime ?: return
        entry.runtime = null
        runtime.close()
    }

    private class Entry(
        val token: Long,
        val factory: Factory,
        var runtime: HeavyDragRuntime? = null,
        var leases: Int = 0
    )
}
