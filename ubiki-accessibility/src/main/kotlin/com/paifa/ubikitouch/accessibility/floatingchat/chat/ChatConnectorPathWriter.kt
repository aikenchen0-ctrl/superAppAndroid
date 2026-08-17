package com.paifa.ubikitouch.accessibility.floatingchat.chat

import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal interface ChatConnectorCommandSink {
    fun moveTo(x: Float, y: Float)

    fun lineTo(x: Float, y: Float)

    fun quadTo(controlX: Float, controlY: Float, endX: Float, endY: Float)
}

internal class PathConnectorCommandSink(
    private val path: Path
) : ChatConnectorCommandSink {
    override fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    override fun quadTo(controlX: Float, controlY: Float, endX: Float, endY: Float) {
        path.quadTo(controlX, controlY, endX, endY)
    }
}

internal class ChatConnectorPathWriter {
    private var anchorCoordinates = FloatArray(INITIAL_ANCHOR_CAPACITY * ANCHOR_STRIDE)

    fun writeConnectorTree(
        avatarBounds: Rect,
        bubbleBounds: List<Rect>?,
        layerBounds: Rect,
        visibleRootBounds: Rect,
        target: FloatingChatConnectionTarget,
        hasMessagesAbove: Boolean,
        hasMessagesBelow: Boolean,
        avatarOffscreenEdge: ChatConnectorViewportEdge? = null,
        sink: ChatConnectorCommandSink
    ): Boolean {
        val anchorCount = populateSortedAnchors(
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            visibleRootBounds = visibleRootBounds,
            target = target
        )
        if (anchorCount == 0 && !hasMessagesAbove && !hasMessagesBelow) return false

        val accountTarget = target == FloatingChatConnectionTarget.Account
        val avatarX = if (accountTarget) {
            avatarBounds.left - layerBounds.left
        } else {
            avatarBounds.right - layerBounds.left
        }
        val avatarY = rectCenterY(avatarBounds) - layerBounds.top
        val viewportTop = visibleRootBounds.top - layerBounds.top
        val viewportBottom = visibleRootBounds.bottom - layerBounds.top
        val trunkX = connectorTrunkX(
            avatarX = avatarX,
            anchorCount = anchorCount,
            accountTarget = accountTarget
        )

        var hasTrunkPoint = anchorCount > 0
        var trunkStartY = if (hasTrunkPoint) anchorY(0) else 0f
        var trunkEndY = trunkStartY
        var anchorIndex = if (hasTrunkPoint) 1 else 0
        while (anchorIndex < anchorCount) {
            val y = anchorY(anchorIndex)
            trunkStartY = min(trunkStartY, y)
            trunkEndY = max(trunkEndY, y)
            anchorIndex += 1
        }
        if (hasMessagesAbove) {
            if (hasTrunkPoint) {
                trunkStartY = min(trunkStartY, viewportTop)
                trunkEndY = max(trunkEndY, viewportTop)
            } else {
                trunkStartY = viewportTop
                trunkEndY = viewportTop
                hasTrunkPoint = true
            }
        }
        if (hasMessagesBelow) {
            if (hasTrunkPoint) {
                trunkStartY = min(trunkStartY, viewportBottom)
                trunkEndY = max(trunkEndY, viewportBottom)
            } else {
                trunkStartY = viewportBottom
                trunkEndY = viewportBottom
                hasTrunkPoint = true
            }
        }
        val avatarBranchY = if (avatarOffscreenEdge == null) {
            avatarY
        } else {
            avatarY.coerceIn(viewportTop, viewportBottom)
        }
        if (hasTrunkPoint) {
            trunkStartY = min(trunkStartY, avatarBranchY)
            trunkEndY = max(trunkEndY, avatarBranchY)
        } else {
            trunkStartY = avatarBranchY
            trunkEndY = avatarBranchY
        }

        if (trunkStartY != trunkEndY) {
            sink.moveTo(trunkX, trunkStartY)
            sink.lineTo(trunkX, trunkEndY)
        }
        writeHooks(
            sink = sink,
            trunkX = trunkX,
            avatarX = avatarX,
            avatarY = avatarY,
            anchorCount = anchorCount,
            includeAvatarHook = avatarOffscreenEdge == null
        )
        return true
    }

