package com.paifa.univerge.accessibility.floatingchat.components

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.BottomGestureBarGestureType
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.defaultBottomGestureBarWidthDp
import com.paifa.univerge.accessibility.bottomGestureBarTouchHeightDp
import com.paifa.univerge.accessibility.resolveBottomGestureBarGestureType
import com.paifa.univerge.accessibility.sanitizeBottomGestureBarWidthDp
import com.paifa.univerge.core.gesture.runtime.nextGestureSessionId
import com.paifa.univerge.core.model.GestureData
import kotlin.math.abs

@Composable
internal fun FloatingChatExpandedBottomGestureBar(
    onGesture: (BottomGestureBarGestureType, GestureData) -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = defaultBottomGestureBarWidthDp()
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnGesture by rememberUpdatedState(onGesture)
    val configuredWidthDp = sanitizeBottomGestureBarWidthDp(widthDp)
    Box(
        modifier = modifier
            .width(configuredWidthDp.dp)
            .height(bottomGestureBarTouchHeightDp().dp)
            .floatingChatBottomGestureBarInput(
                onPressedChange = { nextPressed -> pressed = nextPressed },
                onGesture = { gesture, data -> currentOnGesture(gesture, data) }
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
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val gestureId = nextGestureSessionId()
        val startX = down.position.x
        val startY = down.position.y
        var latestX = startX
        var latestY = startY
        var lastMotionX = startX
        var lastMotionY = startY
        var lastMovementAtMillis = down.uptimeMillis
        var released = false
        onPressedChange(true)
        down.consume()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            latestX = change.position.x
            latestY = change.position.y
            val changedToUp = change.changedToUp()
            if (event.changes.any { it.id != down.id && it.pressed }) {
                // Bottom actions are single-pointer transactions. A second
                // pointer cancels the preview instead of allowing a later UP
                // from the first pointer to commit a tap or swipe.
                change.consume()
                onPressedChange(false)
                released = true
                break
            }
            if (abs(latestX - lastMotionX) >= FloatingChatBottomGestureBarMotionSlopPx || abs(latestY - lastMotionY) >= FloatingChatBottomGestureBarMotionSlopPx) {
                lastMotionX = latestX
                lastMotionY = latestY
                lastMovementAtMillis = change.uptimeMillis
            }
            change.consume()
            if (!change.pressed) {
                released = true
                onPressedChange(false)
                if (changedToUp) {
                    onGesture(
                        resolveBottomGestureBarGestureType(
                            latestX - startX,
                            latestY - startY,
                            change.uptimeMillis - down.uptimeMillis,
                            change.uptimeMillis - lastMovementAtMillis
                        ),
                        GestureData(
                            startX = startX,
                            startY = startY,
                            endX = latestX,
                            endY = latestY,
                            gestureId = gestureId
                        )
                    )
                }
                break
            }
        }
        if (!released) onPressedChange(false)
    }
}

private const val FloatingChatBottomGestureBarVisualWidthDp = 92
private const val FloatingChatBottomGestureBarVisualHeightDp = 5
private const val FloatingChatBottomGestureBarMotionSlopPx = 6f
