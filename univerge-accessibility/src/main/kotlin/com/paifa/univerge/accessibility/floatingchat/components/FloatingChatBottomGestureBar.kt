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
import kotlinx.coroutines.withTimeoutOrNull
import com.paifa.univerge.accessibility.BottomGestureBarGestureType
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.defaultBottomGestureBarWidthDp
import com.paifa.univerge.accessibility.bottomGestureBarTouchHeightDp
import com.paifa.univerge.accessibility.sanitizeBottomGestureBarWidthDp
import com.paifa.univerge.core.gesture.runtime.BottomBarConfig
import com.paifa.univerge.core.gesture.runtime.BottomGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.BottomGestureThresholds
import com.paifa.univerge.core.gesture.runtime.GestureSignal
import com.paifa.univerge.core.gesture.runtime.PointerSample
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.model.GestureData

@Composable
internal fun FloatingChatExpandedBottomGestureBar(
    onGesture: (BottomGestureBarGestureType, GestureData) -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = defaultBottomGestureBarWidthDp(),
    enableSwipeUpHold: Boolean = false
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
                onGesture = { gesture, data -> currentOnGesture(gesture, data) },
                enableSwipeUpHold = enableSwipeUpHold
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
    onGesture: (BottomGestureBarGestureType, GestureData) -> Unit,
    enableSwipeUpHold: Boolean
): Modifier = pointerInput(enableSwipeUpHold) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val safeDensity = density.coerceAtLeast(0.01f)
        val barWidthDp = size.width / safeDensity
        val barHeightDp = size.height / safeDensity
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(
                screenWidthDp = barWidthDp,
                screenHeightDp = barHeightDp,
                widthDp = barWidthDp,
                heightDp = barHeightDp
            ),
            actions = emptyMap(),
            enabledGestures = if (enableSwipeUpHold) {
                BOTTOM_GESTURES_WITH_UPWARD_HOLD
            } else {
                BOTTOM_GESTURES
            },
            thresholds = BottomGestureThresholds(
                minSwipeDistanceDp = 56f / safeDensity,
                slopDp = 6f / safeDensity,
                upwardHoldDurationMs = 500L
            )
        )
        var released = false
        var committed = false
        var logicalTimeMillis = down.uptimeMillis
        onPressedChange(true)
        down.consume()
        recognizer.onDown(
            PointerSample(
                xDp = down.position.x / safeDensity,
                yDp = down.position.y / safeDensity,
                timeMillis = down.uptimeMillis,
                pointerId = down.id.value.toInt()
            )
        )

        fun dispatch(signal: GestureSignal) {
            if (committed || signal !is GestureSignal.Commit) return
            committed = true
            val type = signal.gesture.toBottomGestureBarType()
            if (type == null) return
            onGesture(type, signal.data.toPixels(safeDensity))
        }

        try {
            while (true) {
                val event = if (committed) {
                    awaitPointerEvent(PointerEventPass.Initial)
                } else {
                    withTimeoutOrNull(BOTTOM_GESTURE_TIMER_POLL_MS) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                }
                if (event == null) {
                    logicalTimeMillis += BOTTOM_GESTURE_TIMER_POLL_MS
                    dispatch(recognizer.onHoldTimer(logicalTimeMillis))
                    continue
                }

                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    recognizer.onCancel()
                    released = true
                    onPressedChange(false)
                    break
                }
                logicalTimeMillis = maxOf(logicalTimeMillis, change.uptimeMillis)
                val changedToUp = change.changedToUp()
                if (event.changes.any { it.id != down.id && it.pressed }) {
                    // Bottom actions are single-pointer transactions. A second
                    // pointer cancels the preview instead of allowing a later UP
                    // from the first pointer to commit a tap or swipe.
                    change.consume()
                    recognizer.onCancel()
                    onPressedChange(false)
                    released = true
                    break
                }
                val xDp = change.position.x / safeDensity
                val yDp = change.position.y / safeDensity
                change.consume()
                if (!change.pressed) {
                    released = true
                    onPressedChange(false)
                    if (changedToUp) {
                        dispatch(
                            recognizer.onUp(
                                xDp = xDp,
                                yDp = yDp,
                                timeMillis = logicalTimeMillis,
                                pointerId = change.id.value.toInt()
                            )
                        )
                    } else {
                        recognizer.onCancel()
                    }
                    break
                }

                dispatch(
                    recognizer.onMove(
                        xDp = xDp,
                        yDp = yDp,
                        timeMillis = logicalTimeMillis,
                        pointerId = change.id.value.toInt()
                    )
                )
                if (!committed) {
                    dispatch(recognizer.onHoldTimer(logicalTimeMillis))
                }
            }
        } finally {
            if (!released) {
                recognizer.onCancel()
                onPressedChange(false)
            }
        }
    }
}

private fun GestureType.toBottomGestureBarType(): BottomGestureBarGestureType? = when (this) {
    GestureType.TAP -> BottomGestureBarGestureType.Tap
    GestureType.SWIPE_UP -> BottomGestureBarGestureType.SwipeUp
    GestureType.SWIPE_UP_HOLD -> BottomGestureBarGestureType.SwipeUpHold
    GestureType.SWIPE_LEFT,
    GestureType.SWIPE_RIGHT -> BottomGestureBarGestureType.SwipeHorizontal
    GestureType.LONG_PRESS -> BottomGestureBarGestureType.LongPress
    else -> null
}

private fun GestureData.toPixels(density: Float): GestureData = GestureData(
    startX = startX * density,
    startY = startY * density,
    endX = endX * density,
    endY = endY * density,
    gestureId = gestureId,
    snapshotVersion = snapshotVersion,
    zoneId = zoneId
)

private const val FloatingChatBottomGestureBarVisualWidthDp = 92
private const val FloatingChatBottomGestureBarVisualHeightDp = 5
private const val BOTTOM_GESTURE_TIMER_POLL_MS = 50L

private val BOTTOM_GESTURES: Set<GestureType> = setOf(
    GestureType.TAP,
    GestureType.SWIPE_UP,
    GestureType.SWIPE_LEFT,
    GestureType.SWIPE_RIGHT,
    GestureType.LONG_PRESS
)

private val BOTTOM_GESTURES_WITH_UPWARD_HOLD: Set<GestureType> =
    BOTTOM_GESTURES + GestureType.SWIPE_UP_HOLD
