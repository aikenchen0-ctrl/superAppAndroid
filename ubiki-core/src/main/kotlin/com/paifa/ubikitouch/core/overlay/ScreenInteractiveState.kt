/* 功能概览：跟踪屏幕是否处于可交互状态，供 overlay 决定暂停或恢复。 */
package com.paifa.ubikitouch.core.overlay

// 该类只保存状态，不依赖 Android API，便于用 JVM 单元测试验证。
class ScreenInteractiveState(initialInteractive: Boolean = true) {
    var isInteractive: Boolean = initialInteractive
        private set

    // 返回值表示“本次更新是否需要恢复 overlay”。
    fun updateFromSystem(actualInteractive: Boolean): Boolean {
        val shouldResume = actualInteractive && !isInteractive
        isInteractive = actualInteractive
        return shouldResume
    }

    fun markInteractive() {
        isInteractive = true
    }

    fun markNotInteractive() {
        isInteractive = false
    }
}
