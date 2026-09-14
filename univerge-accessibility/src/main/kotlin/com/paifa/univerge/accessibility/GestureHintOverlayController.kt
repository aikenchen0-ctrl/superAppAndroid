package com.paifa.univerge.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

internal class GestureHintOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var hintView: TextView? = null
    private var shownLabel: String? = null

    fun prewarm() {
        if (hintView != null) return
        createView()?.apply { visibility = android.view.View.GONE }
    }

    fun show(label: String) {
        if (label == shownLabel && hintView != null) return
        val view = hintView ?: createView() ?: return
        view.visibility = android.view.View.VISIBLE
        view.text = "即将执行：$label"
        shownLabel = label
    }

    private fun createView(): TextView? {
        val view = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.argb(224, 26, 31, 42))
                cornerRadius = dp(18).toFloat()
            }
        }
        return runCatching {
            windowManager.addView(view, layoutParams())
            hintView = view
            view
        }.getOrElse { null }
    }

    fun hide() {
        val view = hintView ?: return
        // Keep the non-touchable window alive and only hide the child. Repeated
        // add/remove IPC in MOVE/UP was a source of input callback stalls.
        view.visibility = android.view.View.GONE
        shownLabel = null
    }

    /** Permanent teardown used only when the accessibility service is destroyed. */
    fun dispose() {
        val view = hintView ?: return
        hintView = null
        shownLabel = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            gestureHintWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeightPx() + dp(10)
        }
    }

    private fun statusBarHeightPx(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return resourceId.takeIf { it > 0 }?.let(context.resources::getDimensionPixelSize) ?: 0
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

internal fun gestureHintWindowFlags(): Int {
    return WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
}
