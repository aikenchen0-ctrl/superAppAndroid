package com.paifa.ubikitouch.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.paifa.ubikitouch.accessibility.floatingchat.shell.floatingChatInternalEdgeGestureTouchTargetDp
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.overlay.edgeOverlayOutlineColorArgb
import kotlin.math.roundToInt

internal data class SystemGestureExclusionRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal data class SystemGestureOutlineRect(
    val side: EdgeSide,
    val zoneId: Int,
    val bounds: SystemGestureExclusionRect
)

internal fun nativeSystemGestureExclusionRects(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    targetWidthPx: Int,
    targetHeightPx: Int,
    intercepts: List<NativeTouchInterceptRect>
): List<SystemGestureExclusionRect> {
    return nativeSystemGestureOutlineRects(
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        targetWidthPx = targetWidthPx,
        targetHeightPx = targetHeightPx,
        intercepts = intercepts
    ).map(SystemGestureOutlineRect::bounds)
}

internal fun nativeSystemGestureOutlineRects(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    targetWidthPx: Int,
    targetHeightPx: Int,
    intercepts: List<NativeTouchInterceptRect>
): List<SystemGestureOutlineRect> {
    if (
        sourceWidthPx <= 0 ||
        sourceHeightPx <= 0 ||
        targetWidthPx <= 0 ||
        targetHeightPx <= 0
    ) {
        return emptyList()
    }

    val horizontalScale = targetWidthPx.toDouble() / sourceWidthPx
    val verticalScale = targetHeightPx.toDouble() / sourceHeightPx
    return intercepts.mapNotNull { intercept ->
        if (intercept.left >= intercept.right || intercept.top >= intercept.bottom) {
            return@mapNotNull null
        }

        val left = scaledCoordinate(intercept.left, horizontalScale).coerceIn(0, targetWidthPx)
        val top = scaledCoordinate(intercept.top, verticalScale).coerceIn(0, targetHeightPx)
        val right = scaledCoordinate(intercept.right, horizontalScale).coerceIn(0, targetWidthPx)
        val bottom = scaledCoordinate(intercept.bottom, verticalScale).coerceIn(0, targetHeightPx)
        if (left >= right || top >= bottom) {
            null
        } else {
            SystemGestureOutlineRect(
                side = intercept.side,
                zoneId = intercept.zoneId,
                bounds = SystemGestureExclusionRect(left, top, right, bottom)
            )
        }
    }
}

internal fun rootBackGestureTakeoverSides(
    density: Float,
    screenWidthPx: Int,
    intercepts: List<NativeTouchInterceptRect>
): Set<EdgeSide> {
    if (!density.isFinite() || density <= 0f || screenWidthPx <= 0) return emptySet()

    val maximumSystemExclusionHeightPx = SYSTEM_GESTURE_EXCLUSION_LIMIT_DP * density.toDouble()
    return EdgeSide.entries.filterTo(linkedSetOf()) { side ->
        val intervals = intercepts.mapNotNull { intercept ->
            intercept.verticalIntervalForPhysicalEdge(side, screenWidthPx)
        }
        verticalUnionHeight(intervals).toDouble() > maximumSystemExclusionHeightPx
    }
}

internal fun shouldShowFullScreenGestureExclusionOverlay(
    resolvedMode: ResolvedGestureInputMode,
    floatingChatOwnsSurface: Boolean
): Boolean {
    return resolvedMode == ResolvedGestureInputMode.NativeTouchInteraction || floatingChatOwnsSurface
}

internal fun fullScreenGestureExclusionIntercepts(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    density: Float,
    configs: List<EdgeZoneConfig>,
    resolvedMode: ResolvedGestureInputMode,
    floatingChatOwnsSurface: Boolean
): List<NativeTouchInterceptRect> {
    if (!shouldShowFullScreenGestureExclusionOverlay(resolvedMode, floatingChatOwnsSurface)) {
        return emptyList()
    }
    val fixedTouchTargetDp = if (resolvedMode == ResolvedGestureInputMode.NativeTouchInteraction) {
        null
    } else {
        floatingChatInternalEdgeGestureTouchTargetDp()
    }
    return edgeGestureInterceptRects(
        screenWidthPx = sourceWidthPx,
        screenHeightPx = sourceHeightPx,
        density = density,
        configs = configs,
        fixedTouchTargetDp = fixedTouchTargetDp
    )
}

