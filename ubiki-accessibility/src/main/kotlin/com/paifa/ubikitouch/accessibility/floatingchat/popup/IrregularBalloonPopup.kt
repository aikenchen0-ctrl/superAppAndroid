package com.paifa.ubikitouch.accessibility.floatingchat.popup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Stable
internal class BalloonPopupState(initiallyVisible: Boolean = false) {
    var isVisible by mutableStateOf(initiallyVisible)
        private set

    fun show() { isVisible = true }
    fun dismiss() { isVisible = false }
    fun toggle() { isVisible = !isVisible }
}

@Composable
internal fun rememberBalloonPopupState(initiallyVisible: Boolean = false): BalloonPopupState =
    remember { BalloonPopupState(initiallyVisible) }

internal data class BalloonPlacement(val offset: Offset, val expandToRight: Boolean)

internal fun calculateBalloonPlacement(
    triggerBounds: Rect,
    popupSize: IntSize,
    viewportSize: IntSize,
    edgePaddingPx: Float,
    gapPx: Float
): BalloonPlacement {
    val expandToRight = triggerBounds.center.x <= viewportSize.width / 2f
    val desiredX = if (expandToRight) triggerBounds.right + gapPx else triggerBounds.left - gapPx - popupSize.width
    val maxX = (viewportSize.width - edgePaddingPx - popupSize.width).coerceAtLeast(edgePaddingPx)
    val x = desiredX.coerceIn(edgePaddingPx, maxX)
    val desiredY = triggerBounds.center.y - popupSize.height / 2f
    val maxY = (viewportSize.height - edgePaddingPx - popupSize.height).coerceAtLeast(edgePaddingPx)
    return BalloonPlacement(Offset(x, desiredY.coerceIn(edgePaddingPx, maxY)), expandToRight)
}

@Composable
internal fun IrregularBalloonPopup(
    state: BalloonPopupState,
    coordinateState: BalloonCoordinateState,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 8.dp,
    connectorGap: Dp = 12.dp,
    containerColor: Color = Color(0xFFF7F7F7),
    onDismiss: () -> Unit = {},
    trigger: @Composable (Modifier) -> Unit,
    content: @Composable BoxScope.(expandToRight: Boolean) -> Unit
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    @Suppress("UNUSED_VARIABLE")
    val coordinateVersion = coordinateState.version
    val triggerBoundsInRoot = coordinateState.triggerBounds ?: Rect.Zero
    val triggerBounds = triggerBoundsInRoot.translate(-viewportBounds.left, -viewportBounds.top)
    val placement = calculateBalloonPlacement(
        triggerBounds = triggerBounds,
        popupSize = popupSize,
        viewportSize = viewportSize,
        edgePaddingPx = with(density) { edgePadding.toPx() },
        gapPx = with(density) { connectorGap.toPx() }
    )

    Box(
        modifier
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
    ) {
        if (state.isVisible) {
            DisposableEffect(coordinateState) {
                onDispose { coordinateState.clearBubbles() }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            state.dismiss()
                            onDismiss()
                        }
                    }
            )
            BalloonConnector(coordinateState, Modifier.fillMaxSize())
        }
        trigger(
            Modifier
                .balloonTriggerCoordinates(coordinateState)
                .clickable {
                    val wasVisible = state.isVisible
                    state.toggle()
                    if (wasVisible) onDismiss()
                }
        )
        if (state.isVisible) {
            Box(
                Modifier
                    .offset { IntOffset(placement.offset.x.roundToInt(), placement.offset.y.roundToInt()) }
                    .onSizeChanged { popupSize = it }
                    .balloonBubbleCoordinates(coordinateState, PRIMARY_BALLOON_ID)
                    .irregularBalloonShape(containerColor)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            ) {
                content(placement.expandToRight)
            }
        }
    }
}

private const val PRIMARY_BALLOON_ID = "primary"

private fun Modifier.irregularBalloonShape(color: Color): Modifier = drawBehind {
    val inset = 1f
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return@drawBehind
    val path = Path().apply {
        moveTo(inset, h * 0.28f)
        cubicTo(inset, h * 0.08f, w * 0.12f, inset, w * 0.30f, inset)
        cubicTo(w * 0.52f, h * 0.04f, w * 0.70f, h * 0.01f, w * 0.86f, h * 0.10f)
        cubicTo(w - inset, h * 0.20f, w * 0.98f, h * 0.40f, w - inset, h * 0.56f)
        cubicTo(w * 0.98f, h * 0.80f, w * 0.86f, h - inset, w * 0.62f, h - inset)
        cubicTo(w * 0.38f, h * 0.97f, w * 0.18f, h, w * 0.07f, h * 0.86f)
        cubicTo(inset, h * 0.70f, w * 0.03f, h * 0.48f, inset, h * 0.28f)
        close()
    }
    drawPath(path, color)
}
