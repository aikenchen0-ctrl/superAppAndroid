/*
 * 功能概览：记录一次触摸手势的起点和终点坐标。
 * Kotlin 语法提示：`val` 是只读引用；默认参数让调用方可以只传需要的字段。
 */
package com.paifa.univerge.core.model

// 这是跨模块传递手势信息的轻量值对象，不保存 Android View 状态；终端事件还携带事务元数据。
data class GestureData(
    var startX: Float = 0f,
    var startY: Float = 0f,
    var endX: Float = 0f,
    var endY: Float = 0f,
    /** Stable transaction identity copied from the core terminal signal. */
    val gestureId: Long = 0L,
    /** Configuration revision captured when the transaction started. */
    val snapshotVersion: Long = 0L,
    /** Matched physical hot-zone id, or -1 when the source has no zone. */
    val zoneId: Int = -1
)
