/* 功能概览：表示触摸条位于屏幕左侧还是右侧，并提供持久化 ID。 */
package com.paifa.univerge.core.model

// `id` 用于保存到配置或跨进程传递，不能直接依赖枚举名称。
enum class EdgeSide(val id: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        // 找不到旧配置时回退到左侧，保证读取配置不会抛异常。
        fun fromId(id: String): EdgeSide = entries.firstOrNull { it.id == id } ?: LEFT
    }
}
