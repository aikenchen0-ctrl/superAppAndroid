package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGestureExclusionOverlayControllerTest {
    @Test
    fun fullScreenExclusionOverlayUsesANonTouchableAccessibilityWindow() {
        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            fullScreenGestureExclusionOverlayWindowType()
        )

        val flags = fullScreenGestureExclusionOverlayWindowFlags()
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN != 0)
    }

    @Test
    fun fullScreenExclusionKeepsConfiguredNativeRectsInInputOrderAtSourceSize() {
        val rects = nativeSystemGestureExclusionRects(
            sourceWidthPx = 1080,
            sourceHeightPx = 2400,
            targetWidthPx = 1080,
            targetHeightPx = 2400,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.RIGHT, 1, 1056, 1200, 1080, 1800),
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 100, 24, 700)
            )
        )

        assertEquals(
            listOf(
                SystemGestureExclusionRect(1056, 1200, 1080, 1800),
                SystemGestureExclusionRect(0, 100, 24, 700)
            ),
            rects
        )
    }

    @Test
    fun fullScreenExclusionScalesAndClampsRectsToTheActualViewBounds() {
        val rects = nativeSystemGestureExclusionRects(
            sourceWidthPx = 1080,
            sourceHeightPx = 2400,
            targetWidthPx = 1000,
            targetHeightPx = 2200,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.RIGHT, 0, 1056, 0, 1080, 2400),
                NativeTouchInterceptRect(EdgeSide.LEFT, 1, -108, -240, 108, 2640)
            )
        )

        assertEquals(
            listOf(
                SystemGestureExclusionRect(978, 0, 1000, 2200),
                SystemGestureExclusionRect(0, 0, 100, 2200)
            ),
            rects
        )
    }

    @Test
    fun fullScreenExclusionDropsEmptyOrInvalidRects() {
        val rects = nativeSystemGestureExclusionRects(
            sourceWidthPx = 1080,
            sourceHeightPx = 2400,
            targetWidthPx = 1000,
            targetHeightPx = 2200,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 20, 100, 20, 400),
                NativeTouchInterceptRect(EdgeSide.LEFT, 1, 20, 500, 48, 500),
                NativeTouchInterceptRect(EdgeSide.RIGHT, 2, -100, 100, -10, 400),
                NativeTouchInterceptRect(EdgeSide.RIGHT, 3, 1200, 100, 1280, 400)
            )
        )

        assertEquals(emptyList<SystemGestureExclusionRect>(), rects)
        assertEquals(
            emptyList<SystemGestureExclusionRect>(),
            nativeSystemGestureExclusionRects(
                sourceWidthPx = 0,
                sourceHeightPx = 2400,
                targetWidthPx = 1000,
                targetHeightPx = 2200,
                intercepts = emptyList()
            )
        )
    }

    @Test
    fun rootTakeoverDoesNotApplyAtTheOfficialTwoHundredDpLimit() {
        val sides = rootBackGestureTakeoverSides(
            density = 2f,
            screenWidthPx = 1080,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 0, 24, 400)
            )
        )

        assertEquals(emptySet<EdgeSide>(), sides)
    }

    @Test
    fun rootTakeoverAppliesOnlyToTheSideWhoseCoverageExceedsTheLimit() {
        val sides = rootBackGestureTakeoverSides(
            density = 2f,
            screenWidthPx = 1080,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 0, 24, 401),
                NativeTouchInterceptRect(EdgeSide.RIGHT, 1, 1056, 0, 1080, 400)
            )
        )

        assertEquals(setOf(EdgeSide.LEFT), sides)
    }

    @Test
    fun rootTakeoverMergesOverlappingIntervalsBeforeMeasuringCoverage() {
        val sides = rootBackGestureTakeoverSides(
            density = 2f,
            screenWidthPx = 1080,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 0, 24, 300),
                NativeTouchInterceptRect(EdgeSide.LEFT, 1, 0, 100, 24, 400)
            )
        )

        assertEquals(emptySet<EdgeSide>(), sides)
    }

    @Test
    fun rootTakeoverIgnoresRectsThatDoNotTouchTheirPhysicalEdge() {
        val sides = rootBackGestureTakeoverSides(
            density = 2f,
            screenWidthPx = 1080,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 12, 0, 36, 401),
                NativeTouchInterceptRect(EdgeSide.RIGHT, 1, 1044, 0, 1068, 401)
            )
        )

        assertEquals(emptySet<EdgeSide>(), sides)
    }

    @Test
    fun rootTakeoverRejectsInvalidDisplayInputs() {
        val intercepts = listOf(
            NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 0, 24, 1_000)
        )

        assertEquals(emptySet<EdgeSide>(), rootBackGestureTakeoverSides(0f, 1080, intercepts))
        assertEquals(emptySet<EdgeSide>(), rootBackGestureTakeoverSides(Float.NaN, 1080, intercepts))
        assertEquals(emptySet<EdgeSide>(), rootBackGestureTakeoverSides(2f, 0, intercepts))
    }

    @Test
    fun fullScreenExclusionOverlayFollowsTheResolvedTouchSurfacePolicy() {
        assertTrue(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.NativeTouchInteraction,
                floatingChatOwnsSurface = false
            )
        )
        assertTrue(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.NativeTouchInteraction,
                floatingChatOwnsSurface = true
            )
        )
        assertTrue(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.SecureSlimOverlay,
                floatingChatOwnsSurface = true
            )
        )
        assertTrue(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.SlimOverlayFallback,
                floatingChatOwnsSurface = true
            )
        )
        assertFalse(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.SecureSlimOverlay,
                floatingChatOwnsSurface = false
            )
        )
        assertFalse(
            shouldShowFullScreenGestureExclusionOverlay(
                resolvedMode = ResolvedGestureInputMode.SlimOverlayFallback,
                floatingChatOwnsSurface = false
            )
        )
    }

    @Test
    fun floatingChatFallbackExclusionUsesItsActualFixedTouchTargetWidth() {
        val rect = fullScreenGestureExclusionIntercepts(
            sourceWidthPx = 1080,
            sourceHeightPx = 2400,
            density = 1f,
            configs = listOf(
                EdgeZoneConfig(
                    side = EdgeSide.LEFT,
                    zoneId = 4,
                    thicknessDp = 72,
                    topInsetPercent = 0,
                    bottomInsetPercent = 0
                )
            ),
            resolvedMode = ResolvedGestureInputMode.SlimOverlayFallback,
            floatingChatOwnsSurface = true
        ).single()

        assertEquals(0, rect.left)
        assertEquals(24, rect.right)
    }

    @Test
    fun navigationProtectionPlansRootTakeoverEvenWhenSlimWindowsOwnTheTouchSurface() {
        val plan = gestureNavigationProtectionPlan(
            sdkInt = 29,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 2f,
            configs = listOf(
                EdgeZoneConfig(
                    side = EdgeSide.LEFT,
                    zoneId = 0,
                    topInsetPercent = 0,
                    bottomInsetPercent = 80
                ),
                EdgeZoneConfig(
                    side = EdgeSide.LEFT,
                    zoneId = 1,
                    topInsetPercent = 20,
                    bottomInsetPercent = 60
                )
            ),
            resolvedMode = ResolvedGestureInputMode.SlimOverlayFallback,
            floatingChatOwnsSurface = false
        )

        assertEquals(emptyList<NativeTouchInterceptRect>(), plan.systemExclusionIntercepts)
        assertEquals(setOf(EdgeSide.LEFT), plan.rootTakeoverSides)
    }

    @Test
    fun legacyNavigationProtectionPlansOverscanForAnyActivePhysicalEdge() {
        val plan = gestureNavigationProtectionPlan(
            sdkInt = 28,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 2f,
            configs = listOf(
                EdgeZoneConfig(
                    side = EdgeSide.RIGHT,
                    zoneId = 0,
                    topInsetPercent = 0,
                    bottomInsetPercent = 90
                )
            ),
            resolvedMode = ResolvedGestureInputMode.SlimOverlayFallback,
            floatingChatOwnsSurface = false
        )

        assertEquals(setOf(EdgeSide.RIGHT), plan.rootTakeoverSides)
    }

    @Test
    fun fullScreenOutlineGeometryKeepsSideAndZoneForColorRendering() {
        val outlines = nativeSystemGestureOutlineRects(
            sourceWidthPx = 1080,
            sourceHeightPx = 2400,
            targetWidthPx = 1080,
            targetHeightPx = 2400,
            intercepts = listOf(
                NativeTouchInterceptRect(EdgeSide.LEFT, 0, 0, 100, 24, 700),
                NativeTouchInterceptRect(EdgeSide.RIGHT, 2, 1056, 1200, 1080, 1800)
            )
        )

        assertEquals(
            listOf(
                SystemGestureOutlineRect(
                    side = EdgeSide.LEFT,
                    zoneId = 0,
                    bounds = SystemGestureExclusionRect(0, 100, 24, 700)
                ),
                SystemGestureOutlineRect(
                    side = EdgeSide.RIGHT,
                    zoneId = 2,
                    bounds = SystemGestureExclusionRect(1056, 1200, 1080, 1800)
                )
            ),
            outlines
        )
    }
}
