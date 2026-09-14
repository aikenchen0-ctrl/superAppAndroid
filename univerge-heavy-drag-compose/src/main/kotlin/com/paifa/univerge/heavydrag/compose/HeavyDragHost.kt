package com.paifa.univerge.heavydrag.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import com.paifa.univerge.heavydrag.android.HeavyDragRuntime
import com.paifa.univerge.heavydrag.core.HeavyDragState

/**
 * Compose Root 的触摸观察入口。
 *
 * `motionEventSpy` never claims ACTION_DOWN, so ordinary clicks and scrolling
 * remain owned by the actual child. Once the classifier confirms a heavy drag,
 * the companion pointer filter consumes subsequent changes to keep the drag
 * owner stable.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun HeavyDragHost(
    runtime: HeavyDragRuntime,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var session by remember(runtime) { mutableStateOf(runtime.coordinator.activeSession) }
    val coordinateSpace = remember(runtime) { HeavyDragCoordinateSpace() }
    var width by remember(runtime) { mutableStateOf(0) }
    var height by remember(runtime) { mutableStateOf(0) }

    DisposableEffect(runtime) {
        runtime.setStateListener { next -> session = next }
        runtime.start()
        onDispose {
            runtime.setStateListener(null)
            runtime.stop()
        }
    }

    CompositionLocalProvider(
        LocalHeavyDragSession provides session,
        LocalHeavyDragCoordinator provides runtime.coordinator,
        LocalHeavyDragCoordinateSpace provides coordinateSpace
    ) {
        Box(
            modifier = modifier
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    coordinateSpace.origin = bounds.topLeft
                    width = coordinates.size.width
                    height = coordinates.size.height
                }
                .motionEventSpy { event ->
                    runtime.handleMotionEvent(event, width, height)
                }
                .pointerInput(runtime) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (runtime.coordinator.state == HeavyDragState.DRAGGING) {
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    }
                },
            content = content
        )
    }
}
