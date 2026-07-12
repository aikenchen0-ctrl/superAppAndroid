package com.paifa.ubikitouch.accessibility

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatConnectorGeometryTest {
    @Test
    fun userConnectorUsesAvatarRightEdgeAndBubbleLeftEdge() {
        val layerBounds = Rect(left = 10f, top = 20f, right = 310f, bottom = 520f)
        val avatarBounds = Rect(left = 14f, top = 40f, right = 48f, bottom = 74f)
        val bubbleBounds = Rect(left = 112f, top = 90f, right = 242f, bottom = 138f)

        val line = createChatConnectorLine(
            avatarBounds = avatarBounds,
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            target = FloatingChatConnectionTarget.User
        )

        assertEquals(Offset(38f, 37f), line.start)
        assertEquals(Offset(86f, 37f), line.cornerStart)
        assertEquals(Offset(86f, 94f), line.cornerEnd)
        assertEquals(Offset(106f, 94f), line.end)
    }

    @Test
    fun accountConnectorUsesAvatarLeftEdgeAndBubbleRightEdge() {
        val layerBounds = Rect(left = 10f, top = 20f, right = 310f, bottom = 520f)
        val avatarBounds = Rect(left = 264f, top = 40f, right = 298f, bottom = 74f)
        val bubbleBounds = Rect(left = 72f, top = 90f, right = 210f, bottom = 138f)

        val line = createChatConnectorLine(
            avatarBounds = avatarBounds,
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            target = FloatingChatConnectionTarget.Account
        )

        assertEquals(Offset(254f, 37f), line.start)
        assertEquals(Offset(206f, 37f), line.cornerStart)
        assertEquals(Offset(206f, 94f), line.cornerEnd)
        assertEquals(Offset(196f, 94f), line.end)
    }

    @Test
    fun connectorTreeUsesOneTrunkForMessagesSharingOneAvatar() {
        val layerBounds = Rect(left = 0f, top = 0f, right = 320f, bottom = 520f)
        val messageViewportBounds = Rect(left = 70f, top = 20f, right = 270f, bottom = 460f)
        val avatarBounds = Rect(left = 8f, top = 32f, right = 42f, bottom = 66f)

        val tree = createChatConnectorTree(
            avatarBounds = avatarBounds,
            bubbleBounds = listOf(
                Rect(left = 120f, top = 54f, right = 220f, bottom = 94f),
                Rect(left = 146f, top = 116f, right = 246f, bottom = 156f)
            ),
            layerBounds = layerBounds,
            visibleRootBounds = messageViewportBounds,
            target = FloatingChatConnectionTarget.User,
            hasMessagesAbove = false,
            hasMessagesBelow = false
        ) ?: error("Expected connector tree")

        assertEquals(Offset(90f, 49f), tree.trunkStart)
        assertEquals(Offset(90f, 136f), tree.trunkEnd)
        assertEquals(ChatConnectorBranch(Offset(42f, 49f), Offset(90f, 49f)), tree.avatarBranch)
        assertEquals(
            listOf(
                ChatConnectorBranch(Offset(90f, 74f), Offset(124f, 74f)),
                ChatConnectorBranch(Offset(90f, 136f), Offset(150f, 136f))
            ),
            tree.messageBranches
        )
    }

    @Test
    fun connectorTreeMovesTrunkLeftWhenWideHomeBubbleTouchesDefaultTrunk() {
        val layerBounds = Rect(left = 0f, top = 0f, right = 320f, bottom = 520f)
        val messageViewportBounds = Rect(left = 70f, top = 20f, right = 270f, bottom = 460f)
        val avatarBounds = Rect(left = 8f, top = 32f, right = 42f, bottom = 66f)
        val bubbleBounds = Rect(left = 92f, top = 116f, right = 270f, bottom = 166f)

        val tree = createChatConnectorTree(
            avatarBounds = avatarBounds,
            bubbleBounds = listOf(bubbleBounds),
            layerBounds = layerBounds,
            visibleRootBounds = messageViewportBounds,
            target = FloatingChatConnectionTarget.User,
            hasMessagesAbove = false,
            hasMessagesBelow = false
        ) ?: error("Expected connector tree")
        val branch = tree.messageBranches.single()

        assertTrue(tree.trunkStart.x < branch.end.x)
        assertEquals(bubbleBounds.left + imModuleConnectionLineBubbleOverlapPx(), branch.end.x)
        assertTrue(branch.end.x - tree.trunkStart.x >= imModuleConnectionLineMinimumBranchPx())
        assertTrue(tree.avatarBranch!!.end.x > tree.avatarBranch.start.x)
    }

    @Test
    fun connectorTreeMovesTrunkRightWhenWideAccountBubbleTouchesDefaultTrunk() {
        val layerBounds = Rect(left = 0f, top = 0f, right = 320f, bottom = 520f)
        val messageViewportBounds = Rect(left = 70f, top = 20f, right = 270f, bottom = 460f)
        val avatarBounds = Rect(left = 278f, top = 32f, right = 312f, bottom = 66f)
        val bubbleBounds = Rect(left = 70f, top = 116f, right = 228f, bottom = 166f)

        val tree = createChatConnectorTree(
            avatarBounds = avatarBounds,
            bubbleBounds = listOf(bubbleBounds),
            layerBounds = layerBounds,
            visibleRootBounds = messageViewportBounds,
            target = FloatingChatConnectionTarget.Account,
            hasMessagesAbove = false,
            hasMessagesBelow = false
        ) ?: error("Expected connector tree")
        val branch = tree.messageBranches.single()

        assertTrue(tree.trunkStart.x > branch.end.x)
        assertEquals(bubbleBounds.right - imModuleConnectionLineBubbleOverlapPx(), branch.end.x)
        assertTrue(tree.trunkStart.x - branch.end.x >= imModuleConnectionLineMinimumBranchPx())
        assertTrue(tree.avatarBranch!!.end.x < tree.avatarBranch.start.x)
    }

    @Test
    fun connectorTreeKeepsContinuousTrunkThroughRoundedHooks() {
        val tree = ChatConnectorTree(
            trunkStart = Offset(90f, 49f),
            trunkEnd = Offset(90f, 136f),
            avatarBranch = ChatConnectorBranch(
                start = Offset(42f, 49f),
                end = Offset(90f, 49f)
            ),
            messageBranches = listOf(
                ChatConnectorBranch(Offset(90f, 74f), Offset(119f, 74f)),
                ChatConnectorBranch(Offset(90f, 136f), Offset(145f, 136f))
            )
        )

        val geometry = createChatConnectorBraceGeometry(tree)

        assertEquals(
            listOf(
                ChatConnectorBranch(Offset(90f, 49f), Offset(90f, 136f))
            ),
            geometry.trunkSegments
        )
        assertEquals(
            listOf(
                ChatConnectorBraceHook(Offset(90f, 49f), Offset(42f, 49f), 12f, 1f),
                ChatConnectorBraceHook(Offset(90f, 74f), Offset(119f, 74f), 7.25f, -1f),
                ChatConnectorBraceHook(Offset(90f, 136f), Offset(145f, 136f), 12f, -1f)
            ),
            geometry.hooks
        )
        assertEquals(
            ChatConnectorRoundedHookGeometry(
                curveStart = Offset(90f, 61f),
                curveControl = Offset(90f, 49f),
                horizontalStart = Offset(78f, 49f),
                branchEnd = Offset(42f, 49f)
            ),
            geometry.hooks[0].roundedElbowGeometry()
        )
        assertEquals(Offset(97.25f, 74f), geometry.hooks[1].branchStartAvoidingJointOverlap())
        assertEquals(Offset(102f, 136f), geometry.hooks[2].branchStartAvoidingJointOverlap())
    }

    @Test
    fun closeSingleBubbleConnectorKeepsVisibleTrunkBetweenRoundedHooks() {
        val tree = ChatConnectorTree(
            trunkStart = Offset(90f, 100f),
            trunkEnd = Offset(90f, 130f),
            avatarBranch = ChatConnectorBranch(
                start = Offset(42f, 100f),
                end = Offset(90f, 100f)
            ),
            messageBranches = listOf(
                ChatConnectorBranch(Offset(90f, 130f), Offset(119f, 130f))
            )
        )

        val geometry = createChatConnectorBraceGeometry(tree)
        val visibleSegment = geometry.trunkSegments.single()

        assertEquals(90f, visibleSegment.start.x)
        assertEquals(90f, visibleSegment.end.x)
        assertTrue(visibleSegment.end.y - visibleSegment.start.y >= imModuleConnectionLineStrokeWidthPx())
    }

    @Test
    fun edgeAnchorsAreConvertedIntoConnectorLayerCoordinates() {
        val connectorLayerBounds = Rect(left = 42f, top = 88f, right = 342f, bottom = 588f)
        val bubbleBoundsInRoot = Rect(left = 150f, top = 208f, right = 250f, bottom = 248f)

        val localAnchor = bubbleBoundsInRoot.leftCenterIn(connectorLayerBounds)

        assertEquals(Offset(108f, 140f), localAnchor)
    }

    @Test
    fun fullLayoutBoundsKeepConnectorAnchorStableWhenBubbleIsPartiallyClipped() {
        val connectorLayerBounds = Rect(left = 0f, top = 100f, right = 320f, bottom = 500f)
        val fullBubbleBounds = rootBoundsFromPosition(
            positionInRoot = Offset(120f, 76f),
            width = 140,
            height = 80
        )
        val clippedVisibleBubbleBounds = Rect(left = 120f, top = 100f, right = 260f, bottom = 156f)

        val fullAnchor = fullBubbleBounds.leftCenterIn(connectorLayerBounds)
        val clippedAnchor = clippedVisibleBubbleBounds.leftCenterIn(connectorLayerBounds)

        assertEquals(Offset(120f, 16f), fullAnchor)
        assertEquals(Offset(120f, 28f), clippedAnchor)
        assertNotEquals(clippedAnchor, fullAnchor)
    }

    @Test
    fun connectorTreeExtendsTrunkUpForMessagesAboveTheViewport() {
        val connectorLayerBounds = Rect(left = 0f, top = 100f, right = 320f, bottom = 500f)
        val messageViewportBounds = Rect(left = 48f, top = 120f, right = 272f, bottom = 460f)
        val avatarBounds = Rect(left = 8f, top = 180f, right = 42f, bottom = 214f)

        val tree = createChatConnectorTree(
            avatarBounds = avatarBounds,
            bubbleBounds = emptyList(),
            layerBounds = connectorLayerBounds,
            visibleRootBounds = messageViewportBounds,
            target = FloatingChatConnectionTarget.User,
            hasMessagesAbove = true,
            hasMessagesBelow = false
        ) ?: error("Expected connector tree")

        assertEquals(Offset(90f, 20f), tree.trunkStart)
        assertEquals(Offset(90f, 97f), tree.trunkEnd)
        assertEquals(ChatConnectorBranch(Offset(42f, 97f), Offset(90f, 97f)), tree.avatarBranch)
        assertTrue(tree.messageBranches.isEmpty())
    }

    @Test
    fun homeOverviewConnectorKeysAreScopedPerUnreadSource() {
        val first = FloatingChatMessage(
            id = "home-unread-account-a-li-si",
            type = FloatingChatMessageType.Text,
            text = "first",
            fromMe = false,
            senderName = "Li Si",
            time = "10:00",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = "li-si"
        )
        val second = first.copy(id = "home-unread-account-b-li-si")
        val otherSource = first.copy(
            id = "home-unread-account-a-he-miao",
            connectionTargetId = "he-miao"
        )

        assertEquals(
            homeOverviewConnectorKeyDebugId(first),
            homeOverviewConnectorKeyDebugId(second)
        )
        assertNotEquals(
            homeOverviewConnectorKeyDebugId(first),
            homeOverviewConnectorKeyDebugId(otherSource)
        )
    }

    @Test
    fun leftRailVirtualSessionAvatarBoundsInferOffscreenItemsFromVisibleItems() {
        val viewport = Rect(left = 0f, top = 0f, right = 56f, bottom = 220f)

        val bounds = leftRailVirtualSessionAvatarBounds(
            sessionIds = listOf("session-0", "session-1", "session-2"),
            visibleItems = listOf(
                LeftRailVisibleSessionItem(index = 1, offset = 18, size = 42),
                LeftRailVisibleSessionItem(index = 2, offset = 66, size = 42)
            ),
            viewport = viewport,
            fallbackStepPx = 48f
        )

        assertEquals(Rect(left = 0f, top = -30f, right = 42f, bottom = 12f), bounds["session-0"])
        assertEquals(Rect(left = 0f, top = 18f, right = 42f, bottom = 60f), bounds["session-1"])
        assertEquals(Rect(left = 0f, top = 66f, right = 42f, bottom = 108f), bounds["session-2"])
    }
}
