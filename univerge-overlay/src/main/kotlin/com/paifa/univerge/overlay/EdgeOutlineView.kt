package com.paifa.univerge.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.paifa.univerge.core.model.EdgeSide

@SuppressLint("ViewConstructor")
class EdgeOutlineView(
    context: Context,
    private val side: EdgeSide,
    private val zoneId: Int
) : View(context) {
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = edgeOverlayOutlineColorArgb(side, zoneId)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val strokeWidth = (OUTLINE_STROKE_WIDTH_DP * density)
            .coerceAtMost(minOf(width, height).toFloat())
        val strokeInset = strokeWidth / 2f
        val right = width.toFloat() - strokeInset
        val bottom = height.toFloat() - strokeInset
        if (right < strokeInset || bottom < strokeInset) return
        val cornerRadius = (OUTLINE_CORNER_RADIUS_DP * density).coerceAtMost(
            minOf(right - strokeInset, bottom - strokeInset) / 2f
        )
        outlinePaint.strokeWidth = strokeWidth
        canvas.drawRoundRect(
            strokeInset,
            strokeInset,
            right,
            bottom,
            cornerRadius,
            cornerRadius,
            outlinePaint
        )
    }

    private companion object {
        const val OUTLINE_STROKE_WIDTH_DP = 2f
        const val OUTLINE_CORNER_RADIUS_DP = 8f
    }
}

data class EdgeOutlinePlacement(
    val x: Int,
    val width: Int
)

fun edgeOutlinePlacement(
    side: EdgeSide,
    screenWidthPx: Int,
    outlineWidthPx: Int,
    edgeInsetPx: Int
): EdgeOutlinePlacement {
    val width = outlineWidthPx.coerceIn(1, screenWidthPx.coerceAtLeast(1))
    val safeInset = edgeInsetPx.coerceIn(0, (screenWidthPx - width).coerceAtLeast(0))
    return EdgeOutlinePlacement(
        x = when (side) {
            EdgeSide.LEFT -> safeInset
            EdgeSide.RIGHT -> (screenWidthPx - safeInset - width).coerceAtLeast(0)
        },
        width = width
    )
}
