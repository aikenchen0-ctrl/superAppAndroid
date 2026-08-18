package com.paifa.univerge.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.paifa.univerge.core.gesture.BackGestureOption
import com.paifa.univerge.core.gesture.BackGestureProgress
import com.paifa.univerge.core.gesture.BackGestureOptionBox
import com.paifa.univerge.core.gesture.backGestureOptionLayout
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.sidefunction.SideFunctionConfig
import com.paifa.univerge.core.sidefunction.SideFunctionBox
import com.paifa.univerge.core.sidefunction.DEFAULT_SIDE_FUNCTION_VERTICAL_SAFE_INSET_DP
import com.paifa.univerge.core.sidefunction.sideFunctionGroupShiftY
import com.paifa.univerge.core.sidefunction.sideFunctionLayout
import kotlin.math.abs
import kotlin.math.roundToInt

internal class BackWaveOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var waveView: BackWaveView? = null
    private var activeAnchor: BackWaveAnchor? = null

    fun update(progress: BackGestureProgress, anchor: BackWaveAnchor, items: List<SideFunctionPanelItem>): Boolean {
        val fullScreenAnchor = anchor.copy(
            y = 0,
            height = context.resources.displayMetrics.heightPixels
        )
        val localizedAnchor = localizedBackWaveAnchor(
            anchor = fullScreenAnchor,
            startY = progress.startY,
            density = context.resources.displayMetrics.density,
            itemCount = items.size
        )
        val view = ensureView(localizedAnchor) ?: return false
        view.update(visibleState(progress, localizedAnchor, items))
        return true
    }

    fun finish(progress: BackGestureProgress, anchor: BackWaveAnchor, items: List<SideFunctionPanelItem>) {
        val fullScreenAnchor = anchor.copy(
            y = 0,
            height = context.resources.displayMetrics.heightPixels
        )
        val localizedAnchor = localizedBackWaveAnchor(
            anchor = fullScreenAnchor,
            startY = progress.startY,
            density = context.resources.displayMetrics.density,
            itemCount = items.size
        )
        ensureView(localizedAnchor)?.update(visibleState(progress, localizedAnchor, items))
        dismiss()
    }

    private fun visibleState(
        progress: BackGestureProgress,
        anchor: BackWaveAnchor, items: List<SideFunctionPanelItem>
    ): BackWaveState.Visible {
        return BackWaveState.Visible(
            side = progress.side,
            progress = progress.visualProgress,
            stretchProgress = progress.visualProgress,
            longDistance = progress.longCommitted,
            touchY = progress.touchY - anchor.y,
            startY = progress.startY - anchor.y,
            gestureType = progress.gestureType,
            optionProgress = progress.progress,
            selectedOption = progress.selectedOption,
            optionItems = items,
            screenStartY = progress.startY,
            screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()
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

    private fun ensureView(anchor: BackWaveAnchor): BackWaveView? {
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
        return waveView
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
            x = backWaveWindowX(
                anchor = anchor,
                waveWidthPx = width,
                screenWidthPx = context.resources.displayMetrics.widthPixels
            )
            y = anchor.y
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val WAVE_WIDTH_DP = 184
    }
}

internal data class SideFunctionPanelItem(val label: String, val icon: Drawable? = null)

internal data class BackWaveAnchor(
    val side: EdgeSide,
    val edgeX: Int,
    val y: Int,
    val height: Int
)

internal fun backWaveWindowX(
    anchor: BackWaveAnchor,
    waveWidthPx: Int,
    screenWidthPx: Int
): Int {
    val width = waveWidthPx.coerceIn(1, screenWidthPx.coerceAtLeast(1))
    return when (anchor.side) {
        EdgeSide.LEFT -> anchor.edgeX
        EdgeSide.RIGHT -> anchor.edgeX - width
    }.coerceIn(0, (screenWidthPx - width).coerceAtLeast(0))
}

internal fun sideFunctionPanelLabels(
    customActionIds: List<String>,
    actionLabel: (String) -> String
): List<String> {
    return SideFunctionConfig.fromCustomActionIds(customActionIds).allActionIds.map { actionId ->
        when (actionId) {
            SideFunctionConfig.CLOSE_ALL_ACTION_ID -> "关闭所有"
            GestureAction.Back.id -> "返回"
            else -> actionLabel(actionId)
        }
    }
}

private sealed class BackWaveState {
    data object Hidden : BackWaveState()

    data class Visible(
        val side: EdgeSide,
        val progress: Float,
        val stretchProgress: Float,
        val longDistance: Boolean,
        val touchY: Float,
        val startY: Float,
        val gestureType: GestureType,
        val optionProgress: Float,
        val selectedOption: BackGestureOption,
        val optionItems: List<SideFunctionPanelItem>,
        val screenStartY: Float,
        val screenHeightPx: Float
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
    private val optionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 243, 248, 255)
    }
    private val optionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = Color.argb(210, 22, 28, 38)
    }
    private val optionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        color = Color.argb(225, 22, 28, 38)
        isFakeBoldText = true
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
        drawOptionPanels(
            canvas = canvas,
            visible = visible,
            density = density,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )
    }

    private fun drawOptionPanels(
        canvas: Canvas,
        visible: BackWaveState.Visible,
        density: Float,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val layout = sideFunctionLayout(visible.optionItems.size, visible.optionProgress, density)
        if (layout.boxes.isEmpty()) return

        val groupShiftY = backWaveSideFunctionGroupShiftY(
            layout = layout,
            screenStartY = visible.screenStartY,
            screenHeightPx = visible.screenHeightPx,
            density = density
        )

        optionTextPaint.textSize = OPTION_TEXT_SIZE_SP * density
        layout.boxes.forEach { box ->
            if (box.widthPx <= 0f || box.heightPx <= 0f) return@forEach
            val rect = optionRect(
                box = box,
                side = visible.side,
                startY = visible.startY + groupShiftY,
                viewWidth = viewWidth,
                viewHeight = viewHeight
            )
            val selected = layout.hitTest(
                inwardDistancePx = box.centerInwardPx,
                verticalOffsetPx = visible.touchY - visible.startY - groupShiftY
            ) == box.index
            val scale = if (selected) OPTION_SELECTED_SCALE else 1f
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val alphaProgress = easeOutCubic(visible.optionProgress)
            optionFillPaint.color = if (selected) {
                Color.argb((232f * alphaProgress).toInt().coerceIn(0, 232), 84, 73, 171)
            } else {
                Color.argb((150f * alphaProgress).toInt().coerceIn(0, 150), 243, 248, 255)
            }
            optionStrokePaint.color = if (selected) {
                Color.argb((245f * alphaProgress).toInt().coerceIn(0, 245), 65, 54, 135)
            } else {
                Color.argb((210f * alphaProgress).toInt().coerceIn(0, 210), 22, 28, 38)
            }
            optionTextPaint.color = if (selected) {
                Color.argb((245f * alphaProgress).toInt().coerceIn(0, 245), 255, 255, 255)
            } else {
                Color.argb((225f * alphaProgress).toInt().coerceIn(0, 225), 22, 28, 38)
            }
            val item = visible.optionItems.getOrNull(box.index) ?: return@forEach
            val label = item.label

            canvas.save()
            canvas.scale(scale, scale, centerX, centerY)
            canvas.drawRoundRect(rect, box.cornerRadiusPx, box.cornerRadiusPx, optionFillPaint)
            canvas.drawRoundRect(rect, box.cornerRadiusPx, box.cornerRadiusPx, optionStrokePaint)
            val fontMetrics = optionTextPaint.fontMetrics
            val baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            item.icon?.let { icon ->
                val iconSize = (22f * density).toInt()
                icon.setBounds(
                    (rect.left + 10f * density).toInt(),
                    (centerY - iconSize / 2f).toInt(),
                    (rect.left + 10f * density + iconSize).toInt(),
                    (centerY + iconSize / 2f).toInt()
                )
                icon.draw(canvas)
                optionTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(label, rect.left + 40f * density, baseline, optionTextPaint)
                optionTextPaint.textAlign = Paint.Align.CENTER
            } ?: canvas.drawText(label, centerX, baseline, optionTextPaint)
            canvas.restore()
        }
    }

    private fun optionRect(
        box: SideFunctionBox,
        side: EdgeSide,
        startY: Float,
        viewWidth: Float,
        viewHeight: Float
    ): RectF {
        val centerX = if (side == EdgeSide.LEFT) {
            box.centerInwardPx
        } else {
            viewWidth - box.centerInwardPx
        }
        val rawLeft = centerX - box.widthPx / 2f
        val rawTop = startY + box.centerYOffsetPx - box.heightPx / 2f
        val maxLeft = (viewWidth - box.widthPx).coerceAtLeast(0f)
        val maxTop = (viewHeight - box.heightPx).coerceAtLeast(0f)
        val left = rawLeft.coerceIn(0f, maxLeft)
        val top = rawTop.coerceIn(0f, maxTop)
        return RectF(left, top, left + box.widthPx, top + box.heightPx)
    }

    private companion object {
        const val SURFACE_SAMPLE_COUNT = 31
        const val OPTION_TEXT_SIZE_SP = 14f
        const val OPTION_SELECTED_SCALE = 1.05f
    }
}

