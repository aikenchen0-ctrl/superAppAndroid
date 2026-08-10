package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

internal enum class VideoDemoOverlayCommand {
    Show,
    Hide
}

internal fun nextVideoDemoOverlayCommand(isShowing: Boolean): VideoDemoOverlayCommand {
    return if (isShowing) VideoDemoOverlayCommand.Hide else VideoDemoOverlayCommand.Show
}

internal fun videoDemoOverlayHeightPx(screenHeightPx: Int): Int {
    return screenHeightPx.coerceAtLeast(0)
}

internal class VideoDemoOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var overlayView: VideoDemoOverlayView? = null

    fun showOrToggle() {
        when (nextVideoDemoOverlayCommand(isShowing = overlayView != null)) {
            VideoDemoOverlayCommand.Show -> show()
            VideoDemoOverlayCommand.Hide -> dismiss()
        }
    }

    fun dismissImmediately() {
        val view = overlayView ?: return
        overlayView = null
        view.animate().cancel()
        view.dispose()
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "failed to remove video demo overlay", it) }
    }

    fun dismissIfShowing(): Boolean {
        if (overlayView == null) return false
        dismissImmediately()
        return true
    }

    private fun show() {
        val height = videoDemoOverlayHeightPx(context.resources.displayMetrics.heightPixels)
        if (height == 0) {
            Log.w(TAG, "skip video demo overlay because screen height is unavailable")
            return
        }

        val view = VideoDemoOverlayView(context, ::dismiss).apply {
            translationY = height.toFloat()
            alpha = 0.98f
        }
        runCatching {
            windowManager.addView(view, layoutParams())
            overlayView = view
            view.post {
                if (overlayView !== view) return@post
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(SHOW_DURATION_MILLIS)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
        }.onFailure { error ->
            overlayView = null
            Log.w(TAG, "failed to add video demo overlay", error)
        }
    }

    private fun dismiss() {
        val view = overlayView ?: return
        view.animate().cancel()
        view.animate()
            .translationY(view.height.toFloat().coerceAtLeast(1f))
            .alpha(0f)
            .setDuration(HIDE_DURATION_MILLIS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                if (overlayView !== view) return@withEndAction
                dismissImmediately()
            }
            .start()
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}
