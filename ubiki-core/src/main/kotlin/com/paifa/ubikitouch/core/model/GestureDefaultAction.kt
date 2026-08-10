/* 功能概览：为未显式配置的手势提供默认动作映射。 */
package com.paifa.ubikitouch.core.model

// 使用 `object` 保证默认映射只有一份，且不需要实例化。
object GestureDefaultAction {
    // `when` 表达式会返回一个 GestureAction，而不是只执行分支。
    fun forGesture(gestureType: GestureType): GestureAction {
        return when (gestureType) {
            GestureType.PULL_INWARD -> GestureAction.Back
            GestureType.PULL_INWARD_SHORT -> GestureAction.Back
            GestureType.PULL_INWARD_LONG -> GestureAction.Home
            GestureType.SWIPE_UP -> GestureAction.ExpandFloatingChat
            GestureType.SWIPE_DOWN -> GestureAction.CollapseFloatingChat
            else -> GestureAction.None
        }
    }
}