internal data class GestureNavigationProtectionPlan(
    val systemExclusionIntercepts: List<NativeTouchInterceptRect>,
    val rootTakeoverSides: Set<EdgeSide>
)

internal fun gestureNavigationProtectionPlan(
    sdkInt: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    configs: List<EdgeZoneConfig>,
    resolvedMode: ResolvedGestureInputMode,
    floatingChatOwnsSurface: Boolean
): GestureNavigationProtectionPlan {
    val allIntercepts = edgeGestureInterceptRects(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        density = density,
        configs = configs
    )
    return GestureNavigationProtectionPlan(
        systemExclusionIntercepts = fullScreenGestureExclusionIntercepts(
            sourceWidthPx = screenWidthPx,
            sourceHeightPx = screenHeightPx,
            density = density,
            configs = configs,
            resolvedMode = resolvedMode,
            floatingChatOwnsSurface = floatingChatOwnsSurface
        ),
        rootTakeoverSides = rootGestureTakeoverSidesForSdk(
            sdkInt = sdkInt,
            density = density,
            screenWidthPx = screenWidthPx,
            intercepts = allIntercepts
        )
    )
}

private fun rootGestureTakeoverSidesForSdk(
    sdkInt: Int,
    density: Float,
    screenWidthPx: Int,
    intercepts: List<NativeTouchInterceptRect>
): Set<EdgeSide> {
    return when {
        sdkInt >= ANDROID_Q_API -> rootBackGestureTakeoverSides(density, screenWidthPx, intercepts)
        sdkInt >= ANDROID_O_API -> EdgeSide.entries.filterTo(linkedSetOf()) { side ->
            intercepts.any { intercept ->
                intercept.verticalIntervalForPhysicalEdge(side, screenWidthPx) != null
            }
        }
        else -> emptySet()
    }
}

internal fun fullScreenGestureExclusionOverlayWindowType(): Int {
    return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
}

@Suppress("DEPRECATION")
internal fun fullScreenGestureExclusionOverlayWindowFlags(): Int {
    return WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN
}

internal class NativeGestureExclusionOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val warningLogger: (String, Throwable?) -> Unit = { message, error ->
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }
) {
    private var exclusionView: SystemGestureExclusionOverlayView? = null

    fun synchronize(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        intercepts: List<NativeTouchInterceptRect>
    ) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            sourceWidthPx <= 0 ||
            sourceHeightPx <= 0 ||
            intercepts.isEmpty()
        ) {
            remove()
            return
        }

        val view = exclusionView ?: SystemGestureExclusionOverlayView(context).also {
            exclusionView = it
        }
        view.updateSourceRects(sourceWidthPx, sourceHeightPx, intercepts)
        if (view.isAttachedToWindow) return

        runCatching {
            windowManager.addView(view, fullScreenGestureExclusionOverlayLayoutParams())
        }.onFailure { error ->
            warningLogger("failed to add full-screen gesture exclusion overlay", error)
            if (!view.isAttachedToWindow) exclusionView = null
        }
    }

    fun remove() {
        val view = exclusionView ?: return
        view.clearGestureExclusionRects()
        if (!view.isAttachedToWindow) {
            exclusionView = null
            return
        }
        runCatching {
            windowManager.removeView(view)
        }.onSuccess {
            exclusionView = null
        }.onFailure { error ->
            warningLogger("failed to remove full-screen gesture exclusion overlay", error)
        }
    }

    @Suppress("DEPRECATION")
    private fun fullScreenGestureExclusionOverlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            fullScreenGestureExclusionOverlayWindowType(),
            fullScreenGestureExclusionOverlayWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
    }
}

