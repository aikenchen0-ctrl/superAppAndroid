package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType

@Composable
internal fun Modifier.floatingChatOverlayGestureBinding(
    edgeGestureShortThresholdDp: Int,
    edgeGestureLongThresholdDp: Int,
    onEdgeGesture: (EdgeSide, GestureType, GestureData) -> Unit,
    onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean,
    onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCancel: () -> Unit
): Modifier {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val edgeGestureTouchTargetPx = with(density) {
        FloatingChatInternalEdgeGestureDefaults.TouchTargetDp.toPx()
    }
    val edgeGestureShortThresholdPx = with(density) {
        edgeGestureShortThresholdDp
            .coerceIn(
                FloatingChatInternalEdgeGestureDefaults.ShortThresholdMinDp,
                FloatingChatInternalEdgeGestureDefaults.ShortThresholdMaxDp
            )
            .dp
            .toPx() * FloatingChatInternalEdgeGestureDefaults.ThresholdResponseRatio
    }
    val edgeGestureLongThresholdPx = with(density) {
        edgeGestureLongThresholdDp
            .coerceIn(
                edgeGestureShortThresholdDp + FloatingChatInternalEdgeGestureDefaults.LongThresholdMinDeltaDp,
                FloatingChatInternalEdgeGestureDefaults.LongThresholdMaxDp
            )
            .dp
            .toPx() * FloatingChatInternalEdgeGestureDefaults.ThresholdResponseRatio
    }
    val currentOnEdgeGesture by rememberUpdatedState(onEdgeGesture)
    val currentOnBackGestureProgress by rememberUpdatedState(onBackGestureProgress)
    val currentOnBackGestureCommit by rememberUpdatedState(onBackGestureCommit)
    val currentOnBackGestureEnd by rememberUpdatedState(onBackGestureEnd)
    val currentOnBackGestureCancel by rememberUpdatedState(onBackGestureCancel)

    return floatingChatInternalEdgeGesture(
        touchTargetPx = edgeGestureTouchTargetPx,
        touchSlopPx = viewConfiguration.touchSlop,
        shortThresholdPx = edgeGestureShortThresholdPx,
        longThresholdPx = edgeGestureLongThresholdPx,
        onGesture = currentOnEdgeGesture,
        onBackGestureProgress = currentOnBackGestureProgress,
        onBackGestureCommit = currentOnBackGestureCommit,
        onBackGestureEnd = currentOnBackGestureEnd,
        onBackGestureCancel = currentOnBackGestureCancel
    )
}
