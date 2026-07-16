package com.paifa.ubikitouch.accessibility.floatingchat.chat

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.paifa.ubikitouch.accessibility.*
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlin.math.abs

@Composable
internal fun ChatConnectorLayer(
    messages: List<FloatingChatMessage>,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean,
    listState: LazyListState,
    offscreenIndex: ConnectorOffscreenIndex,
    connectorState: ConnectorCoordinateState,
    modifier: Modifier = Modifier
) {
    var layerBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val connectorNativePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val connectorTreeNativePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val connectorStroke = remember {
        Stroke(
            width = imModuleConnectionLineStrokeWidthPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    val connectorTreeStroke = remember {
        Stroke(
            width = imModuleConnectionLineStrokeWidthPx(),
            cap = StrokeCap.Butt,
            join = StrokeJoin.Round
        )
    }
    Canvas(
        modifier = modifier.onGloballyPositioned { coordinates ->
            layerBoundsInRoot = coordinates.boundsInRoot()
        }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val connectorInvalidationVersion = connectorState.version
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@Canvas
        val layerBounds = layerBoundsInRoot ?: return@Canvas
        val messageViewportBounds = connectorState.messageViewport ?: return@Canvas
        connectorNativePaint.configureConnectorPaint(cap = Paint.Cap.ROUND)
        connectorTreeNativePaint.configureConnectorPaint(cap = Paint.Cap.BUTT)

        val visibleBubbleGroups = linkedMapOf<ConnectorTargetKey, MutableList<Rect>>()
        val avatarSourceKeys = linkedMapOf<ConnectorTargetKey, ConnectorTargetKey>()
        val directGroupMemberBranches = mutableListOf<ChatConnectorBranch>()
        val visibleGroupMemberBounds = mutableListOf<Rect>()
        visibleItems.forEach { itemInfo ->
            val message = messages.getOrNull(itemInfo.index) ?: return@forEach
            val key = if (homeOverviewVisible) {
                message.toHomeOverviewConnectorTargetKey()
            } else {
                message.toConnectorTargetKey(
                    selection = selection,
                    selectedAccountId = selectedAccountId,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible
                )
            } ?: return@forEach

            val bubbleBounds = connectorState.messageBubbles[message.id] ?: return@forEach
            avatarSourceKeys[key] = if (homeOverviewVisible) {
                message.toHomeOverviewConnectorSourceKey() ?: key
            } else {
                key
            }
            if (key.lane == ConnectorAvatarLane.GroupMember) {
                connectorState.groupMemberAvatars[key.targetId]?.let { bounds ->
                    visibleGroupMemberBounds += bounds
                    directGroupMemberBranches += createGroupMemberMessageConnectorBranch(
                        avatarBounds = bounds,
                        bubbleBounds = bubbleBounds,
                        layerBounds = layerBounds
                    )
                }
            }
            visibleBubbleGroups.getOrPut(key) { mutableListOf() }.add(bubbleBounds)
        }

        if (selection.isGroupThread() && groupMemberAvatarsVisible) {
            drawGroupMemberConnectorTree(
                connectorState = connectorState,
                memberBounds = visibleGroupMemberBounds,
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                nativePaint = connectorTreeNativePaint,
                stroke = connectorTreeStroke
            )
        }

        val firstVisibleIndex = visibleItems.minOf { it.index }
        val lastVisibleIndex = visibleItems.maxOf { it.index }
        val offscreenEdges = offscreenConnectorEdges(
            index = offscreenIndex,
            firstVisibleIndex = firstVisibleIndex,
            lastVisibleIndex = lastVisibleIndex
        )
        val connectorKeys = visibleBubbleGroups.keys + offscreenEdges.keys
        connectorKeys.forEach { key ->
            if (key.lane == ConnectorAvatarLane.GroupMember) return@forEach
            val avatarSourceKey = avatarSourceKeys[key] ?: key
            val avatarOffscreenEdge = if (avatarSourceKey.lane == ConnectorAvatarLane.Account) {
                connectorState.accountAvatarEdgeFor(avatarSourceKey.targetId)
            } else {
                null
            }
            val avatarBounds = when (avatarSourceKey.lane) {
                ConnectorAvatarLane.Session -> {
                    if (
                        selection.isGroupThread() &&
                        avatarSourceKey.targetId == selection.groupConnectorId()
                    ) {
                        connectorState.groupThreadAvatar ?: connectorState.userAvatarFor(avatarSourceKey.targetId)
                    } else if (
                        selection is ChatThreadSelection.Private &&
                        avatarSourceKey.targetId == selection.contactId
                    ) {
                        connectorState.privateThreadAvatarFor(avatarSourceKey.targetId)
                            ?: connectorState.userAvatarFor(avatarSourceKey.targetId)
                    } else {
                        connectorState.userAvatarFor(avatarSourceKey.targetId)
                    }
                }
                ConnectorAvatarLane.GroupMember -> connectorState.groupMemberAvatars[avatarSourceKey.targetId]
                ConnectorAvatarLane.Account -> connectorState.accountAvatarFor(avatarSourceKey.targetId)
            } ?: if (homeOverviewVisible) {
                homeOverviewFallbackConnectorAvatarBounds(
                    bubbleBounds = visibleBubbleGroups[key].orEmpty(),
                    layerBounds = layerBounds,
                    visibleRootBounds = messageViewportBounds,
                    target = key.target
                )
            } else {
                null
            } ?: return@forEach

            val edgeState = offscreenEdges[key] ?: ConnectorViewportEdgeState()
            val tree = createChatConnectorTree(
                avatarBounds = avatarBounds,
                bubbleBounds = visibleBubbleGroups[key].orEmpty(),
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                target = key.target,
                hasMessagesAbove = edgeState.hasAbove,
                hasMessagesBelow = edgeState.hasBelow,
                avatarOffscreenEdge = avatarOffscreenEdge
            ) ?: return@forEach

            drawChatConnectorTree(tree, connectorTreeNativePaint, connectorTreeStroke)
        }
        directGroupMemberBranches.forEach { branch ->
            drawChatConnectorBranch(branch, connectorNativePaint)
        }
    }
}

private fun Paint.configureConnectorPaint(cap: Paint.Cap) {
    style = Paint.Style.STROKE
    strokeWidth = imModuleConnectionLineStrokeWidthPx()
    strokeCap = cap
    strokeJoin = Paint.Join.ROUND
    color = OverlayTokens.connectorLine.toArgb()
    setShadowLayer(
        imModuleConnectionLineShadowBlurPx(),
        imModuleConnectionLineShadowOffsetXPx(),
        imModuleConnectionLineShadowOffsetYPx(),
        OverlayTokens.connectorLineShadow.toArgb()
    )
}

private fun DrawScope.drawChatConnectorBranch(
    branch: ChatConnectorBranch,
    nativePaint: Paint
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawLine(
            branch.start.x,
            branch.start.y,
            branch.end.x,
            branch.end.y,
            nativePaint
        )
    }
    drawLine(
        color = OverlayTokens.connectorLine,
        start = branch.start,
        end = branch.end,
        strokeWidth = imModuleConnectionLineStrokeWidthPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawChatConnectorTree(
    tree: ChatConnectorTree,
    nativePaint: Paint,
    stroke: Stroke
) {
    val connectorPath = tree.toPath()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(connectorPath.asAndroidPath(), nativePaint)
    }
    drawPath(path = connectorPath, color = OverlayTokens.connectorLine, style = stroke)
}

private fun DrawScope.drawGroupMemberConnectorTree(
    connectorState: ConnectorCoordinateState,
    memberBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    nativePaint: Paint,
    stroke: Stroke
) {
    val groupAvatarBounds = connectorState.groupThreadAvatar ?: return
    if (memberBounds.isEmpty()) return

    val tree = createChatConnectorTree(
        avatarBounds = groupAvatarBounds,
        bubbleBounds = memberBounds,
        layerBounds = layerBounds,
        visibleRootBounds = visibleRootBounds,
        target = FloatingChatConnectionTarget.User,
        hasMessagesAbove = false,
        hasMessagesBelow = false
    ) ?: return
    drawChatConnectorTree(tree, nativePaint, stroke)
}

private fun ChatConnectorTree.toPath(): Path {
    val geometry = createChatConnectorBraceGeometry(this)
    return Path().apply {
        geometry.trunkSegments.forEach { segment ->
            moveTo(segment.start.x, segment.start.y)
            lineTo(segment.end.x, segment.end.y)
        }
        geometry.hooks.forEach { hook -> braceHookSegment(hook) }
    }
}

private fun Path.braceHookSegment(hook: ChatConnectorBraceHook) {
    val deltaX = hook.branchEnd.x - hook.center.x
    if (abs(deltaX) <= 0.5f) return

    val geometry = hook.roundedElbowGeometry()
    moveTo(geometry.curveStart.x, geometry.curveStart.y)
    quadraticTo(
        geometry.curveControl.x,
        geometry.curveControl.y,
        geometry.horizontalStart.x,
        geometry.horizontalStart.y
    )
    lineTo(geometry.branchEnd.x, geometry.branchEnd.y)
}
