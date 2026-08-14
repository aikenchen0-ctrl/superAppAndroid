package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal data class FinderPublishOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun finderPublishOverlayWindowPresentation() = FinderPublishOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun finderPublishStatusBarHeightDp(): Int = 30

internal fun finderPublishEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

internal fun finderPublishExitTranslationY(heightPx: Int): Float {
    val height = heightPx.coerceAtLeast(0)
    return if (height == 0) 0f else -height.toFloat()
}

internal class FinderPublishOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: ComposeView? = null
    private var owner: AccessibilityOverlayComposeOwner? = null

    fun show() {
        if (view != null) return
        val composeOwner = AccessibilityOverlayComposeOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = finderPublishEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { FinderPublishScreen(context = context, onBack = ::dismiss) }
        }
        runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            composeView.post {
                if (view !== composeView) return@post
                composeView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(SHOW_DURATION_MILLIS)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
        }.onFailure { error ->
            composeOwner.destroy()
            Log.w(TAG, "failed to add Finder publish overlay", error)
        }
    }

    fun dismissImmediately() {
        val currentView = view ?: return
        view = null
        currentView.animate().cancel()
        owner?.destroy()
        owner = null
        runCatching { windowManager.removeViewImmediate(currentView) }
            .onFailure { error -> Log.w(TAG, "failed to remove Finder publish overlay", error) }
    }

    fun dismiss() {
        val currentView = view ?: return
        currentView.animate().cancel()
        val exitHeight = currentView.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        currentView.animate()
            .translationY(finderPublishExitTranslationY(exitHeight))
            .alpha(0f)
            .setDuration(HIDE_DURATION_MILLIS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                if (view === currentView) dismissImmediately()
            }
            .start()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private companion object {
        const val TAG = "UbikiFinderPublish"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}
