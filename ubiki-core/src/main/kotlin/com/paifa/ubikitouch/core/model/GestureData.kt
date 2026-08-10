/*
 * 功能概览：记录一次触摸手势的起点和终点坐标。
 * Kotlin 语法提示：`val` 是只读引用；默认参数让调用方可以只传需要的字段。
 */
package com.paifa.ubikitouch.core.model

// 这是跨模块传递手势信息的轻量值对象，不保存 Android View 状态。
data class GestureData(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f
)
