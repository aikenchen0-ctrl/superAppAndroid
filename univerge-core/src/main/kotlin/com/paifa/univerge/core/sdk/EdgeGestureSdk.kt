package com.paifa.univerge.core.sdk

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData

/** Host boundary for the edge gesture runtime. It contains no Android or chat types. */
fun interface EdgeGestureActionHandler {
    fun execute(action: GestureAction, data: GestureData): Boolean
}

/** Optional host behavior. Returning true means the host consumed the action. */
interface EdgeGestureHostActions {
    fun consumeBack(data: GestureData): Boolean = false

    fun execute(action: GestureAction, data: GestureData): Boolean = false
}

/** Immutable lifecycle/configuration contract exposed by the edge gesture SDK. */
data class EdgeGestureSdkConfig(
    val enabled: Boolean = true,
    val keepNativeInputWhileHostSurfaceIsVisible: Boolean = true
)

interface EdgeGestureSdk {
    val isStarted: Boolean

    fun start(config: EdgeGestureSdkConfig)

    fun updateConfig(config: EdgeGestureSdkConfig)

    fun stop()

    fun close()
}
