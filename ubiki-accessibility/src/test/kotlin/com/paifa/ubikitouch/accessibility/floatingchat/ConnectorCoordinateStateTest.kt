package com.paifa.ubikitouch.accessibility.floatingchat

import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorCoordinateState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorAvatarLane
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorOffscreenIndex
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorTargetKey
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorViewportEdgeState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.LeftRailVisibleSessionItem
import com.paifa.ubikitouch.accessibility.floatingchat.chat.RailPinnedAvatarEdge
import com.paifa.ubikitouch.accessibility.floatingchat.chat.RightRailVisibleAccountItem
import com.paifa.ubikitouch.accessibility.floatingchat.chat.leftRailPinnedSelectedAvatarEdge
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rightRailPinnedSelectedAccountEdge
import com.paifa.ubikitouch.accessibility.floatingchat.chat.updateOffscreenConnectorEdges
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorCoordinateStateTest {
    @Test
    fun virtualUserAvatarBatchInvalidatesOnceAndSkipsUnchangedBounds() {
        val state = ConnectorCoordinateState()
        val viewport = Rect(0f, 0f, 48f, 240f)
        val visibleItems = listOf(
            LeftRailVisibleSessionItem(index = 0, offset = 0, size = 40),
            LeftRailVisibleSessionItem(index = 1, offset = 48, size = 40)
        )

        state.updateVirtualUserAvatars(
            sessionIds = listOf("session-1", "session-2", "session-3"),
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = 48f
        )

        assertEquals(1, state.version)

        state.updateVirtualUserAvatars(
            sessionIds = listOf("session-1", "session-2", "session-3"),
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = 48f
        )

        assertEquals(1, state.version)
    }

    @Test
    fun virtualAccountAvatarBatchInvalidatesOnceAndSkipsUnchangedBounds() {
        val state = ConnectorCoordinateState()
        val viewport = Rect(200f, 0f, 248f, 240f)
        val visibleItems = listOf(
            RightRailVisibleAccountItem(index = 0, offset = 0, size = 40),
            RightRailVisibleAccountItem(index = 1, offset = 48, size = 40)
        )

        state.updateVirtualAccountAvatars(
            accountIds = listOf("account-1", "account-2", "account-3"),
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = 48f
        )

        assertEquals(1, state.version)

        state.updateVirtualAccountAvatars(
            accountIds = listOf("account-1", "account-2", "account-3"),
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = 48f
        )

        assertEquals(1, state.version)
    }

    @Test
    fun offscreenVirtualUserMovementOnSamePinnedEdgeDoesNotInvalidate() {
        val state = ConnectorCoordinateState()
        val viewport = Rect(0f, 0f, 48f, 40f)
        state.updateUserViewport(viewport)
        state.updateVirtualUserAvatars(
            sessionIds = listOf("session-1", "session-2", "session-3"),
            visibleItems = listOf(LeftRailVisibleSessionItem(index = 2, offset = 100, size = 20)),
            viewport = viewport,
            fallbackStepPx = 30f
        )
        val versionBeforeOffscreenShift = state.version

        state.updateVirtualUserAvatars(
            sessionIds = listOf("session-1", "session-2", "session-3"),
            visibleItems = listOf(LeftRailVisibleSessionItem(index = 2, offset = 110, size = 20)),
            viewport = viewport,
            fallbackStepPx = 30f
        )

        assertEquals(versionBeforeOffscreenShift, state.version)
    }

    @Test
    fun offscreenVirtualAccountMovementOnSamePinnedEdgeDoesNotInvalidate() {
        val state = ConnectorCoordinateState()
        val viewport = Rect(200f, 0f, 248f, 40f)
        state.updateAccountViewport(viewport)
        state.updateVirtualAccountAvatars(
            accountIds = listOf("account-1", "account-2", "account-3"),
            visibleItems = listOf(RightRailVisibleAccountItem(index = 2, offset = 100, size = 20)),
            viewport = viewport,
            fallbackStepPx = 30f
        )
        val versionBeforeOffscreenShift = state.version

        state.updateVirtualAccountAvatars(
            accountIds = listOf("account-1", "account-2", "account-3"),
            visibleItems = listOf(RightRailVisibleAccountItem(index = 2, offset = 110, size = 20)),
            viewport = viewport,
            fallbackStepPx = 30f
        )

        assertEquals(versionBeforeOffscreenShift, state.version)
    }

    @Test
    fun selectedSessionEdgeIsResolvedWithoutChangingVirtualBoundsContract() {
        val edge = leftRailPinnedSelectedAvatarEdge(
            sessionIds = listOf("session-1", "session-2", "session-3"),
            selectedSessionId = "session-1",
            visibleItems = listOf(
                LeftRailVisibleSessionItem(index = 1, offset = 0, size = 42),
                LeftRailVisibleSessionItem(index = 2, offset = 48, size = 42)
            ),
            viewportHeightPx = 120f,
            fallbackStepPx = 48f
        )

        assertEquals(RailPinnedAvatarEdge.Top, edge)
    }

    @Test
    fun selectedAccountEdgeRespectsReverseLayout() {
        val edge = rightRailPinnedSelectedAccountEdge(
            accountIds = listOf("account-1", "account-2", "account-3", "account-4"),
            selectedAccountId = "account-4",
            visibleItems = listOf(
                RightRailVisibleAccountItem(index = 1, offset = 48, size = 42)
            ),
            viewportHeightPx = 120f,
            fallbackStepPx = 48f,
            reverseLayout = true
        )

        assertEquals(RailPinnedAvatarEdge.Top, edge)
    }

    @Test
    fun selectedAccountBelowVisibleRangePinsToBottom() {
        val edge = rightRailPinnedSelectedAccountEdge(
            accountIds = listOf("account-1", "account-2", "account-3"),
            selectedAccountId = "account-1",
            visibleItems = listOf(
                RightRailVisibleAccountItem(index = 1, offset = 0, size = 42),
                RightRailVisibleAccountItem(index = 2, offset = 48, size = 42)
            ),
            viewportHeightPx = 120f,
            fallbackStepPx = 48f,
            reverseLayout = true
        )

        assertEquals(RailPinnedAvatarEdge.Bottom, edge)
    }

    @Test
    fun reusableOffscreenEdgeMapClearsStaleTargets() {
        val activeKey = ConnectorTargetKey(
            target = FloatingChatConnectionTarget.User,
            targetId = "active",
            lane = ConnectorAvatarLane.Session
        )
        val staleKey = activeKey.copy(targetId = "stale")
        val destination = linkedMapOf(
            staleKey to ConnectorViewportEdgeState(hasAbove = true)
        )

        updateOffscreenConnectorEdges(
            index = ConnectorOffscreenIndex(
                beforeByIndex = listOf(setOf(activeKey)),
                afterByIndex = listOf(setOf(activeKey))
            ),
            firstVisibleIndex = 0,
            lastVisibleIndex = 0,
            destination = destination
        )

        assertEquals(
            mapOf(activeKey to ConnectorViewportEdgeState(hasAbove = true, hasBelow = true)),
            destination
        )
    }
}
