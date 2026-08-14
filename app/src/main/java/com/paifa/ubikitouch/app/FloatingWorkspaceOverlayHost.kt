package com.paifa.ubikitouch.app

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.accessibility.UbikiAccessibilityService
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceMotion

private const val WorkspaceOverlayAnimationMillis = 240L

/**
 * 右侧 iconButton 的通用全屏悬浮承载器。
 * 测试流程：从普通 Activity 入口打开页面，确认由 TYPE_ACCESSIBILITY_OVERLAY 自下向上显示；
 * 点击共享工具栏的返回后，确认实体 View 向顶部退出并释放 WindowManager 资源。
 */
internal object FloatingWorkspaceOverlayHost {
    private var controller: FloatingWorkspaceOverlayController? = null

    fun show(
        content: @Composable (onBack: () -> Unit) -> Unit,
        onClosed: () -> Unit = {}
    ): Boolean {
        val service = UbikiAccessibilityService.instance ?: return false
        if (controller != null) return true
        return FloatingWorkspaceOverlayController(
            context = service,
            windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
            content = content,
            onClosed = {
                controller = null
                onClosed()
            }
        ).let { next ->
            controller = next
            next.show().also { shown -> if (!shown) controller = null }
        }
    }
}

private class FloatingWorkspaceOverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

private class FloatingWorkspaceOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val content: @Composable (onBack: () -> Unit) -> Unit,
    private val onClosed: () -> Unit
) {
    private var view: ComposeView? = null
    private var owner: FloatingWorkspaceOverlayOwner? = null
    private var closed = false

    fun show(): Boolean {
        if (view != null) return true
        val composeOwner = FloatingWorkspaceOverlayOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = FloatingWorkspaceMotion.enterTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { content(::dismiss) }
        }
        return runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            composeView.post {
                if (view === composeView) {
                    composeView.animate().translationY(0f).alpha(1f)
                        .setDuration(WorkspaceOverlayAnimationMillis)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
            }
            true
        }.getOrElse {
            composeOwner.destroy()
            false
        }
    }

    fun dismiss() {
        val current = view ?: return
        current.animate().cancel()
        val height = current.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        current.animate().translationY(FloatingWorkspaceMotion.exitTranslationY(height)).alpha(0f)
            .setDuration(WorkspaceOverlayAnimationMillis)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction { if (view === current) dismissImmediately() }
            .start()
    }

    private fun dismissImmediately() {
        val current = view
        view = null
        current?.animate()?.cancel()
        owner?.destroy()
        owner = null
        current?.let { runCatching { windowManager.removeViewImmediate(it) } }
        if (!closed) {
            closed = true
            onClosed()
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }
}
