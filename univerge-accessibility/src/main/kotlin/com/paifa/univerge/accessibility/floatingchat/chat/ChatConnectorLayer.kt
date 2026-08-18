package com.paifa.univerge.accessibility.floatingchat.chat

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
import com.paifa.univerge.accessibility.*
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.core.model.FloatingChatConnectionTarget
import com.paifa.univerge.core.model.FloatingChatMessage

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
    val connectorPathWriter = remember { ChatConnectorPathWriter() }
    val connectorCommandSink = remember { PathConnectorCommandSink(connectorPath) }
    val connectorTreeCommandSink = remember { PathConnectorCommandSink(connectorTreePath) }
    val connectorTargetKeysByMessageId = remember(
        messages,
        selection,
        selectedAccountId,
        homeOverviewVisible,
        homeOverviewConnectorGroupIds,
        groupMemberAvatarsVisible
    ) {
        buildMap {
            messages.forEach { message ->
                put(
                    message.id,
                    if (homeOverviewVisible) {
                        message.toHomeOverviewConnectorTargetKey(
                            homeOverviewConnectorGroupIds[message.id]
                        )
                    } else {
                        message.toConnectorTargetKey(
                            selection = selection,
                            selectedAccountId = selectedAccountId,
                            groupMemberAvatarsVisible = groupMemberAvatarsVisible
                        )
                    }
                )
            }
        }
    }
    val visibleBubbleGroups = remember(
        messages,
        selection,
        selectedAccountId,
        homeOverviewVisible,
        homeOverviewConnectorGroupIds,
        groupMemberAvatarsVisible
    ) {
        linkedMapOf<ConnectorTargetKey, MutableList<Rect>>()
    }
    val activeBubbleGroupKeys = remember(
        messages,
        selection,
        selectedAccountId,
        homeOverviewVisible,
        homeOverviewConnectorGroupIds,
        groupMemberAvatarsVisible
    ) {
        linkedSetOf<ConnectorTargetKey>()
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
        visibleGroupMemberBounds.clear()
        connectorKeys.clear()
        var firstVisibleIndex = Int.MAX_VALUE
        var lastVisibleIndex = Int.MIN_VALUE
        visibleItems.forEach { itemInfo ->
            firstVisibleIndex = minOf(firstVisibleIndex, itemInfo.index)
            lastVisibleIndex = maxOf(lastVisibleIndex, itemInfo.index)
            forEachVisibleConnectorMessage(
                messages = messages,
                homeOverviewMessageGroups = homeOverviewMessageGroups,
                homeOverviewVisible = homeOverviewVisible,
                itemIndex = itemInfo.index
            ) messageLoop@ { message ->
                val key = connectorTargetKeysByMessageId[message.id] ?: return@messageLoop

                val bubbleBounds = connectorState.messageBubbles[message.id] ?: return@messageLoop
                if (key.lane == ConnectorAvatarLane.GroupMember) {
                    connectorState.groupMemberAvatars[key.targetId]?.let { bounds ->
                        visibleGroupMemberBounds += bounds
                        connectorPathWriter.writeGroupMemberMessageConnector(
                            avatarBounds = bounds,
                            bubbleBounds = bubbleBounds,
                            layerBounds = layerBounds,
                            sink = connectorCommandSink
                        )
                    }
                }
                visibleBubbleGroups.getOrPut(key) { mutableListOf() }.add(bubbleBounds)
                activeBubbleGroupKeys += key
            }
        }
        visibleBubbleGroups.keys.retainAll(activeBubbleGroupKeys)

        if (selection.isGroupThread() && groupMemberAvatarsVisible) {
            connectorState.groupThreadAvatar?.let { groupAvatarBounds ->
                if (visibleGroupMemberBounds.isNotEmpty()) {
                    connectorPathWriter.writeConnectorTree(
                        avatarBounds = groupAvatarBounds,
                        bubbleBounds = visibleGroupMemberBounds,
                        layerBounds = layerBounds,
                        visibleRootBounds = messageViewportBounds,
                        target = FloatingChatConnectionTarget.User,
                        hasMessagesAbove = false,
                        hasMessagesBelow = false,
                        sink = connectorTreeCommandSink
                    )
                }
            }
        }

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
            val avatarOffscreenEdge = if (key.lane == ConnectorAvatarLane.Account) {
                connectorState.accountAvatarEdgeFor(key.targetId)
            } else {
                null
            }
            val avatarBounds = when (key.lane) {
                ConnectorAvatarLane.Session -> {
                    if (homeOverviewVisible) {
                        connectorState.homeOverviewAvatarFor(key.targetId)
                    } else if (
                        selection.isGroupThread() &&
                        key.targetId == selection.groupConnectorId()
                    ) {
                        connectorState.groupThreadAvatar ?: connectorState.userAvatarFor(key.targetId)
                    } else if (
                        selection is ChatThreadSelection.Private &&
                        key.targetId == selection.contactId
                    ) {
                        connectorState.privateThreadAvatarFor(key.targetId)
                            ?: connectorState.userAvatarFor(key.targetId)
                    } else {
                        connectorState.userAvatarFor(key.targetId)
                    }
                }
                ConnectorAvatarLane.GroupMember -> connectorState.groupMemberAvatars[key.targetId]
                ConnectorAvatarLane.Account -> connectorState.accountAvatarFor(key.targetId)
            } ?: return@forEach

            val edgeState = offscreenEdges[key]
            connectorPathWriter.writeConnectorTree(
                avatarBounds = avatarBounds,
                bubbleBounds = visibleBubbleGroups[key],
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                target = key.target,
                hasMessagesAbove = edgeState?.hasAbove == true,
                hasMessagesBelow = edgeState?.hasBelow == true,
                avatarOffscreenEdge = avatarOffscreenEdge,
                sink = connectorTreeCommandSink
            )
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

private inline fun forEachVisibleConnectorMessage(
    messages: List<FloatingChatMessage>,
    homeOverviewMessageGroups: List<HomeOverviewMessageGroup>,
    homeOverviewVisible: Boolean,
    itemIndex: Int,
    action: (FloatingChatMessage) -> Unit
) {
    if (homeOverviewVisible) {
        homeOverviewMessagesForVisibleGroup(homeOverviewMessageGroups, itemIndex).forEach(action)
    } else {
        messages.getOrNull(itemIndex)?.let(action)
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
