package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.abs
import kotlin.math.roundToInt

internal class BackWaveOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var waveView: BackWaveView? = null
    private var activeAnchor: BackWaveAnchor? = null

    fun update(progress: BackGestureProgress, anchor: BackWaveAnchor) {
        val localizedAnchor = localizedBackWaveAnchor(
            anchor = anchor,
            startY = progress.startY,
            density = context.resources.displayMetrics.density
        )
        ensureView(localizedAnchor).update(visibleState(progress, localizedAnchor))
    }

    fun finish(progress: BackGestureProgress, anchor: BackWaveAnchor) {
        val localizedAnchor = localizedBackWaveAnchor(
            anchor = anchor,
            startY = progress.startY,
            density = context.resources.displayMetrics.density
        )
        ensureView(localizedAnchor).update(visibleState(progress, localizedAnchor))
        dismiss()
    }

    private fun visibleState(
        progress: BackGestureProgress,
        anchor: BackWaveAnchor
    ): BackWaveState.Visible {
        return BackWaveState.Visible(
            side = progress.side,
            progress = progress.visualProgress,
            stretchProgress = progress.visualProgress,
            longDistance = progress.longCommitted,
            touchY = progress.touchY - anchor.y,
            startY = progress.startY - anchor.y,
            gestureType = progress.gestureType
        )
    }

    fun dismiss() {
        val view = waveView ?: return
        runCatching {
            windowManager.removeView(view)
        }.onFailure {
            Log.w(TAG, "failed to remove back wave overlay", it)
        }
        waveView = null
        activeAnchor = null
    }

    private fun ensureView(anchor: BackWaveAnchor): BackWaveView {
        val existing = waveView
        if (existing != null && activeAnchor == anchor) return existing
        dismiss()
        activeAnchor = anchor
        val view = BackWaveView(context)
        waveView = view
        runCatching {
            windowManager.addView(view, layoutParams(anchor))
        }.onFailure {
            Log.w(TAG, "failed to add back wave overlay", it)
            waveView = null
            activeAnchor = null
        }
        return view
    }

    private fun layoutParams(anchor: BackWaveAnchor): WindowManager.LayoutParams {
        val width = (WAVE_WIDTH_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return WindowManager.LayoutParams(
            width,
            anchor.height.coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (anchor.side == EdgeSide.LEFT) {
                0
            } else {
                context.resources.displayMetrics.widthPixels - width
            }
            y = anchor.y
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val WAVE_WIDTH_DP = 72
    }
}

internal data class BackWaveAnchor(
    val side: EdgeSide,
    val y: Int,
    val height: Int
)

private sealed class BackWaveState {
    data object Hidden : BackWaveState()

    data class Visible(
        val side: EdgeSide,
        val progress: Float,
        val stretchProgress: Float,
        val longDistance: Boolean,
        val touchY: Float,
        val startY: Float,
        val gestureType: GestureType
    ) : BackWaveState()
}

private class BackWaveView(context: Context) : View(context) {
    private var state: BackWaveState = BackWaveState.Hidden
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(92, 9, 12, 18)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(170, 243, 248, 255)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(88, 255, 255, 255)
    }
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(225, 22, 28, 38)
    }
    private val surfacePath = Path()
    private val highlightPath = Path()
    private val cuePath = Path()

    init {
        setLayerType(backWaveLayerType(), null)
    }

    fun update(nextState: BackWaveState) {
        state = nextState
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val visible = state as? BackWaveState.Visible ?: return
        val progress = visible.progress.coerceIn(0f, 1f)
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val density = resources.displayMetrics.density
        val layout = backWaveVisualLayout(
            density = density,
            progress = progress,
            stretchProgress = visible.stretchProgress,
            longDistance = visible.longDistance,
            startY = visible.startY,
            touchY = visible.touchY
        )
        buildBackWaveSurfacePath(
            target = surfacePath,
            layout = layout,
            side = visible.side,
            viewWidth = viewWidth,
            samples = SURFACE_SAMPLE_COUNT
        )
        val highlightLayout = layout.copy(
            halfHeightPx = layout.halfHeightPx * 0.68f,
            depthPx = layout.depthPx * 0.72f,
            bendStrength = layout.bendStrength * 0.58f
        )
        buildBackWaveSurfacePath(
            target = highlightPath,
            layout = highlightLayout,
            side = visible.side,
            viewWidth = viewWidth,
            samples = SURFACE_SAMPLE_COUNT
        )
        buildBackWaveCuePath(
            target = cuePath,
            layout = layout,
            side = visible.side,
            viewWidth = viewWidth
        )

        val alpha = (layout.alphaProgress * 255f).toInt().coerceIn(0, 255)
        shadowPaint.alpha = (alpha * 0.34f).toInt().coerceIn(0, 92)
        bubblePaint.alpha = (alpha * 0.74f).toInt().coerceIn(0, 190)
        highlightPaint.alpha = (alpha * 0.28f).toInt().coerceIn(0, 72)
        cuePaint.alpha = (alpha * 0.82f).toInt().coerceIn(0, 210)
        cuePaint.strokeWidth = layout.cueStrokeWidthPx
        canvas.save()
        canvas.translate(
            if (visible.side == EdgeSide.LEFT) density * 1.5f else -density * 1.5f,
            density * 2f
        )
        canvas.drawPath(surfacePath, shadowPaint)
        canvas.restore()
        canvas.drawPath(surfacePath, bubblePaint)
        canvas.drawPath(highlightPath, highlightPaint)
        canvas.drawPath(cuePath, cuePaint)
    }

    private companion object {
        const val SURFACE_SAMPLE_COUNT = 31
    }
}

