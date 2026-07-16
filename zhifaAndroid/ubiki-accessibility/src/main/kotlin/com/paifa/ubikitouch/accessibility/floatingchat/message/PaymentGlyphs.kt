package com.paifa.ubikitouch.accessibility.floatingchat.message

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.paifa.ubikitouch.accessibility.OverlayTokens

@Composable
internal fun RedPacketPaymentGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = 5.dp.toPx()
        drawRoundRect(Color(0xFFEF3E46), cornerRadius = CornerRadius(radius, radius))
        drawRoundRect(Color(0xFFDE3441), topLeft = Offset(0f, size.height * 0.44f), size = Size(size.width, size.height * 0.56f), cornerRadius = CornerRadius(radius, radius))
        drawPath(Path().apply { moveTo(0f, size.height * 0.44f); quadraticTo(size.width * 0.5f, size.height * 0.62f, size.width, size.height * 0.44f); lineTo(size.width, size.height * 0.56f); quadraticTo(size.width * 0.5f, size.height * 0.75f, 0f, size.height * 0.56f); close() }, Color(0xFFE1474A))
        drawCircle(Color(0xFFFFD553), size.minDimension * 0.17f, Offset(size.width * 0.5f, size.height * 0.47f))
        drawContext.canvas.nativeCanvas.drawText("楼", size.width * 0.5f, size.height * 0.52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color(0xFFD89123).toArgb(); textAlign = Paint.Align.CENTER; textSize = size.minDimension * 0.22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
    }
}

@Composable
internal fun TransferPaymentGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val paintColor = OverlayTokens.paymentCardText
        drawCircle(paintColor, size.minDimension * 0.46f, Offset(size.width / 2f, size.height / 2f), style = Stroke(width = stroke, cap = StrokeCap.Round))
        val path = Path().apply { moveTo(size.width * 0.67f, size.height * 0.31f); lineTo(size.width * 0.31f, size.height * 0.31f); lineTo(size.width * 0.22f, size.height * 0.43f); moveTo(size.width * 0.31f, size.height * 0.57f); lineTo(size.width * 0.69f, size.height * 0.57f); lineTo(size.width * 0.78f, size.height * 0.45f) }
        drawPath(path, paintColor, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
