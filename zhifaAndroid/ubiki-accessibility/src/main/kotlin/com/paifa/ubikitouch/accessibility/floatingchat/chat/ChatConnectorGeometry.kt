package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.chatConnectorHookTrunkOverlapPx
import com.paifa.ubikitouch.accessibility.connectorRoundedElbowRadiusPx
import com.paifa.ubikitouch.accessibility.imModuleConnectionLineBubbleGapPx
import com.paifa.ubikitouch.accessibility.imModuleConnectionLineBubbleOverlapPx
import com.paifa.ubikitouch.accessibility.imModuleConnectionLineCornerRadiusPx
import com.paifa.ubikitouch.accessibility.imModuleConnectionLineHorizontalOffsetPx
import com.paifa.ubikitouch.accessibility.imModuleConnectionLineMinimumBranchPx
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class ChatConnectorLine(
    val start: Offset,
    val cornerStart: Offset,
    val cornerEnd: Offset,
    val end: Offset
)

internal data class ChatConnectorBranch(
    val start: Offset,
    val end: Offset
)

internal data class ChatConnectorTree(
    val trunkStart: Offset,
    val trunkEnd: Offset,
    val avatarBranch: ChatConnectorBranch?,
    val messageBranches: List<ChatConnectorBranch>
)

internal data class ChatConnectorBraceHook(
    val center: Offset,
    val branchEnd: Offset,
    val radius: Float,
    val verticalDirection: Float
)

internal data class ChatConnectorBraceGeometry(
    val trunkSegments: List<ChatConnectorBranch>,
    val hooks: List<ChatConnectorBraceHook>
)

internal data class ChatConnectorRoundedHookGeometry(
    val curveStart: Offset,
    val curveControl: Offset,
    val horizontalStart: Offset,
    val branchEnd: Offset
)

internal enum class ChatConnectorViewportEdge {
    Above,
    Below
}

internal fun createChatConnectorLine(
    avatarBounds: Rect,
    bubbleBounds: Rect,
    layerBounds: Rect,
    target: FloatingChatConnectionTarget
): ChatConnectorLine {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val bubbleAnchor = if (target == FloatingChatConnectionTarget.Account) {
        bubbleBounds.rightCenterIn(layerBounds).awayFromBubble(target)
    } else {
        bubbleBounds.leftCenterIn(layerBounds).awayFromBubble(target)
    }
    val elbowX = connectorMidX(avatarAnchor, target)
    return ChatConnectorLine(
        start = avatarAnchor,
        cornerStart = Offset(elbowX, avatarAnchor.y),
        cornerEnd = Offset(elbowX, bubbleAnchor.y),
        end = bubbleAnchor
    )
}

internal fun createOffscreenChatConnectorLine(
    avatarBounds: Rect,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget,
    edge: ChatConnectorViewportEdge
): ChatConnectorLine {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val endY = when (edge) {
        ChatConnectorViewportEdge.Above -> visibleRootBounds.top - layerBounds.top
        ChatConnectorViewportEdge.Below -> visibleRootBounds.bottom - layerBounds.top
    }
    val endX = if (target == FloatingChatConnectionTarget.Account) {
        visibleRootBounds.right - layerBounds.left
    } else {
        visibleRootBounds.left - layerBounds.left
    }
    val edgeAnchor = Offset(endX, endY)
    val elbowX = connectorMidX(avatarAnchor, target)

    return ChatConnectorLine(
        start = avatarAnchor,
        cornerStart = Offset(elbowX, avatarAnchor.y),
        cornerEnd = Offset(elbowX, edgeAnchor.y),
        end = edgeAnchor
    )
}

