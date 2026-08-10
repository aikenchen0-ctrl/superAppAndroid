package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDemoFeedModelsTest {
    @Test
    fun catalogProvidesThreeTabsAndThreeItemsPerTab() {
        assertEquals(
            listOf(VideoDemoTab.Recommended, VideoDemoTab.Following, VideoDemoTab.Nearby),
            VideoDemoCatalog.tabs
        )
        VideoDemoCatalog.tabs.forEach { tab ->
            assertEquals(3, VideoDemoCatalog.itemsFor(tab).size)
            assertTrue(
                VideoDemoCatalog.itemsFor(tab).all { item ->
                    item.videoUrl.startsWith("https://")
                }
            )
        }
    }

    @Test
    fun pageIndexLoopsAcrossAvailableItems() {
        assertEquals(1, videoDemoPageIndex(currentIndex = 0, direction = VideoDemoPageDirection.Next, itemCount = 3))
        assertEquals(0, videoDemoPageIndex(currentIndex = 2, direction = VideoDemoPageDirection.Next, itemCount = 3))
        assertEquals(2, videoDemoPageIndex(currentIndex = 0, direction = VideoDemoPageDirection.Previous, itemCount = 3))
        assertEquals(0, videoDemoPageIndex(currentIndex = 8, direction = VideoDemoPageDirection.Next, itemCount = 3))
        assertEquals(0, videoDemoPageIndex(currentIndex = -2, direction = VideoDemoPageDirection.Previous, itemCount = 3))
        assertEquals(0, videoDemoPageIndex(currentIndex = 0, direction = VideoDemoPageDirection.Next, itemCount = 0))
    }

    @Test
    fun swipeLayoutTracksFingerAndPlacesAdjacentVideoOffscreen() {
        val next = videoDemoSwipeLayout(
            direction = VideoDemoPageDirection.Next,
            dragOffsetY = -240f,
            viewportHeightPx = 1_000f
        )
        val previous = videoDemoSwipeLayout(
            direction = VideoDemoPageDirection.Previous,
            dragOffsetY = 250f,
            viewportHeightPx = 1_000f
        )

        assertEquals(-240f, next.currentTranslationY, 0.001f)
        assertEquals(760f, next.adjacentTranslationY, 0.001f)
        assertEquals(250f, previous.currentTranslationY, 0.001f)
        assertEquals(-750f, previous.adjacentTranslationY, 0.001f)
    }

    @Test
    fun swipeCommitsOnlyAtTheConfiguredViewportFraction() {
        assertFalse(videoDemoShouldCommitSwipe(dragOffsetY = -219f, viewportHeightPx = 1_000f))
        assertTrue(videoDemoShouldCommitSwipe(dragOffsetY = -220f, viewportHeightPx = 1_000f))
        assertTrue(videoDemoShouldCommitSwipe(dragOffsetY = 220f, viewportHeightPx = 1_000f))
        assertFalse(videoDemoShouldCommitSwipe(dragOffsetY = 220f, viewportHeightPx = 0f))
    }

    @Test
    fun togglesLocalActionStateWithoutTouchingOtherIds() {
        val initial = emptySet<String>()
        val afterAdd = toggleVideoDemoId(initial, "video-1")
        val afterRemove = toggleVideoDemoId(afterAdd, "video-1")

        assertEquals(setOf("video-1"), afterAdd)
        assertEquals(emptySet<String>(), afterRemove)
    }

    @Test
    fun meteorPreviewAcceptsVerticalUpAndDownFromBothEdges() {
        EdgeSide.entries.forEach { side ->
            assertTrue(
                isMeteorPreviewGesture(
                    side = side,
                    dx = 8f,
                    dy = 420f,
                    minDistancePx = 48f
                )
            )
            assertTrue(
                isMeteorPreviewGesture(
                    side = side,
                    dx = 8f,
                    dy = -420f,
                    minDistancePx = 48f
                )
            )
        }
        assertFalse(
            isMeteorPreviewGesture(
                side = EdgeSide.RIGHT,
                dx = -160f,
                dy = 120f,
                minDistancePx = 48f
            )
        )
    }

    @Test
    fun meteorProgressIsClampedToOne() {
        assertEquals(
            0f,
            meteorPreviewProgress(dx = 0f, dy = 0f, minDistancePx = 48f),
            0.001f
        )
        assertEquals(
            0.5f,
            meteorPreviewProgress(dx = 0f, dy = 24f, minDistancePx = 48f),
            0.001f
        )
        assertEquals(
            0.5f,
            meteorPreviewProgress(dx = 0f, dy = -24f, minDistancePx = 48f),
            0.001f
        )
        assertEquals(
            1f,
            meteorPreviewProgress(dx = 0f, dy = 240f, minDistancePx = 48f),
            0.001f
        )
    }
}
