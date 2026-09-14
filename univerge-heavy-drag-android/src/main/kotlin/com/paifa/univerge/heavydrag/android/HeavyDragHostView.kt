package com.paifa.univerge.heavydrag.android

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/** 传统 View 树的根级事件容器；未命中已注册源时保持原生分发。 */
class HeavyDragHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var runtime: HeavyDragRuntime? = null
    private var interceptingGesture = false
    private var downAlreadyForwarded = false

    fun bind(runtime: HeavyDragRuntime): HeavyDragHostView {
        if (this.runtime === runtime) return this
        this.runtime?.stop()
        this.runtime = runtime
        if (isAttachedToWindow) runtime.start()
        return this
    }

    fun unbind() {
        runtime?.stop()
        runtime = null
        interceptingGesture = false
        downAlreadyForwarded = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runtime?.start()
    }

    override fun onDetachedFromWindow() {
        runtime?.stop()
        interceptingGesture = false
        downAlreadyForwarded = false
        super.onDetachedFromWindow()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val captured = runtime?.handleMotionEvent(event, width, height) == true
            interceptingGesture = captured
            downAlreadyForwarded = captured
            return captured
        }
        return interceptingGesture || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interceptingGesture) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && downAlreadyForwarded) {
            downAlreadyForwarded = false
            return true
        }
        val consumed = runtime?.handleMotionEvent(event, width, height) == true
        // A pending gesture that moved too far is cancelled by the runtime.
        // Stop claiming future streams immediately; the current stream cannot
        // be handed back to a child after interception has started.
        if (!consumed && event.actionMasked == MotionEvent.ACTION_MOVE) {
            interceptingGesture = false
            downAlreadyForwarded = false
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
            || event.actionMasked == MotionEvent.ACTION_POINTER_UP
        ) {
            interceptingGesture = false
            downAlreadyForwarded = false
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            interceptingGesture = false
            downAlreadyForwarded = false
        }
        return consumed
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            runtime?.onWindowLostFocus()
            interceptingGesture = false
            downAlreadyForwarded = false
        }
    }
}