internal fun createChatConnectorTree(
    avatarBounds: Rect,
    bubbleBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget,
    hasMessagesAbove: Boolean,
    hasMessagesBelow: Boolean,
    avatarOffscreenEdge: ChatConnectorViewportEdge? = null
): ChatConnectorTree? {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val visibleAnchors = bubbleBounds
        .map { bounds ->
            val anchor = if (target == FloatingChatConnectionTarget.Account) {
                bounds.rightCenterIn(layerBounds).awayFromBubble(target)
            } else {
                bounds.leftCenterIn(layerBounds).awayFromBubble(target)
            }
            anchor.pinnedToMessageViewport(layerBounds, visibleRootBounds)
        }
        .sortedBy { anchor -> anchor.y }

    if (visibleAnchors.isEmpty() && !hasMessagesAbove && !hasMessagesBelow) return null

    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    val trunkX = connectorTreeTrunkX(avatarAnchor, visibleAnchors, target)
    val avatarEdgeY = avatarAnchor.pinnedToMessageViewport(layerBounds, visibleRootBounds).y
    val branchYs = visibleAnchors.map { anchor -> anchor.y } +
        listOfNotNull(
            viewportTop.takeIf { hasMessagesAbove },
            viewportBottom.takeIf { hasMessagesBelow },
            avatarEdgeY.takeIf { avatarOffscreenEdge != null },
            avatarAnchor.y.takeIf { avatarOffscreenEdge == null }
        )
    val trunkStart = Offset(trunkX, branchYs.minOrNull() ?: avatarAnchor.y)
    val trunkEnd = Offset(trunkX, branchYs.maxOrNull() ?: avatarAnchor.y)

    return ChatConnectorTree(
        trunkStart = trunkStart,
        trunkEnd = trunkEnd,
        avatarBranch = if (avatarOffscreenEdge == null) {
            ChatConnectorBranch(avatarAnchor, Offset(trunkX, avatarAnchor.y))
        } else {
            null
        },
        messageBranches = visibleAnchors.map { anchor ->
            ChatConnectorBranch(Offset(trunkX, anchor.y), anchor)
        }
    )
}

internal fun ChatConnectorLine.pinnedToMessageViewport(
    layerBounds: Rect,
    visibleRootBounds: Rect
): ChatConnectorLine {
    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    val pinnedEndY = end.y.coerceIn(viewportTop, viewportBottom)
    if (pinnedEndY == end.y) return this
    return copy(
        cornerEnd = Offset(cornerEnd.x, pinnedEndY),
        end = Offset(end.x, pinnedEndY)
    )
}

internal fun homeOverviewConnectorKeyDebugId(message: FloatingChatMessage): String? {
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    val sourceId = homeOverviewConnectorSourceKeyDebugId(message) ?: return null
    return "home-source:$sourceId"
}

internal fun homeOverviewConnectorSourceKeyDebugId(message: FloatingChatMessage): String? {
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    return message.connectionTargetId
}

internal fun Rect.leftCenterIn(containerBounds: Rect): Offset {
    return Offset(
        x = left - containerBounds.left,
        y = center.y - containerBounds.top
    )
}

internal fun Rect.rightCenterIn(containerBounds: Rect): Offset {
    return Offset(
        x = right - containerBounds.left,
        y = center.y - containerBounds.top
    )
}

internal fun rootBoundsFromPosition(
    positionInRoot: Offset,
    width: Int,
    height: Int
): Rect {
    return Rect(
        left = positionInRoot.x,
        top = positionInRoot.y,
        right = positionInRoot.x + width,
        bottom = positionInRoot.y + height
    )
}

internal fun isConnectorAnchorVisible(
    anchorInLayer: Offset,
    layerBounds: Rect,
    visibleRootBounds: Rect
): Boolean {
    val anchorRootY = layerBounds.top + anchorInLayer.y
    return anchorRootY >= visibleRootBounds.top && anchorRootY <= visibleRootBounds.bottom
}

internal fun createChatConnectorBraceGeometry(tree: ChatConnectorTree): ChatConnectorBraceGeometry {
    val requestedRadius = imModuleConnectionLineCornerRadiusPx()
    val avatarHook = tree.avatarBranch?.let { branch -> branch.end to branch.start }
    val pendingHooks = (listOfNotNull(avatarHook) +
        tree.messageBranches.map { branch -> branch.start to branch.end })
        .sortedBy { (center, _) -> center.y }
    val hooks = pendingHooks.mapIndexed { index, (center, branchEnd) ->
        val horizontalRoom = abs(branchEnd.x - center.x)
        val requestedHookRadius = min(requestedRadius, connectorRoundedElbowRadiusPx(horizontalRoom))
        val nextCenterY = pendingHooks.getOrNull(index + 1)?.first?.y
        val previousCenterY = pendingHooks.getOrNull(index - 1)?.first?.y
        val verticalRoom = when {
            index == 0 && nextCenterY != null -> abs(nextCenterY - center.y) / 2f
            index > 0 && previousCenterY != null -> abs(center.y - previousCenterY) / 2f
            else -> requestedHookRadius
        }
        ChatConnectorBraceHook(
            center = center,
            branchEnd = branchEnd,
            radius = min(requestedHookRadius, verticalRoom).coerceAtLeast(1f),
            verticalDirection = if (index == 0) 1f else -1f
        )
    }
    return ChatConnectorBraceGeometry(
        trunkSegments = connectorContinuousTrunkSegments(tree.trunkStart, tree.trunkEnd),
        hooks = hooks
    )
}

