package com.paifa.univerge.accessibility.floatingchat.popup

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.paifa.univerge.accessibility.floatingchat.chat.configureConnectorPaint
import com.paifa.univerge.accessibility.floatingchat.chat.createChatConnectorLine
import com.paifa.univerge.core.model.FloatingChatConnectionTarget

internal class BalloonCoordinateState {
    var version by mutableIntStateOf(0)
        private set
    var triggerBounds: Rect? = null
        private set
    val bubbleBounds = mutableMapOf<String, Rect>()

    fun updateTrigger(bounds: Rect) {
        if (triggerBounds != bounds) {
            triggerBounds = bounds
            version += 1
        }
    }

    fun updateBubble(id: String, bounds: Rect) {
        if (bubbleBounds[id] != bounds) {
            bubbleBounds[id] = bounds
            version += 1
        }
    }

    fun removeBubble(id: String) {
        if (bubbleBounds.remove(id) != null) version += 1
    }

    fun clearBubbles() {
        if (bubbleBounds.isNotEmpty()) {
            bubbleBounds.clear()
            version += 1
        }
    }
}

internal fun Modifier.balloonTriggerCoordinates(state: BalloonCoordinateState): Modifier =
    onGloballyPositioned { state.updateTrigger(it.boundsInRoot()) }

internal fun Modifier.balloonBubbleCoordinates(
    state: BalloonCoordinateState,
    id: String
): Modifier = onGloballyPositioned { state.updateBubble(id, it.boundsInRoot()) }

@Composable
internal fun BalloonConnector(
    coordinateState: BalloonCoordinateState,
    modifier: Modifier = Modifier
) {
    var layerBounds by remember { mutableStateOf<Rect?>(null) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { configureConnectorPaint(Paint.Cap.ROUND) }
    }
    val path = remember { Path() }
    Canvas(modifier.onGloballyPositioned { layerBounds = it.boundsInRoot() }) {
        @Suppress("UNUSED_VARIABLE")
        val invalidationVersion = coordinateState.version
        val trigger = coordinateState.triggerBounds ?: return@Canvas
        val layer = layerBounds ?: return@Canvas
        path.rewind()
        coordinateState.bubbleBounds.values.forEach { bubble ->
            val bubbleIsRight = bubble.center.x >= trigger.center.x
            val line = createChatConnectorLine(
                avatarBounds = trigger,
                bubbleBounds = bubble,
                layerBounds = layer,
                target = if (bubbleIsRight) {
                    FloatingChatConnectionTarget.User
                } else {
                    FloatingChatConnectionTarget.Account
                }
            )
            path.moveTo(line.start.x, line.start.y)
            path.lineTo(line.cornerStart.x, line.cornerStart.y)
            path.quadTo(
                line.cornerStart.x,
                line.cornerEnd.y,
                line.cornerEnd.x,
                line.cornerEnd.y
            )
            path.lineTo(line.end.x, line.end.y)
        }
        drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
    }
}
