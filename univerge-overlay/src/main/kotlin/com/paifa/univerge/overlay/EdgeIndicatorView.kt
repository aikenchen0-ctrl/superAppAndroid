package com.paifa.univerge.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.paifa.univerge.core.model.EdgeSide

@SuppressLint("ViewConstructor")
class EdgeIndicatorView(
    context: Context,
    private val side: EdgeSide,
    opacityPercent: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = edgeOverlayIndicatorColorArgb(opacityPercent)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val verticalInset = HANDLE_VERTICAL_INSET_DP * resources.displayMetrics.density
        if (height <= verticalInset * 2f) return
        canvas.drawRoundRect(
            0f,
            verticalInset,
            width.toFloat(),
            height - verticalInset,
            width.toFloat(),
            width.toFloat(),
            paint
        )
    }

    private companion object {
        const val HANDLE_VERTICAL_INSET_DP = 8f
    }
}

data class EdgeIndicatorPlacement(
    val x: Int,
    val width: Int
)

fun edgeIndicatorPlacement(
    side: EdgeSide,
    screenWidthPx: Int,
    indicatorWidthPx: Int
): EdgeIndicatorPlacement {
    val width = indicatorWidthPx.coerceIn(1, screenWidthPx.coerceAtLeast(1))
    return EdgeIndicatorPlacement(
        x = when (side) {
            EdgeSide.LEFT -> 0
            EdgeSide.RIGHT -> (screenWidthPx - width).coerceAtLeast(0)
        },
        width = width
    )
}