    fun writeGroupMemberMessageConnector(
        avatarBounds: Rect,
        bubbleBounds: Rect,
        layerBounds: Rect,
        sink: ChatConnectorCommandSink
    ) {
        val avatarX = avatarBounds.right - layerBounds.left
        val avatarY = rectCenterY(avatarBounds) - layerBounds.top
        val bubbleX = bubbleBounds.left - layerBounds.left -
            imModuleConnectionLineBubbleGapPx() +
            imModuleConnectionLineBubbleOverlapPx()
        sink.moveTo(avatarX, avatarY)
        sink.lineTo(bubbleX, avatarY)
    }

    private fun populateSortedAnchors(
        bubbleBounds: List<Rect>?,
        layerBounds: Rect,
        visibleRootBounds: Rect,
        target: FloatingChatConnectionTarget
    ): Int {
        val boundsList = bubbleBounds ?: return 0
        val anchorCount = boundsList.size
        ensureAnchorCapacity(anchorCount)
        val accountTarget = target == FloatingChatConnectionTarget.Account
        val viewportTop = visibleRootBounds.top - layerBounds.top
        val viewportBottom = visibleRootBounds.bottom - layerBounds.top
        val gap = imModuleConnectionLineBubbleGapPx()
        val overlap = imModuleConnectionLineBubbleOverlapPx()
        var index = 0
        while (index < anchorCount) {
            val bounds = boundsList[index]
            val x = if (accountTarget) {
                bounds.right - layerBounds.left + gap - overlap
            } else {
                bounds.left - layerBounds.left - gap + overlap
            }
            val y = (rectCenterY(bounds) - layerBounds.top)
                .coerceIn(viewportTop, viewportBottom)
            insertAnchor(index = index, x = x, y = y)
            index += 1
        }
        return anchorCount
    }

    private fun insertAnchor(index: Int, x: Float, y: Float) {
        var destination = index
        while (destination > 0 && anchorY(destination - 1).compareTo(y) > 0) {
            setAnchor(
                index = destination,
                x = anchorX(destination - 1),
                y = anchorY(destination - 1)
            )
            destination -= 1
        }
        setAnchor(index = destination, x = x, y = y)
    }

    private fun connectorTrunkX(
        avatarX: Float,
        anchorCount: Int,
        accountTarget: Boolean
    ): Float {
        val horizontalOffset = imModuleConnectionLineHorizontalOffsetPx()
        val defaultX = if (accountTarget) {
            avatarX - horizontalOffset
        } else {
            avatarX + horizontalOffset
        }
        if (anchorCount == 0) return defaultX

        var bubbleAnchorX = anchorX(0)
        var index = 1
        while (index < anchorCount) {
            val x = anchorX(index)
            bubbleAnchorX = if (accountTarget) max(bubbleAnchorX, x) else min(bubbleAnchorX, x)
            index += 1
        }

        val minimumBranch = imModuleConnectionLineMinimumBranchPx()
        val minimumAvatarBranch = imModuleConnectionLineCornerRadiusPx()
        return if (accountTarget) {
            val lower = bubbleAnchorX + minimumBranch
            val upper = avatarX - minimumAvatarBranch
            if (lower <= upper) {
                defaultX.coerceIn(lower, upper)
            } else {
                lower.coerceAtMost(upper).coerceAtLeast(bubbleAnchorX + 1f)
            }
        } else {
            val lower = avatarX + minimumAvatarBranch
            val upper = bubbleAnchorX - minimumBranch
            if (lower <= upper) {
                defaultX.coerceIn(lower, upper)
            } else {
                upper.coerceAtLeast(lower).coerceAtMost(bubbleAnchorX - 1f)
            }
        }
    }

