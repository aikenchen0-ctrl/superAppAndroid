package com.paifa.univerge.app.heavydrag

import android.content.Context
import android.view.MotionEvent
import com.paifa.univerge.heavydrag.android.HeavyTouchClassifierError
import com.paifa.univerge.heavydrag.android.HeavyTouchClassifierGateway
import com.paifa.univerge.heavydrag.core.HeavyTouchClassification
import com.paifa.univerge.heavydrag.core.HeavyTouchEventType
import com.paifa.univerge.heavydrag.core.HeavyTouchStrength
import com.zhifaios.eyes.touch.AispectTouchClassifier
import com.zhifaios.eyes.touch.AispectTouchConfig
import com.zhifaios.eyes.touch.AispectTouchError
import com.zhifaios.eyes.touch.AispectTouchListener
import com.zhifaios.eyes.touch.AispectTouchResult

/** 应用侧防腐层：把 Aispect AAR 结果转换为通用拖拽领域结果。 */
internal class AispectHeavyTouchBridge(
    context: Context,
    config: AispectTouchConfig = AispectTouchConfig.defaultConfig()
) : HeavyTouchClassifierGateway {
    private val classifier = AispectTouchClassifier(context.applicationContext, config)
    private var listener: HeavyTouchClassifierGateway.Listener? = null
    private var activeGestureId: Long? = null
    private var rawResultListener: ((AispectTouchResult) -> Unit)? = null
    private var rawErrorListener: ((AispectTouchError) -> Unit)? = null

    init {
        classifier.setListener(object : AispectTouchListener {
            override fun onTouchResult(result: AispectTouchResult) {
                rawResultListener?.invoke(result)
                val resultGestureId = result.gestureId.takeIf { it > 0L } ?: activeGestureId ?: return
                listener?.onClassification(result.toHeavyClassification(resultGestureId))
            }

            override fun onTouchError(error: AispectTouchError) {
                rawErrorListener?.invoke(error)
                val errorGestureId = error.gestureId.takeIf { it > 0L } ?: activeGestureId ?: 0L
                listener?.onError(
                    HeavyTouchClassifierError(
                        gestureId = errorGestureId,
                        code = error.code.name,
                        message = error.cause?.message,
                        cause = error.cause
                    )
                )
            }
        })
    }

    override fun setListener(listener: HeavyTouchClassifierGateway.Listener?) {
        this.listener = listener
    }

    fun setRawResultListener(listener: ((AispectTouchResult) -> Unit)?) {
        rawResultListener = listener
    }

    fun setRawErrorListener(listener: ((AispectTouchError) -> Unit)?) {
        rawErrorListener = listener
    }

    fun selectedModelInfo() = classifier.selectedModelInfo()

    override fun start() = classifier.start()

    override fun stop() = classifier.stop()

    override fun close() = classifier.close()

    override fun handleMotionEvent(
        event: MotionEvent,
        width: Int,
        height: Int,
        gestureId: Long
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) activeGestureId = gestureId
        val handled = classifier.handleMotionEvent(event, width, height, gestureId)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeGestureId = null
        }
        return handled
    }
}

private fun AispectTouchResult.toHeavyClassification(fallbackGestureId: Long): HeavyTouchClassification {
    val mappedEventType = when (eventType) {
        com.zhifaios.eyes.touch.AispectTouchEventType.SAMPLE -> HeavyTouchEventType.SAMPLE
        com.zhifaios.eyes.touch.AispectTouchEventType.PRESS -> HeavyTouchEventType.PRESS
        com.zhifaios.eyes.touch.AispectTouchEventType.TAP -> HeavyTouchEventType.TAP
        com.zhifaios.eyes.touch.AispectTouchEventType.HOLD -> HeavyTouchEventType.HOLD
        com.zhifaios.eyes.touch.AispectTouchEventType.DRAG -> HeavyTouchEventType.DRAG
        com.zhifaios.eyes.touch.AispectTouchEventType.CANCEL -> HeavyTouchEventType.CANCEL
        com.zhifaios.eyes.touch.AispectTouchEventType.UNKNOWN -> HeavyTouchEventType.UNKNOWN
    }
    val mappedStrength = when (strength) {
        com.zhifaios.eyes.touch.AispectTouchStrength.LIGHT -> HeavyTouchStrength.LIGHT
        com.zhifaios.eyes.touch.AispectTouchStrength.HEAVY -> HeavyTouchStrength.HEAVY
        com.zhifaios.eyes.touch.AispectTouchStrength.UNKNOWN -> HeavyTouchStrength.UNKNOWN
    }
    return HeavyTouchClassification(
        gestureId = fallbackGestureId,
        eventType = mappedEventType,
        strength = mappedStrength,
        confidence = confidence.toFloat(),
        eventTimeMillis = eventTimeMillis
    )
}