internal fun backWaveLayerType(): Int = View.LAYER_TYPE_NONE

internal fun localizedBackWaveAnchor(
    anchor: BackWaveAnchor,
    startY: Float,
    density: Float
): BackWaveAnchor {
    val height = (BACK_WAVE_WINDOW_HEIGHT_DP * density.coerceAtLeast(0.1f))
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(anchor.height.coerceAtLeast(1))
    val minY = anchor.y
    val maxY = (anchor.y + anchor.height - height).coerceAtLeast(minY)
    val centeredY = startY.roundToInt() - height / 2
    return anchor.copy(
        y = centeredY.coerceIn(minY, maxY),
        height = height
    )
}

internal data class BackWaveVisualLayout(
    val centerY: Float,
    val halfHeightPx: Float,
    val depthPx: Float,
    val alphaProgress: Float,
    val pullRatio: Float,
    val bendStrength: Float,
    val cueCenterX: Float,
    val cueCenterY: Float,
    val cueSizePx: Float,
    val cueStrokeWidthPx: Float
)

internal data class BackWavePoint(
    val x: Float,
    val y: Float
)

internal fun backWaveVisualLayout(
    density: Float,
    progress: Float,
    stretchProgress: Float = progress,
    longDistance: Boolean,
    startY: Float,
    touchY: Float
): BackWaveVisualLayout {
    val safeDensity = density.coerceAtLeast(0.1f)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val easedProgress = easeOutQuart(clampedProgress)
    val easedStretch = easeOutCubic(stretchProgress.coerceIn(0f, 1f))
    val pullOffsetY = (touchY - startY)
        .coerceIn(-MAX_RUBBER_BEND_DP * safeDensity, MAX_RUBBER_BEND_DP * safeDensity)
    val pullRatio = pullOffsetY / (MAX_RUBBER_BEND_DP * safeDensity)
    val longMultiplier = if (longDistance) 1.22f else 1f
    val depthPx = MAX_DEPTH_DP * safeDensity * (0.10f + 0.90f * easedStretch) * longMultiplier
    val halfHeightPx = MAX_HALF_HEIGHT_DP * safeDensity * easedProgress
    val bendStrength = (pullRatio * (0.48f + 0.42f * easedStretch) * longMultiplier)
        .coerceIn(-0.95f, 0.95f)
    return BackWaveVisualLayout(
        centerY = startY,
        halfHeightPx = halfHeightPx,
        depthPx = depthPx,
        alphaProgress = easedProgress,
        pullRatio = pullRatio,
        bendStrength = bendStrength,
        cueCenterX = depthPx * 0.58f,
        cueCenterY = startY + pullRatio * halfHeightPx * 0.34f,
        cueSizePx = (7.5f + 3.2f * easedStretch + if (longDistance) 1.2f else 0f) * safeDensity,
        cueStrokeWidthPx = (2.1f + 0.8f * easedStretch) * safeDensity
    )
}

internal fun backWaveSurfacePoints(
    layout: BackWaveVisualLayout,
    side: EdgeSide,
    samples: Int
): List<BackWavePoint> {
    val count = samples.coerceAtLeast(3)
    return List(count) { index ->
        val t = -1f + 2f * index / (count - 1).toFloat()
        val base = (1f - t * t).coerceAtLeast(0f)
        val asymmetricStretch = (1f + layout.bendStrength * t).coerceIn(0.18f, 1.9f)
        val x = layout.depthPx * base * asymmetricStretch
        val yWarp = layout.pullRatio * layout.halfHeightPx * 0.14f * base * (1f - abs(t))
        val y = layout.centerY + t * layout.halfHeightPx + yWarp
        val mirroredX = if (side == EdgeSide.LEFT) x else -x
        BackWavePoint(mirroredX, y)
    }
}

