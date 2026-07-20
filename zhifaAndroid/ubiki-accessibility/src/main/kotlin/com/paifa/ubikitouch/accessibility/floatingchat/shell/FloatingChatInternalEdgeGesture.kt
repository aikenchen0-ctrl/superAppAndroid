package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.leftRailTouchableWidthDp
import com.paifa.ubikitouch.accessibility.floatingchat.tools.rightRailWidthDp
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.gesture.SwipeClassifier
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.hypot

internal fun Modifier.floatingChatInternalEdgeGesture(
    touchTargetPx: Float,
    touchSlopPx: Float,
    shortThresholdPx: Float,
    longThresholdPx: Float,
    onGesture: (EdgeSide, GestureType, GestureData) -> Unit,
    onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean,
    onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCancel: () -> Unit
): Modifier {
    return pointerInput(
        touchTargetPx,
        touchSlopPx,
        shortThresholdPx,
        longThresholdPx,
        onGesture,
        onBackGestureProgress,
        onBackGestureCommit,
        onBackGestureEnd,
        onBackGestureCancel
    ) {
        val classifier = SwipeClassifier()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val side = edgeSideForPosition(
                x = down.position.x,
                width = size.width.toFloat(),
                touchTargetPx = touchTargetPx
            ) ?: return@awaitEachGesture

            val startX = down.position.x
            val startY = down.position.y
            var latestX = startX
            var latestY = startY
            var consumingGesture = false
            var latestBackProgress: BackGestureProgress? = null
            var sentBackCancel = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                latestX = change.position.x
                latestY = change.position.y
                val dx = latestX - startX
                val dy = latestY - startY
                val distance = hypot(dx, dy)
                val backProgress = BackGestureProgress.fromDelta(
                    side = side,
                    dx = dx,
                    dy = dy,
                    thresholdPx = shortThresholdPx,
                    longThresholdPx = longThresholdPx,
                    touchY = latestY,
                    startY = startY,
                    minimumDragDistancePx = 0f
                )

                if (backProgress != null) {
                    latestBackProgress = backProgress
                    sentBackCancel = false
                    onBackGestureProgress(side, backProgress)
                    if (distance > touchSlopPx || consumingGesture) {
                        consumingGesture = true
                        change.consume()
                    }
                } else if (latestBackProgress != null && !sentBackCancel) {
                    latestBackProgress = null
                    sentBackCancel = true
                    onBackGestureCancel()
                }

                val classified = classifier.classify(side, dx, dy)
                if (!consumingGesture && classified != null && distance >= shortThresholdPx) {
                    consumingGesture = true
                    change.consume()
                } else if (consumingGesture && classified != null) {
                    change.consume()
                }

                if (!change.pressed) {
                    if (consumingGesture) {
                        val data = GestureData(startX, startY, latestX, latestY)
                        val finalBackProgress = latestBackProgress
                        if (finalBackProgress != null) {
                            onBackGestureEnd(side, finalBackProgress)
                            if (!onBackGestureCommit(side, finalBackProgress, data)) {
                                onGesture(side, finalBackProgress.gestureType, data)
                            }
                        } else if (classified != null && distance >= shortThresholdPx) {
                            onGesture(side, classified, data)
                        }
                        change.consume()
                    } else if (latestBackProgress != null) {
                        onBackGestureCancel()
                    }
                    break
                }
            }
        }
    }
}

internal object FloatingChatInternalEdgeGestureDefaults {
    val TouchTargetDp: Dp = floatingChatInternalEdgeGestureTouchTargetDp().dp
    const val ShortThresholdDp: Int = 32
    const val LongThresholdDp: Int = 180
    const val ShortThresholdMinDp: Int = 8
    const val ShortThresholdMaxDp: Int = 120
    const val LongThresholdMinDeltaDp: Int = 8
    const val LongThresholdMaxDp: Int = 320
    const val ThresholdResponseRatio: Float = 0.70f
}

internal fun floatingChatServiceOverlayOperationsAreGuarded(): Boolean = true

internal fun floatingChatServiceOverlayRefreshRequiresInitializedControllers(): Boolean = true

internal fun floatingChatOverlayStaysAboveGestureOverlay(): Boolean = true

internal fun gestureOverlayIsBroughtToFrontAfterFloatingChatRecreated(): Boolean = false

internal fun floatingChatOverlayHandlesOwnEdgeGestures(): Boolean = true

internal fun floatingChatOverlayEdgeGestureConsumesPlainTaps(): Boolean = false

internal fun floatingChatInternalEdgeGestureObservesInitialPointerPass(): Boolean = false

internal fun floatingChatInternalEdgeGestureTouchTargetDp(): Int = 24

internal fun floatingChatInternalEdgeGestureCoversSideRails(): Boolean {
    return floatingChatInternalEdgeGestureTouchTargetDp() >= leftRailTouchableWidthDp() &&
        floatingChatInternalEdgeGestureTouchTargetDp() >= rightRailWidthDp()
}

internal fun floatingChatInternalEdgeGestureUsesEarlyHorizontalLock(): Boolean = false

internal fun floatingChatExpandedBottomGestureHandledInsideOverlay(): Boolean = true

private fun edgeSideForPosition(
    x: Float,
    width: Float,
    touchTargetPx: Float
): EdgeSide? {
    if (width <= 0f || touchTargetPx <= 0f) return null
    return when {
        x <= touchTargetPx -> EdgeSide.LEFT
        x >= width - touchTargetPx -> EdgeSide.RIGHT
        else -> null
    }
}
