/*
 * 功能概览：把“返回”边缘手势的拖动进度转换成一次性提交信号。
 * Kotlin 语法提示：`class` 声明一个类；`private set` 表示属，但只能在类内部性可以被外部读取修改。
 */
package com.paifa.ubikitouch.core.gesture

// 短手势可以按距离提交，也可以在速度足够快时提前提交，避免抬手后才触发动作。
class BackGestureCommitController {
    var committed: Boolean = false
        private set
    private var longCommitted: Boolean = false

    // 每次 MOVE/UP 都会调用；返回 true 表示本次手势刚刚完成提交。
    fun update(progress: BackGestureProgress, velocityPxPerSecond: Float = 0f): Boolean {
        if (progress.longCommitted) {
            if (longCommitted) return false
            longCommitted = true
            committed = true
            return true
        }
        if (!committed && shouldCommitShort(progress, velocityPxPerSecond)) {
            committed = true
            return true
        }
        return false
    }

    // 一次手势结束后清理状态，下一次手势从未提交开始。
    fun reset() {
        committed = false
        longCommitted = false
    }

    private fun shouldCommitShort(progress: BackGestureProgress, velocityPxPerSecond: Float): Boolean {
        if (progress.committed) return true
        val fastEnough = velocityPxPerSecond >= QUICK_COMMIT_VELOCITY_PX_PER_SECOND
        val pulledFarEnough = progress.progress >= QUICK_COMMIT_MIN_PROGRESS
        return fastEnough && pulledFarEnough
    }

    private companion object {
        const val QUICK_COMMIT_VELOCITY_PX_PER_SECOND = 520f
        const val QUICK_COMMIT_MIN_PROGRESS = 0.24f
    }
}