    private fun writeHooks(
        sink: ChatConnectorCommandSink,
        trunkX: Float,
        avatarX: Float,
        avatarY: Float,
        anchorCount: Int,
        includeAvatarHook: Boolean
    ) {
        val avatarHookIndex = if (includeAvatarHook) {
            findAvatarHookIndex(anchorCount = anchorCount, avatarY = avatarY)
        } else {
            NO_AVATAR_HOOK
        }
        val hookCount = anchorCount + if (includeAvatarHook) 1 else 0
        var hookIndex = 0
        while (hookIndex < hookCount) {
            val isAvatarHook = hookIndex == avatarHookIndex
            val anchorIndexForHook = messageAnchorIndexForHook(
                hookIndex = hookIndex,
                avatarHookIndex = avatarHookIndex
            )
            val centerY = if (isAvatarHook) avatarY else anchorY(anchorIndexForHook)
            val branchEndX = if (isAvatarHook) avatarX else anchorX(anchorIndexForHook)
            val deltaX = branchEndX - trunkX
            if (abs(deltaX) > MINIMUM_HOOK_HORIZONTAL_ROOM) {
                val horizontalRoom = abs(deltaX)
                val requestedHookRadius = min(
                    imModuleConnectionLineCornerRadiusPx(),
                    connectorRoundedElbowRadiusPx(horizontalRoom)
                )
                val verticalRoom = when {
                    hookIndex == 0 && hookCount > 1 -> {
                        abs(
                            hookCenterY(
                                hookIndex = 1,
                                avatarHookIndex = avatarHookIndex,
                                avatarY = avatarY
                            ) - centerY
                        ) / 2f
                    }
                    hookIndex > 0 -> {
                        abs(
                            centerY - hookCenterY(
                                hookIndex = hookIndex - 1,
                                avatarHookIndex = avatarHookIndex,
                                avatarY = avatarY
                            )
                        ) / 2f
                    }
                    else -> requestedHookRadius
                }
                val radius = min(requestedHookRadius, verticalRoom).coerceAtLeast(1f)
                val safeRadius = min(radius, horizontalRoom).coerceAtLeast(1f)
                val verticalDirection = if (hookIndex == 0) 1f else -1f
                val horizontalDirection = if (deltaX >= 0f) 1f else -1f
                sink.moveTo(
                    trunkX,
                    centerY + verticalDirection * (safeRadius + chatConnectorHookTrunkOverlapPx())
                )
                sink.quadTo(
                    trunkX,
                    centerY,
                    trunkX + horizontalDirection * safeRadius,
                    centerY
                )
                sink.lineTo(branchEndX, centerY)
            }
            hookIndex += 1
        }
    }

    private fun findAvatarHookIndex(anchorCount: Int, avatarY: Float): Int {
        var index = 0
        while (index < anchorCount && anchorY(index).compareTo(avatarY) < 0) {
            index += 1
        }
        return index
    }

    private fun hookCenterY(
        hookIndex: Int,
        avatarHookIndex: Int,
        avatarY: Float
    ): Float {
        if (hookIndex == avatarHookIndex) return avatarY
        return anchorY(messageAnchorIndexForHook(hookIndex, avatarHookIndex))
    }

    private fun messageAnchorIndexForHook(hookIndex: Int, avatarHookIndex: Int): Int {
        return if (avatarHookIndex == NO_AVATAR_HOOK || hookIndex < avatarHookIndex) {
            hookIndex
        } else {
            hookIndex - 1
        }
    }

    private fun ensureAnchorCapacity(anchorCount: Int) {
        val requiredCoordinates = anchorCount * ANCHOR_STRIDE
        if (requiredCoordinates <= anchorCoordinates.size) return

        var newCapacity = anchorCoordinates.size
        while (newCapacity < requiredCoordinates) {
            newCapacity *= 2
        }
        anchorCoordinates = anchorCoordinates.copyOf(newCapacity)
    }

    private fun anchorX(index: Int): Float = anchorCoordinates[index * ANCHOR_STRIDE]

    private fun anchorY(index: Int): Float = anchorCoordinates[index * ANCHOR_STRIDE + 1]

    private fun setAnchor(index: Int, x: Float, y: Float) {
        val offset = index * ANCHOR_STRIDE
        anchorCoordinates[offset] = x
        anchorCoordinates[offset + 1] = y
    }

    private fun rectCenterY(bounds: Rect): Float {
        return bounds.top + (bounds.bottom - bounds.top) / 2.0f
    }

    private companion object {
        const val INITIAL_ANCHOR_CAPACITY = 8
        const val ANCHOR_STRIDE = 2
        const val NO_AVATAR_HOOK = -1
        const val MINIMUM_HOOK_HORIZONTAL_ROOM = 0.5f
    }
}
