package com.paifa.univerge.heavydrag.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragTarget
import com.paifa.univerge.heavydrag.core.HeavyTargetEvent
import com.paifa.univerge.heavydrag.core.HeavyTargetMode
import com.paifa.univerge.heavydrag.core.HeavyRect

/** 声明显式 Drop Zone 或 Overlap Target。 */
fun Modifier.heavyDropTarget(
    coordinator: HeavyDragCoordinator,
    targetId: String,
    mode: HeavyTargetMode = HeavyTargetMode.DROP_ZONE,
    enabled: Boolean = true,
    priority: Int = 0,
    zIndex: Float = 0f,
    overlapEnterThreshold: Float = 0.20f,
    overlapExitThreshold: Float = 0.10f,
    accepts: (Any?) -> Boolean = { true },
    onEnter: (HeavyTargetEvent) -> Unit = {},
    onOver: (HeavyTargetEvent) -> Unit = {},
    onExit: (HeavyTargetEvent) -> Unit = {},
    onDrop: (HeavyTargetEvent) -> Unit = {},
    onOverlapEnter: (HeavyTargetEvent) -> Unit = {},
    onOverlap: (HeavyTargetEvent) -> Unit = {},
    onOverlapExit: (HeavyTargetEvent) -> Unit = {},
    onOverlapCommit: (HeavyTargetEvent) -> Unit = {}
): Modifier = composed {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestPriority by rememberUpdatedState(priority)
    val latestZIndex by rememberUpdatedState(zIndex)
    val latestAccepts by rememberUpdatedState(accepts)
    val latestOnEnter by rememberUpdatedState(onEnter)
    val latestOnOver by rememberUpdatedState(onOver)
    val latestOnExit by rememberUpdatedState(onExit)
    val latestOnDrop by rememberUpdatedState(onDrop)
    val latestOnOverlapEnter by rememberUpdatedState(onOverlapEnter)
    val latestOnOverlap by rememberUpdatedState(onOverlap)
    val latestOnOverlapExit by rememberUpdatedState(onOverlapExit)
    val latestOnOverlapCommit by rememberUpdatedState(onOverlapCommit)
    val localSpace = LocalHeavyDragCoordinateSpace.current

    DisposableEffect(
        coordinator,
        targetId,
        mode,
        enabled,
        priority,
        zIndex,
        overlapEnterThreshold,
        overlapExitThreshold
    ) {
        val target = HeavyDragTarget(
            id = targetId,
            bounds = HeavyRect(0f, 0f, 0f, 0f),
            mode = mode,
            enabled = latestEnabled,
            priority = latestPriority,
            zIndex = latestZIndex,
            accepts = { value -> latestAccepts(value) },
            overlapEnterThreshold = overlapEnterThreshold,
            overlapExitThreshold = overlapExitThreshold,
            onEnter = { event -> latestOnEnter(event) },
            onOver = { event -> latestOnOver(event) },
            onExit = { event -> latestOnExit(event) },
            onDrop = { event -> latestOnDrop(event) },
            onOverlapEnter = { event -> latestOnOverlapEnter(event) },
            onOverlap = { event -> latestOnOverlap(event) },
            onOverlapExit = { event -> latestOnOverlapExit(event) },
            onOverlapCommit = { event -> latestOnOverlapCommit(event) }
        )
        val registration = coordinator.registerTarget(target)
        onDispose { registration.dispose() }
    }

    this.onGloballyPositioned { coordinates ->
        coordinator.updateTargetBounds(targetId, localSpace.toLocal(coordinates.boundsInRoot()))
    }
}

fun Modifier.heavyOverlapTarget(
    coordinator: HeavyDragCoordinator,
    targetId: String,
    enabled: Boolean = true,
    priority: Int = 0,
    zIndex: Float = 0f,
    overlapEnterThreshold: Float = 0.20f,
    overlapExitThreshold: Float = 0.10f,
    accepts: (Any?) -> Boolean = { true },
    onEnter: (HeavyTargetEvent) -> Unit = {},
    onOver: (HeavyTargetEvent) -> Unit = {},
    onExit: (HeavyTargetEvent) -> Unit = {},
    onCommit: (HeavyTargetEvent) -> Unit = {}
): Modifier = heavyDropTarget(
    coordinator = coordinator,
    targetId = targetId,
    mode = HeavyTargetMode.OVERLAP,
    enabled = enabled,
    priority = priority,
    zIndex = zIndex,
    overlapEnterThreshold = overlapEnterThreshold,
    overlapExitThreshold = overlapExitThreshold,
    accepts = accepts,
    onOverlapEnter = onEnter,
    onOverlap = onOver,
    onOverlapExit = onExit,
    onOverlapCommit = onCommit
)
