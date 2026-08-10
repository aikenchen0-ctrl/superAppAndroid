package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

@Composable
internal fun FloatingChatLocationGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val center = Offset(size.width * 0.5f, size.height * 0.43f)
        drawCircle(OverlayTokens.locationPin, size.minDimension * 0.30f, center, style = stroke)
        drawCircle(OverlayTokens.locationPin, size.minDimension * 0.08f, center)
        drawLine(OverlayTokens.locationPin, Offset(size.width * 0.5f, size.height * 0.73f), Offset(size.width * 0.5f, size.height * 0.92f), stroke.width, StrokeCap.Round)
    }
}
