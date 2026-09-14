package com.paifa.univerge.heavydrag.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import com.paifa.univerge.heavydrag.core.HeavyClickEvent
import com.paifa.univerge.heavydrag.core.HeavyDragCancelEvent
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragEndEvent
import com.paifa.univerge.heavydrag.core.HeavyDragMoveEvent
import com.paifa.univerge.heavydrag.core.HeavyDragStartEvent
import com.paifa.univerge.heavydrag.core.HeavyDragSource
import com.paifa.univerge.heavydrag.core.HeavyRect

/** 声明一个可重触拖动源；分类和手势所有权由 Root Host 统一管理。 */
fun Modifier.heavyDraggable(
    coordinator: HeavyDragCoordinator,
    sourceId: String,
    payload: Any? = null,
    enabled: Boolean = true,
    priority: Int = 0,
    zIndex: Float = 0f,
    applyDragTranslation: Boolean = true,
    resetTranslationOnEnd: Boolean = true,
    onClick: (HeavyClickEvent) -> Unit = {},
    onDragStart: (HeavyDragStartEvent) -> Unit = {},
    onDrag: (HeavyDragMoveEvent) -> Unit = {},
    onDragEnd: (HeavyDragEndEvent) -> Unit = {},
    onDragCancel: (HeavyDragCancelEvent) -> Unit = {}
): Modifier = composed {
    var translation by remember(sourceId) { mutableStateOf(Offset.Zero) }
    val latestPayload by rememberUpdatedState(payload)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestPriority by rememberUpdatedState(priority)
    val latestZIndex by rememberUpdatedState(zIndex)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    val registration = remember(coordinator, sourceId) {
        mutableStateOf<com.paifa.univerge.heavydrag.core.HeavyRegistration?>(null)
    }
    DisposableEffect(coordinator, sourceId, payload, enabled, priority, zIndex) {
        val source = HeavyDragSource(
            id = sourceId,
            bounds = HeavyRect(0f, 0f, 0f, 0f),
            payload = latestPayload,
            enabled = latestEnabled,
            priority = latestPriority,
            zIndex = latestZIndex,
            onClick = { event -> latestOnClick(event) },
            onDragStart = { event ->
                if (applyDragTranslation) {
                    translation = Offset(
                        event.currentBounds.left - event.initialBounds.left,
                        event.currentBounds.top - event.initialBounds.top
                    )
                }
                latestOnDragStart(event)
            },
            onDrag = { event ->
                if (applyDragTranslation) {
                    translation = Offset(
                        event.currentBounds.left - event.initialBounds.left,
                        event.currentBounds.top - event.initialBounds.top
                    )
                }
                latestOnDrag(event)
            },
            onDragEnd = { event ->
                latestOnDragEnd(event)
                if (applyDragTranslation && resetTranslationOnEnd) translation = Offset.Zero
            },
            onDragCancel = { event ->
                latestOnDragCancel(event)
                if (applyDragTranslation) translation = Offset.Zero
            }
        )
        registration.value = coordinator.registerSource(source)
        onDispose {
            registration.value?.dispose()
            registration.value = null
        }
    }

    val localSpace = LocalHeavyDragCoordinateSpace.current
    this
        .onGloballyPositioned { coordinates ->
            coordinator.updateSourceBounds(sourceId, localSpace.toLocal(coordinates.boundsInRoot()))
        }
        .graphicsLayer {
            translationX = if (applyDragTranslation) translation.x else 0f
            translationY = if (applyDragTranslation) translation.y else 0f
        }
}