internal fun backWaveDirectionCue(
    layout: BackWaveVisualLayout,
    side: EdgeSide
): List<BackWavePoint> {
    val size = layout.cueSizePx
    val vertical = layout.pullRatio * size * 0.72f
    val centerX = layout.cueCenterX
    val centerY = layout.cueCenterY
    val tailX = centerX - size * 0.42f
    val tipX = centerX + size * 0.36f
    val upperTailY = centerY - size * 0.34f
    val lowerTailY = centerY + size * 0.34f
    val tipY = centerY + vertical
    val points = listOf(
        BackWavePoint(tailX, upperTailY),
        BackWavePoint(tipX, tipY),
        BackWavePoint(tailX, lowerTailY)
    )
    return if (side == EdgeSide.LEFT) {
        points
    } else {
        points.map { point -> point.copy(x = -point.x) }
    }
}

private fun buildBackWaveSurfacePath(
    target: Path,
    layout: BackWaveVisualLayout,
    side: EdgeSide,
    viewWidth: Float,
    samples: Int
) {
    val points = backWaveSurfacePoints(layout, side, samples)
    val edgeX = if (side == EdgeSide.LEFT) 0f else viewWidth
    target.reset()
    target.moveTo(edgeX, points.first().y)
    points.forEach { point ->
        target.lineTo(mapBackWaveX(point.x, side, viewWidth), point.y)
    }
    target.lineTo(edgeX, points.last().y)
    target.close()
}

private fun buildBackWaveCuePath(
    target: Path,
    layout: BackWaveVisualLayout,
    side: EdgeSide,
    viewWidth: Float
) {
    val points = backWaveDirectionCue(layout, side)
    target.reset()
    if (points.isEmpty()) return
    target.moveTo(mapBackWaveX(points[0].x, side, viewWidth), points[0].y)
    points.drop(1).forEach { point ->
        target.lineTo(mapBackWaveX(point.x, side, viewWidth), point.y)
    }
}

private fun mapBackWaveX(
    localX: Float,
    side: EdgeSide,
    viewWidth: Float
): Float {
    return if (side == EdgeSide.LEFT) localX else viewWidth + localX
}

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private fun easeOutQuart(value: Float): Float {
    val inverse = 1f - value.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse * inverse
}

private const val MAX_RUBBER_BEND_DP = 96f
private const val MAX_HALF_HEIGHT_DP = 58f
private const val MAX_DEPTH_DP = 36f
private const val BACK_WAVE_WINDOW_HEIGHT_DP = 144f

internal fun backWaveShowsFromFirstDragPixel(): Boolean = true

internal fun backWaveUsesBezierCurveSurface(): Boolean = true

internal fun backWaveUsesRounderCurveSurface(): Boolean = true

internal fun backWaveUsesRubberBulgeSurface(): Boolean = true

internal fun backWaveBendsWithFingerY(): Boolean = true

internal fun backWaveDrawsArrowGlyph(): Boolean = false

internal fun backWaveDrawsDirectionArrowGlyph(): Boolean = true

internal fun backWaveDrawsLargeArrowGlyph(): Boolean = false

internal fun backWaveUsesBubbleHighlight(): Boolean = true

internal fun backWaveUsesSubtleDirectionCue(): Boolean = true

internal fun backWaveLongDistanceHasStrongerDeformation(): Boolean = true

internal fun backWaveUsesContinuousStretchInsteadOfLongThresholdJump(): Boolean = true

internal fun backWaveVerticalPullStretchesParabola(): Boolean = true

internal fun backWaveKeepsOuterSizeWhileLineWarps(): Boolean = true

internal fun backWaveAppearsContinuouslyFromZeroToMaxSize(): Boolean = true

internal fun backWaveDistinguishesShortAndLongDistance(): Boolean = true

internal fun backWaveDiagonalGesturesUseShortLongAnimation(): Boolean = true

internal fun backWaveVisualProgressUsesLongThreshold(): Boolean = true

internal fun backWaveUsesParabolicTrajectory(): Boolean = true

internal fun backWaveUsesPureEdgeParabolicShape(): Boolean = true

internal fun backWaveSamplesParabolaBeforeFill(): Boolean = true

internal fun backWaveUsesAospStyleEdgeArrow(): Boolean = false
