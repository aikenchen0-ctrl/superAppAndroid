package com.paifa.univerge.heavydrag.android

import android.view.MotionEvent
import com.paifa.univerge.heavydrag.core.HeavyTouchClassification

/** 将任意触摸分类器适配为重触交互层可消费的最小接口。 */
interface HeavyTouchClassifierGateway {
    interface Listener {
        fun onClassification(result: HeavyTouchClassification)
        fun onError(error: HeavyTouchClassifierError) = Unit
    }

    fun setListener(listener: Listener?)
    fun start()
    fun stop()
    fun close()

    fun handleMotionEvent(
        event: MotionEvent,
        width: Int,
        height: Int,
        gestureId: Long
    ): Boolean
}

data class HeavyTouchClassifierError(
    val gestureId: Long,
    val code: String,
    val message: String? = null,
    val cause: Throwable? = null
)
