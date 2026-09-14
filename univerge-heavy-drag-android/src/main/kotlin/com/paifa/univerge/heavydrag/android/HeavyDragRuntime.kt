package com.paifa.univerge.heavydrag.android

import android.os.SystemClock
import android.view.MotionEvent
import com.paifa.univerge.heavydrag.core.HeavyDragCancelReason
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyPoint
import com.paifa.univerge.heavydrag.core.HeavyDragSessionSnapshot

/** 每个 Android UI Root 唯一的触摸事件入口。 */
class HeavyDragRuntime(
    val coordinator: HeavyDragCoordinator,
    private val classifier: HeavyTouchClassifierGateway,
    private val clockMillis: () -> Long = SystemClock::uptimeMillis,
    private val onClassifierError: (HeavyTouchClassifierError) -> Unit = {}
) : AutoCloseable {
    private var activeGestureId: Long? = null
    private var running = false
    private var stateListener: ((HeavyDragSessionSnapshot?) -> Unit)? = null

    init {
        classifier.setListener(object : HeavyTouchClassifierGateway.Listener {
            override fun onClassification(result: com.paifa.univerge.heavydrag.core.HeavyTouchClassification) {
                coordinator.onClassification(result, clockMillis())
                notifyState()
            }

            override fun onError(error: HeavyTouchClassifierError) {
                onClassifierError(error)
            }
        })
    }

    fun start() {
        if (running) return
        running = true
        classifier.start()
        notifyState()
    }

    fun stop() {
        if (!running) return
        running = false
        coordinator.cancel(HeavyDragCancelReason.HOST_DISPOSED, clockMillis())
        activeGestureId?.let { gestureId ->
            cancelClassifierGesture(null, 0, 0, gestureId)
        }
        activeGestureId = null
        classifier.stop()
        notifyState()
    }

    fun setStateListener(listener: ((HeavyDragSessionSnapshot?) -> Unit)?) {
        stateListener = listener
        notifyState()
    }

    fun handleMotionEvent(event: MotionEvent, width: Int, height: Int): Boolean {
        if (!running) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val pointerId = event.getPointerId(event.actionIndex)
            val gestureId = coordinator.onPointerDown(
                pointerId = pointerId,
                position = HeavyPoint(event.x, event.y),
                timestampMillis = event.eventTime,
                pointerCount = event.pointerCount
            ) ?: return false
            activeGestureId = gestureId
            classifier.handleMotionEvent(event, width, height, gestureId)
            notifyState()
            return true
        }

        val gestureId = activeGestureId ?: return false
        val activePointerId = coordinator.activeSession?.pointerId ?: event.getPointerId(event.actionIndex)
        val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.actionIndex
        val point = HeavyPoint(event.getX(pointerIndex), event.getY(pointerIndex))

        val consumed = when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dispatch = coordinator.onPointerMove(
                    activePointerId,
                    point,
                    event.eventTime,
                    gestureId,
                    event.pointerCount
                )
                if (!dispatch.consumed) {
                    cancelClassifierGesture(event, width, height, gestureId)
                    activeGestureId = null
                } else {
                    classifier.handleMotionEvent(event, width, height, gestureId)
                }
                dispatch.consumed
            }

            MotionEvent.ACTION_UP -> {
                val dispatch = coordinator.onPointerUp(
                    activePointerId,
                    point,
                    event.eventTime,
                    gestureId,
                    event.pointerCount
                )
                if (dispatch.consumed) {
                    classifier.handleMotionEvent(event, width, height, gestureId)
                } else {
                    cancelClassifierGesture(event, width, height, gestureId)
                }
                activeGestureId = null
                dispatch.consumed
            }

            MotionEvent.ACTION_CANCEL -> {
                val dispatch = coordinator.onPointerCancel(
                    activePointerId,
                    event.eventTime,
                    gestureId,
                    event.pointerCount
                )
                cancelClassifierGesture(event, width, height, gestureId)
                activeGestureId = null
                dispatch.consumed
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                val dispatch = coordinator.handlePointerEvent(
                    com.paifa.univerge.heavydrag.core.HeavyPointerEvent(
                        action = if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                            com.paifa.univerge.heavydrag.core.HeavyPointerAction.POINTER_DOWN
                        } else {
                            com.paifa.univerge.heavydrag.core.HeavyPointerAction.POINTER_UP
                        },
                        pointerId = activePointerId,
                        position = point,
                        timestampMillis = event.eventTime,
                        pointerCount = event.pointerCount,
                        gestureId = gestureId
                    )
                )
                cancelClassifierGesture(event, width, height, gestureId)
                activeGestureId = null
                dispatch.consumed
            }

            else -> false
        }
        notifyState()
        return consumed
    }

    fun onWindowLostFocus() {
        val gestureId = activeGestureId
        coordinator.onWindowLostFocus(clockMillis())
        if (gestureId != null) {
            cancelClassifierGesture(null, 0, 0, gestureId)
        }
        activeGestureId = null
        notifyState()
    }

    override fun close() {
        coordinator.dispose(clockMillis())
        activeGestureId?.let { gestureId ->
            cancelClassifierGesture(null, 0, 0, gestureId)
        }
        activeGestureId = null
        classifier.setListener(null)
        classifier.close()
        running = false
        notifyState()
    }

    private fun notifyState() {
        stateListener?.invoke(coordinator.activeSession)
    }

    /**
     * A Compose interop stream cannot be handed back after its DOWN was
     * consumed. Close the classifier side explicitly when the coordinator
     * abandons a pending gesture so both layers observe the same terminal event.
     */
    private fun cancelClassifierGesture(
        sourceEvent: MotionEvent?,
        width: Int,
        height: Int,
        gestureId: Long
    ) {
        val cancel = if (sourceEvent != null) {
            MotionEvent.obtain(sourceEvent)
        } else {
            MotionEvent.obtain(
                0L,
                clockMillis(),
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0
            )
        }
        try {
            cancel.action = MotionEvent.ACTION_CANCEL
            classifier.handleMotionEvent(cancel, width, height, gestureId)
        } finally {
            cancel.recycle()
        }
    }
}
