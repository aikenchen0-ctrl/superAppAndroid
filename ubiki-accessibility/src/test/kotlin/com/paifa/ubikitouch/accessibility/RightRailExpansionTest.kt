package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.tools.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RightRailExpansionTest {
    @Test
    fun defaultRailModeUsesBalancedAccountAndToolSections() {
        val weights = rightRailWeightsForAccountWeight(defaultRightRailAccountWeight())

        assertEquals(0.48f, weights.accountWeight)
        assertEquals(0.52f, weights.toolWeight)
    }

    @Test
    fun accountAreaDragExpandsAccountSection() {
        assertEquals(0.25f, rightRailSectionShiftFraction())
        assertEquals(0.70f, rightRailAccountWeightForAccountAreaDrag())
        assertEquals(
            RightRailWeights(accountWeight = 0.70f, toolWeight = 0.30f),
            rightRailWeightsForAccountWeight(rightRailAccountWeightForAccountAreaDrag())
        )
    }

    @Test
    fun toolAreaDragExpandsToolSection() {
        assertEquals(0.30f, rightRailAccountWeightForToolAreaDrag())
        assertEquals(
            RightRailWeights(accountWeight = 0.30f, toolWeight = 0.70f),
            rightRailWeightsForAccountWeight(rightRailAccountWeightForToolAreaDrag())
        )
    }

    @Test
    fun rightRailUsesAreaBasedExpansionWithoutDiscreteSnap() {
        assertEquals(false, rightRailUsesDiscreteSnapExpansion())
        assertEquals(false, rightRailUsesContinuousDragExpansion())
        assertEquals(true, rightRailUsesAreaBasedExpansion())
        assertEquals(true, rightRailUsesIndependentListScrolling())
        assertEquals(true, rightRailKeepsAccountAndToolSectionHeightsStableWhileScrolling())
    }

    /** 测试流程：滚动联系人或功能列表后松手，确认源码没有延迟恢复默认高度的回弹任务。 */
    @Test
    fun scrollingKeepsTheExpandedSectionAtSeventyPercent() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/RightCoordinateRail.kt"
        ).readText()

        assertFalse(source.contains("delay(2_000)"))
        assertEquals(0.70f, rightRailAccountWeightForAccountAreaDrag())
        assertEquals(0.30f, rightRailAccountWeightForToolAreaDrag())
    }

    @Test
    fun selectedAccountDoesNotPinWhileTheAccountListScrolls() {
        assertEquals(false, rightRailPinsSelectedAccountAvatarWhileScrolledOffscreen())
    }

    @Test
    fun shortContentStopsExpansionBeforeEmptyFixedSlot() {
        assertEquals(106, rightRailListContentHeightDp(itemCount = 2, itemHeightDp = 50))
        assertEquals(0.48f, rightRailMaxAccountWeightForContentDp(accountCount = 2, toolCount = 8, railHeightDp = 800))
        assertEquals(0.48f, rightRailMinAccountWeightForContentDp(toolCount = 2, railHeightDp = 800))
        assertEquals(true, rightRailStopsExpansionAtContentHeightWhenItemsAreShort())
    }
}
