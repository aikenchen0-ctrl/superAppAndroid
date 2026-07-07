package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class RightRailExpansionTest {
    @Test
    fun defaultRailModeUsesBalancedAccountAndToolSections() {
        val weights = rightRailWeightsForAccountWeight(defaultRightRailAccountWeight())

        assertEquals(0.42f, weights.accountWeight)
        assertEquals(0.58f, weights.toolWeight)
    }

    @Test
    fun accountAreaDragExpandsAccountSection() {
        assertEquals(0.25f, rightRailSectionShiftFraction())
        assertEquals(0.67f, rightRailAccountWeightForAccountAreaDrag())
        assertEquals(
            RightRailWeights(accountWeight = 0.67f, toolWeight = 0.33f),
            rightRailWeightsForAccountWeight(rightRailAccountWeightForAccountAreaDrag())
        )
    }

    @Test
    fun toolAreaDragExpandsToolSection() {
        assertEquals(0.24f, rightRailAccountWeightForToolAreaDrag())
        assertEquals(
            RightRailWeights(accountWeight = 0.24f, toolWeight = 0.76f),
            rightRailWeightsForAccountWeight(rightRailAccountWeightForToolAreaDrag())
        )
    }

    @Test
    fun rightRailUsesAreaBasedExpansionWithoutDiscreteSnap() {
        assertEquals(false, rightRailUsesDiscreteSnapExpansion())
        assertEquals(false, rightRailUsesContinuousDragExpansion())
        assertEquals(true, rightRailUsesAreaBasedExpansion())
        assertEquals(true, rightRailUsesIndependentListScrolling())
        assertEquals(false, rightRailKeepsAccountAndToolSectionHeightsStableWhileScrolling())
    }

    @Test
    fun shortContentStopsExpansionBeforeEmptyFixedSlot() {
        assertEquals(90, rightRailListContentHeightDp(itemCount = 2, itemHeightDp = 42))
        assertEquals(0.42f, rightRailMaxAccountWeightForContentDp(accountCount = 2, toolCount = 8, railHeightDp = 800))
        assertEquals(0.42f, rightRailMinAccountWeightForContentDp(toolCount = 2, railHeightDp = 800))
        assertEquals(true, rightRailStopsExpansionAtContentHeightWhenItemsAreShort())
    }
}