@SuppressLint("ViewConstructor")
private class SystemGestureExclusionOverlayView(
    context: Context
) : View(context) {
    private var sourceWidthPx: Int = 0
    private var sourceHeightPx: Int = 0
    private var intercepts: List<NativeTouchInterceptRect> = emptyList()
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    init {
        // The dedicated edge indicators own visuals. This helper only publishes exclusion rects.
        setWillNotDraw(true)
    }

    fun updateSourceRects(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        intercepts: List<NativeTouchInterceptRect>
    ) {
        this.sourceWidthPx = sourceWidthPx
        this.sourceHeightPx = sourceHeightPx
        this.intercepts = intercepts
        applyGestureExclusionRects()
        invalidate()
    }

    fun clearGestureExclusionRects() {
        intercepts = emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = emptyList()
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyGestureExclusionRects()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applyGestureExclusionRects()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun applyGestureExclusionRects() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !isAttachedToWindow ||
            width <= 0 ||
            height <= 0
        ) {
            return
        }
        systemGestureExclusionRects = nativeSystemGestureExclusionRects(
            sourceWidthPx = sourceWidthPx,
            sourceHeightPx = sourceHeightPx,
            targetWidthPx = width,
            targetHeightPx = height,
            intercepts = intercepts
        ).map { rect ->
            Rect(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun drawOutline(canvas: Canvas, outline: SystemGestureOutlineRect) {
        val bounds = outline.bounds
        val outlineWidth = bounds.right - bounds.left
        val outlineHeight = bounds.bottom - bounds.top
        val strokeWidth = (OUTLINE_STROKE_WIDTH_DP * resources.displayMetrics.density)
            .coerceAtMost(minOf(outlineWidth, outlineHeight).toFloat())
        if (strokeWidth <= 0f) return
        val strokeInset = strokeWidth / 2f
        val left = bounds.left + strokeInset
        val top = bounds.top + strokeInset
        val right = bounds.right - strokeInset
        val bottom = bounds.bottom - strokeInset
        if (right < left || bottom < top) return
        val cornerRadius = (OUTLINE_CORNER_RADIUS_DP * resources.displayMetrics.density).coerceAtMost(
            minOf(right - left, bottom - top) / 2f
        )
        outlinePaint.color = edgeOverlayOutlineColorArgb(outline.side, outline.zoneId)
        outlinePaint.strokeWidth = strokeWidth
        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, outlinePaint)
    }
}

private fun scaledCoordinate(coordinate: Int, scale: Double): Int {
    return (coordinate.toDouble() * scale).roundToInt()
}

private fun NativeTouchInterceptRect.verticalIntervalForPhysicalEdge(
    side: EdgeSide,
    screenWidthPx: Int
): VerticalInterval? {
    if (
        this.side != side ||
        left < 0 ||
        right > screenWidthPx ||
        left >= right ||
        top >= bottom
    ) {
        return null
    }
    val touchesPhysicalEdge = when (side) {
        EdgeSide.LEFT -> left == 0
        EdgeSide.RIGHT -> right == screenWidthPx
    }
    return if (touchesPhysicalEdge) VerticalInterval(top, bottom) else null
}

private fun verticalUnionHeight(intervals: List<VerticalInterval>): Long {
    if (intervals.isEmpty()) return 0L

    val sorted = intervals.sortedWith(compareBy<VerticalInterval> { it.start }.thenBy { it.end })
    var currentStart = sorted.first().start
    var currentEnd = sorted.first().end
    var total = 0L
    sorted.drop(1).forEach { interval ->
        if (interval.start > currentEnd) {
            total += currentEnd.toLong() - currentStart.toLong()
            currentStart = interval.start
            currentEnd = interval.end
        } else if (interval.end > currentEnd) {
            currentEnd = interval.end
        }
    }
    return total + currentEnd.toLong() - currentStart.toLong()
}

private data class VerticalInterval(
    val start: Int,
    val end: Int
)

private const val SYSTEM_GESTURE_EXCLUSION_LIMIT_DP = 200
private const val ANDROID_O_API = 26
private const val ANDROID_Q_API = 29
private const val OUTLINE_STROKE_WIDTH_DP = 2f
private const val OUTLINE_CORNER_RADIUS_DP = 8f
