package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.sidefunction.sideFunctionLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SideFunctionSelectionTest {
    @Test
    fun hapticFeedbackRunsOnlyWhenTheSideFunctionPanelFirstAppears() {
        assertEquals(false, shouldVibrateForSideFunctionPanelOpen(wasPresented = false, isPresented = false))
        assertEquals(true, shouldVibrateForSideFunctionPanelOpen(wasPresented = false, isPresented = true))
        assertEquals(false, shouldVibrateForSideFunctionPanelOpen(wasPresented = true, isPresented = true))
        assertEquals(false, shouldVibrateForSideFunctionPanelOpen(wasPresented = true, isPresented = false))
    }

    @Test
    fun selectionUsesTheDisplayedProgressCoordinatesNearTheTopSafeInset() {
        val progress = BackGestureProgress(
            side = EdgeSide.LEFT,
            dragDistancePx = 100f,
            thresholdPx = 100f,
            touchY = 174f,
            startY = 20f
        )

        assertEquals(
            2,
            sideFunctionSelectionIndex(
                progress = progress,
                itemCount = 5,
                density = 1f,
                viewportHeightPx = 1_000f
            )
        )
    }

    @Test
    fun renderedPanelUsesScreenSafeInsetInsteadOfItsLocalWindowInset() {
        val layout = sideFunctionLayout(itemCount = 5, progress = 1f, density = 1f)

        assertEquals(
            -154f,
            backWaveSideFunctionGroupShiftY(
                layout = layout,
                screenStartY = 980f,
                screenHeightPx = 1_000f,
                density = 1f
            ),
            0.001f
        )
    }
}