internal fun ChatConnectorBraceHook.branchStartAvoidingJointOverlap(): Offset {
    return roundedElbowGeometry().horizontalStart
}

internal fun ChatConnectorBraceHook.roundedElbowGeometry(): ChatConnectorRoundedHookGeometry {
    val deltaX = branchEnd.x - center.x
    if (abs(deltaX) <= 0.5f) {
        return ChatConnectorRoundedHookGeometry(center, center, center, branchEnd)
    }
    val horizontalDirection = if (deltaX >= 0f) 1f else -1f
    val safeRadius = min(radius, abs(deltaX)).coerceAtLeast(1f)
    return ChatConnectorRoundedHookGeometry(
        curveStart = center.copy(
            y = center.y + verticalDirection * (safeRadius + chatConnectorHookTrunkOverlapPx())
        ),
        curveControl = center,
        horizontalStart = center.copy(x = center.x + horizontalDirection * safeRadius),
        branchEnd = branchEnd
    )
}

private fun Offset.pinnedToMessageViewport(
    layerBounds: Rect,
    visibleRootBounds: Rect
): Offset {
    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    return copy(y = y.coerceIn(viewportTop, viewportBottom))
}

private fun Offset.awayFromBubble(target: FloatingChatConnectionTarget): Offset {
    val gap = imModuleConnectionLineBubbleGapPx()
    val overlap = imModuleConnectionLineBubbleOverlapPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        copy(x = x + gap - overlap)
    } else {
        copy(x = x - gap + overlap)
    }
}

private fun connectorMidX(
    avatarAnchor: Offset,
    target: FloatingChatConnectionTarget
): Float {
    val offset = imModuleConnectionLineHorizontalOffsetPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        avatarAnchor.x - offset
    } else {
        avatarAnchor.x + offset
    }
}

private fun connectorTreeTrunkX(
    avatarAnchor: Offset,
    visibleAnchors: List<Offset>,
    target: FloatingChatConnectionTarget
): Float {
    val defaultX = connectorMidX(avatarAnchor, target)
    if (visibleAnchors.isEmpty()) return defaultX

    val minimumBranch = imModuleConnectionLineMinimumBranchPx()
    val minimumAvatarBranch = imModuleConnectionLineCornerRadiusPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        val bubbleAnchorX = visibleAnchors.maxOf { anchor -> anchor.x }
        val lower = bubbleAnchorX + minimumBranch
        val upper = avatarAnchor.x - minimumAvatarBranch
        if (lower <= upper) defaultX.coerceIn(lower, upper)
        else lower.coerceAtMost(upper).coerceAtLeast(bubbleAnchorX + 1f)
    } else {
        val bubbleAnchorX = visibleAnchors.minOf { anchor -> anchor.x }
        val lower = avatarAnchor.x + minimumAvatarBranch
        val upper = bubbleAnchorX - minimumBranch
        if (lower <= upper) defaultX.coerceIn(lower, upper)
        else upper.coerceAtLeast(lower).coerceAtMost(bubbleAnchorX - 1f)
    }
}

private fun connectorContinuousTrunkSegments(
    trunkStart: Offset,
    trunkEnd: Offset
): List<ChatConnectorBranch> {
    if (trunkStart == trunkEnd) return emptyList()
    val topY = min(trunkStart.y, trunkEnd.y)
    val bottomY = max(trunkStart.y, trunkEnd.y)
    return listOf(
        ChatConnectorBranch(
            start = Offset(trunkStart.x, topY),
            end = Offset(trunkStart.x, bottomY)
        )
    )
}
