package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.BottomGestureBarGestureType
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.defaultBottomGestureBarWidthDp
import com.paifa.ubikitouch.accessibility.resolveBottomGestureBarGestureType
import com.paifa.ubikitouch.core.model.GestureData
import kotlin.math.abs

@Composable
internal fun FloatingChatExpandedBottomGestureBar(
    onGesture: (BottomGestureBarGestureType, GestureData) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(defaultBottomGestureBarWidthDp().dp)
            .height(FloatingChatBottomGestureBarHeightDp.dp)
            .floatingChatBottomGestureBarInput(
                onPressedChange = { nextPressed -> pressed = nextPressed },
                onGesture = onGesture
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(FloatingChatBottomGestureBarVisualWidthDp.dp)
                .height(FloatingChatBottomGestureBarVisualHeightDp.dp)
                .clip(RoundedCornerShape(50))
                .background(OverlayTokens.primaryText.copy(alpha = if (pressed) 0.82f else 0.58f))
        )
    }
}

private fun Modifier.floatingChatBottomGestureBarInput(
    onPressedChange: (Boolean) -> Unit,
    onGesture: (BottomGestureBarGestureType, GestureData) -> Unit
): Modifier = pointerInput(onGesture) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startX = down.position.x
        val startY = down.position.y
        var latestX = startX
        var latestY = startY
        var lastMotionX = startX
        var lastMotionY = startY
        var lastMovementAtMillis = down.uptimeMillis
        var gestureDispatched = false
        onPressedChange(true)
        down.consume()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            latestX = change.position.x
            latestY = change.position.y
            if (abs(latestX - lastMotionX) >= FloatingChatBottomGestureBarMotionSlopPx || abs(latestY - lastMotionY) >= FloatingChatBottomGestureBarMotionSlopPx) {
                lastMotionX = latestX
                lastMotionY = latestY
                lastMovementAtMillis = change.uptimeMillis
            }
            if (!gestureDispatched) {
                val gestureType = resolveBottomGestureBarGestureType(latestX - startX, latestY - startY, change.uptimeMillis - down.uptimeMillis, change.uptimeMillis - lastMovementAtMillis)
                if (gestureType == BottomGestureBarGestureType.SwipeUpHold) {
                    gestureDispatched = true
                    onGesture(gestureType, GestureData(startX, startY, latestX, latestY))
                }
            }
            change.consume()
            if (!change.pressed) {
                onPressedChange(false)
                if (!gestureDispatched) {
                    onGesture(resolveBottomGestureBarGestureType(latestX - startX, latestY - startY, change.uptimeMillis - down.uptimeMillis, change.uptimeMillis - lastMovementAtMillis), GestureData(startX, startY, latestX, latestY))
                }
                break
            }
        }
        onPressedChange(false)
    }
}

private const val FloatingChatBottomGestureBarHeightDp = 30
private const val FloatingChatBottomGestureBarVisualWidthDp = 92
private const val FloatingChatBottomGestureBarVisualHeightDp = 5
private const val FloatingChatBottomGestureBarMotionSlopPx = 6f