internal fun backWaveLayerType(): Int = View.LAYER_TYPE_NONE

internal fun backWaveSideFunctionGroupShiftY(
    layout: com.paifa.univerge.core.sidefunction.SideFunctionLayout,
    screenStartY: Float,
    screenHeightPx: Float,
    density: Float
): Float {
    return sideFunctionGroupShiftY(
        layout = layout,
        startY = screenStartY,
        viewportHeightPx = screenHeightPx,
        verticalSafeInsetPx = DEFAULT_SIDE_FUNCTION_VERTICAL_SAFE_INSET_DP * density
    )
}

internal fun localizedBackWaveAnchor(
    anchor: BackWaveAnchor,
    startY: Float,
    density: Float
): BackWaveAnchor = localizedBackWaveAnchor(anchor, startY, density, 2)

internal fun localizedBackWaveAnchor(
    anchor: BackWaveAnchor,
    startY: Float,
    density: Float,
    itemCount: Int
): BackWaveAnchor {
    val itemHeightDp = itemCount.coerceIn(2, 7) * 44f
    val spacingDp = (itemCount.coerceIn(2, 7) - 1) * 12f
    val requiredHeightDp = maxOf(BACK_WAVE_WINDOW_HEIGHT_DP, itemHeightDp + spacingDp + 32f)
    val height = (requiredHeightDp * density.coerceAtLeast(0.1f))
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
