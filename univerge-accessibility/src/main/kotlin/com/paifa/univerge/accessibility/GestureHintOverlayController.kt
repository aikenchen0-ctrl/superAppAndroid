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

    fun show(label: String) {
        if (label == shownLabel && hintView != null) return
        val view = hintView ?: TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.argb(224, 26, 31, 42))
                cornerRadius = dp(18).toFloat()
            }
        }.also { created ->
            runCatching { windowManager.addView(created, layoutParams()) }
                .onSuccess { hintView = created }
                .onFailure { return }
        }
        view.text = "即将执行：$label"
        shownLabel = label
    }

    fun hide() {
        val view = hintView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        hintView = null
        shownLabel = null
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
