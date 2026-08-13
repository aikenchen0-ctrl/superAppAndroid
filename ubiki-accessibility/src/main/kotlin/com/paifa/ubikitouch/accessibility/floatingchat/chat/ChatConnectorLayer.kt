package com.paifa.ubikitouch.accessibility.floatingchat.chat

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.paifa.ubikitouch.accessibility.*
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlin.math.abs

@Composable
internal fun ChatConnectorLayer(
    messages: List<FloatingChatMessage>,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    homeOverviewConnectorGroupIds: Map<String, String>,
    homeOverviewMessageGroups: List<HomeOverviewMessageGroup>,
    groupMemberAvatarsVisible: Boolean,
    listState: LazyListState,
    offscreenIndex: ConnectorOffscreenIndex,
    connectorState: ConnectorCoordinateState,
    modifier: Modifier = Modifier
) {
    var layerBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val connectorNativePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { configureConnectorPaint(cap = Paint.Cap.ROUND) }
    }
    val connectorTreeNativePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { configureConnectorPaint(cap = Paint.Cap.BUTT) }
    }
    val connectorPath = remember { Path() }
    val connectorTreePath = remember { Path() }
    val visibleBubbleGroups = remember(messages, selection, homeOverviewVisible) {
        linkedMapOf<ConnectorTargetKey, MutableList<Rect>>()
    }
    val activeBubbleGroupKeys = remember(messages, selection, homeOverviewVisible) {
        linkedSetOf<ConnectorTargetKey>()
    }
    val avatarSourceKeys = remember(messages, selection, homeOverviewVisible) {
        linkedMapOf<ConnectorTargetKey, ConnectorTargetKey>()
    }
    val directGroupMemberBranches = remember(messages, selection) {
        mutableListOf<ChatConnectorBranch>()
    }
    val visibleGroupMemberBounds = remember(messages, selection) { mutableListOf<Rect>() }
    val connectorKeys = remember(messages, selection, homeOverviewVisible) {
        linkedSetOf<ConnectorTargetKey>()
    }
    val offscreenEdges = remember(messages, selection, homeOverviewVisible) {
        linkedMapOf<ConnectorTargetKey, ConnectorViewportEdgeState>()
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
        connectorPath.rewind()
        connectorTreePath.rewind()
        visibleBubbleGroups.values.forEach(MutableList<Rect>::clear)
        activeBubbleGroupKeys.clear()
        avatarSourceKeys.clear()
        directGroupMemberBranches.clear()
        visibleGroupMemberBounds.clear()
        connectorKeys.clear()
        visibleItems.forEach { itemInfo ->
            val itemMessages = if (homeOverviewVisible) {
                homeOverviewMessagesForVisibleGroup(homeOverviewMessageGroups, itemInfo.index)
            } else {
                listOfNotNull(messages.getOrNull(itemInfo.index))
            }
            itemMessages.forEach messageLoop@ { message ->
                val key = if (homeOverviewVisible) {
                    message.toHomeOverviewConnectorTargetKey(homeOverviewConnectorGroupIds[message.id])
                } else {
                    message.toConnectorTargetKey(
                        selection = selection,
                        selectedAccountId = selectedAccountId,
                        groupMemberAvatarsVisible = groupMemberAvatarsVisible
                    )
                } ?: return@messageLoop

                val bubbleBounds = connectorState.messageBubbles[message.id] ?: return@messageLoop
                avatarSourceKeys[key] = key
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
                activeBubbleGroupKeys += key
            }
        }

        if (selection.isGroupThread() && groupMemberAvatarsVisible) {
            appendGroupMemberConnectorTree(
                connectorState = connectorState,
                memberBounds = visibleGroupMemberBounds,
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                path = connectorTreePath
            )
        }

        val firstVisibleIndex = visibleItems.minOf { it.index }
        val lastVisibleIndex = visibleItems.maxOf { it.index }
        if (homeOverviewVisible) {
            offscreenEdges.clear()
        } else {
            updateOffscreenConnectorEdges(
                index = offscreenIndex,
                firstVisibleIndex = firstVisibleIndex,
                lastVisibleIndex = lastVisibleIndex,
                destination = offscreenEdges
            )
        }
        connectorKeys += activeBubbleGroupKeys
        connectorKeys += offscreenEdges.keys
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
                    if (homeOverviewVisible) {
                        connectorState.homeOverviewAvatarFor(avatarSourceKey.targetId)
                    } else if (
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

            connectorTreePath.appendChatConnectorTree(tree)
        }
        directGroupMemberBranches.forEach { branch ->
            connectorPath.appendChatConnectorBranch(branch)
        }
        drawIntoCanvas { canvas ->
            if (!connectorTreePath.isEmpty) {
                canvas.nativeCanvas.drawPath(connectorTreePath, connectorTreeNativePaint)
            }
            if (!connectorPath.isEmpty) {
                canvas.nativeCanvas.drawPath(connectorPath, connectorNativePaint)
            }
        }
    }
}

internal fun Paint.configureConnectorPaint(cap: Paint.Cap) {
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

private fun Path.appendChatConnectorBranch(branch: ChatConnectorBranch) {
    moveTo(branch.start.x, branch.start.y)
    lineTo(branch.end.x, branch.end.y)
}

private fun appendGroupMemberConnectorTree(
    connectorState: ConnectorCoordinateState,
    memberBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    path: Path
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
    path.appendChatConnectorTree(tree)
}

private fun Path.appendChatConnectorTree(tree: ChatConnectorTree) {
    val geometry = createChatConnectorBraceGeometry(tree)
    geometry.trunkSegments.forEach { segment ->
        appendChatConnectorBranch(segment)
    }
    geometry.hooks.forEach { hook -> appendBraceHookSegment(hook) }
}

private fun Path.appendBraceHookSegment(hook: ChatConnectorBraceHook) {
    val deltaX = hook.branchEnd.x - hook.center.x
    if (abs(deltaX) <= 0.5f) return

    val geometry = hook.roundedElbowGeometry()
    moveTo(geometry.curveStart.x, geometry.curveStart.y)
    quadTo(
        geometry.curveControl.x,
        geometry.curveControl.y,
        geometry.horizontalStart.x,
        geometry.horizontalStart.y
    )
    lineTo(geometry.branchEnd.x, geometry.branchEnd.y)
}
