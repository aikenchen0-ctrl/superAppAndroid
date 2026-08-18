package com.paifa.univerge.accessibility.floatingchat.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.chat.leftRailTouchableWidthDp
import com.paifa.univerge.accessibility.floatingchat.tools.rightRailWidthDp
import com.paifa.univerge.core.gesture.BackGestureProgress
import com.paifa.univerge.core.gesture.SwipeClassifier
import com.paifa.univerge.core.gesture.hitTestBackGestureOption
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.model.sanitizeEdgeInsetDp
import kotlin.math.hypot

internal fun Modifier.floatingChatInternalEdgeGesture(
    density: Float,
    touchTargetPx: Float,
    leftEdgeConfigs: List<EdgeZoneConfig>,
    rightEdgeConfigs: List<EdgeZoneConfig>,
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
        density,
        touchTargetPx,
        leftEdgeConfigs,
        rightEdgeConfigs,
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
            val zone = floatingChatEdgeZoneForPosition(
                x = down.position.x,
                y = down.position.y,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                touchTargetPx = touchTargetPx,
                density = density,
                leftConfigs = leftEdgeConfigs,
                rightConfigs = rightEdgeConfigs
            ) ?: return@awaitEachGesture
            val side = zone.side

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
                val baseProgress = BackGestureProgress.fromDelta(
                    side = side,
                    dx = dx,
                    dy = dy,
                    thresholdPx = shortThresholdPx,
                    longThresholdPx = longThresholdPx,
                    touchY = latestY,
                    startY = startY,
                    minimumDragDistancePx = 0f
                )
                val backProgress = baseProgress?.copy(
                    selectedOption = hitTestBackGestureOption(
                        side = side,
                        startX = startX,
                        startY = startY,
                        touchX = latestX,
                        touchY = latestY,
                        progress = baseProgress.progress,
                        density = density,
                        viewportHeightPx = size.height.toFloat()
                    )
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

internal fun floatingChatEdgeZoneForPosition(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    touchTargetPx: Float,
    density: Float,
    leftConfigs: List<EdgeZoneConfig>,
    rightConfigs: List<EdgeZoneConfig>
): EdgeZoneConfig? {
    if (width <= 0f || height <= 0f || touchTargetPx <= 0f || density <= 0f) return null
    return (leftConfigs + rightConfigs).firstOrNull { config ->
        val sanitized = config.sanitized()
        if (!sanitized.enabled) return@firstOrNull false
        val insetPx = sanitizeEdgeInsetDp(sanitized.edgeInsetDp) * density
        val horizontalMatch = when (sanitized.side) {
            EdgeSide.LEFT -> {
                val left = insetPx.coerceIn(0f, width)
                val right = (left + touchTargetPx).coerceAtMost(width)
                x in left..right
            }
            EdgeSide.RIGHT -> {
                val right = (width - insetPx).coerceIn(0f, width)
                val left = (right - touchTargetPx).coerceAtLeast(0f)
                x in left..right
            }
        }
        val top = height * sanitized.topInsetPercent / 100f
        val bottom = height * (100 - sanitized.bottomInsetPercent) / 100f
        horizontalMatch && y in top..bottom
    }
}
