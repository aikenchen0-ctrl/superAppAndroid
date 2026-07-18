package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlin.math.roundToInt

internal data class RightRailWeights(
    val accountWeight: Float,
    val toolWeight: Float
)

internal fun defaultRightRailAccountWeight(): Float = RightRailDefaultAccountWeight

internal fun minRightRailAccountWeight(): Float = RightRailMinAccountWeight

internal fun maxRightRailAccountWeight(): Float = RightRailMaxAccountWeight

internal fun rightRailSectionShiftFraction(): Float = RightRailSectionShiftFraction

internal fun rightRailWidthDp(): Int = RightRailWidthDp

internal fun rightRailAvatarScreenEdgeInsetPx(): Int = RailScreenEdgeInsetPx

internal fun rightRailToolIconScreenEdgeInsetPx(): Int = RailScreenEdgeInsetPx

internal fun rightRailAvatarSizeDp(): Int = RailAvatarSizeDp

internal fun rightRailToolButtonWidthDp(): Int = RailToolButtonWidthDp

internal fun rightRailToolButtonHeightDp(): Int = RailToolButtonHeightDp

internal fun rightRailItemGapDp(): Int = RightRailItemGapDp

internal fun rightRailSectionResizeMs(): Int = RightRailSectionResizeMs

internal fun rightRailUsesDiscreteSnapExpansion(): Boolean = false

internal fun rightRailUsesContinuousDragExpansion(): Boolean = false

internal fun rightRailUsesAreaBasedExpansion(): Boolean = true

internal fun rightRailUsesIndependentListScrolling(): Boolean = true

internal fun rightRailKeepsAccountAndToolSectionHeightsStableWhileScrolling(): Boolean = false

internal fun rightRailStopsExpansionAtContentHeightWhenItemsAreShort(): Boolean = true

internal fun rightRailPinsSelectedAccountAvatarWhileScrolledOffscreen(): Boolean = true

internal fun rightRailWeightsForAccountWeight(accountWeight: Float): RightRailWeights {
    val safeAccountWeight = rightRailNormalizeAccountWeight(accountWeight).coerceIn(
        minimumValue = minRightRailAccountWeight(),
        maximumValue = maxRightRailAccountWeight()
    )
    return RightRailWeights(
        accountWeight = safeAccountWeight,
        toolWeight = rightRailNormalizeAccountWeight(1f - safeAccountWeight)
    )
}

internal fun rightRailAccountWeightAfterDrag(
    currentWeight: Float,
    deltaY: Float,
    railHeightPx: Float,
    minWeight: Float = minRightRailAccountWeight(),
    maxWeight: Float = maxRightRailAccountWeight()
): Float {
    if (railHeightPx <= 0f) {
        return currentWeight.coerceIn(minWeight, maxWeight)
    }
    return rightRailNormalizeAccountWeight(currentWeight + deltaY / railHeightPx)
        .coerceIn(minWeight, maxWeight)
}

internal fun rightRailAccountWeightAfterToolDrag(
    currentWeight: Float,
    deltaY: Float,
    railHeightPx: Float
): Float {
    return rightRailAccountWeightAfterDrag(
        currentWeight = currentWeight,
        deltaY = deltaY,
        railHeightPx = railHeightPx
    )
}

internal fun rightRailAccountWeightForAccountAreaDrag(): Float {
    return rightRailNormalizeAccountWeight(
        (defaultRightRailAccountWeight() + rightRailSectionShiftFraction())
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
    )
}

internal fun rightRailAccountWeightForToolAreaDrag(): Float {
    return rightRailNormalizeAccountWeight(
        (defaultRightRailAccountWeight() - rightRailSectionShiftFraction())
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
    )
}

internal fun rightRailMaxAccountWeightForContentDp(
    accountCount: Int,
    toolCount: Int,
    railHeightDp: Int
): Float {
    if (railHeightDp <= 0) return maxRightRailAccountWeight()
    val accountContentHeight = rightRailListContentHeightDp(
        itemCount = accountCount,
        itemHeightDp = RailAvatarSizeDp
    )
    val toolMinimumHeight = rightRailListContentHeightDp(
        itemCount = toolCount.coerceAtMost(RightRailMinimumVisibleToolCount),
        itemHeightDp = RailToolButtonHeightDp
    )
    return rightRailNormalizeAccountWeight(
        ((accountContentHeight + RightRailExpansionSlackDp).toFloat() / railHeightDp)
            .coerceAtMost(1f - toolMinimumHeight.toFloat() / railHeightDp)
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtLeast(defaultRightRailAccountWeight())
    )
}

internal fun rightRailMinAccountWeightForContentDp(
    toolCount: Int,
    railHeightDp: Int
): Float {
    if (railHeightDp <= 0) return minRightRailAccountWeight()
    val toolContentHeight = rightRailListContentHeightDp(
        itemCount = toolCount,
        itemHeightDp = RailToolButtonHeightDp
    )
    return rightRailNormalizeAccountWeight(
        (1f - (toolContentHeight + RightRailExpansionSlackDp).toFloat() / railHeightDp)
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtMost(defaultRightRailAccountWeight())
    )
}

internal fun rightRailListContentHeightDp(
    itemCount: Int,
    itemHeightDp: Int
): Int {
    val safeCount = itemCount.coerceAtLeast(0)
    if (safeCount == 0) return 0
    return safeCount * itemHeightDp + (safeCount - 1) * RightRailItemGapDp
}

internal fun rightRailSelectedAccountFirstVisibleIndex(
    accounts: List<FloatingChatContact>,
    selectedAccountId: String?
): Int {
    return accounts.indexOfFirst { account -> account.id == selectedAccountId }
        .coerceAtLeast(0)
}

internal fun rightRailScrollsSelectedAccountIntoViewForConnectors(): Boolean = true

private fun rightRailNormalizeAccountWeight(weight: Float): Float {
    return (weight * 100f).roundToInt() / 100f
}

private const val RightRailWidthDp = 58
private const val RailScreenEdgeInsetPx = 8
private const val RailToolButtonWidthDp = 42
private const val RailAvatarSizeDp = 42
private const val RailToolButtonHeightDp = 42
private const val RightRailItemGapDp = 6
private const val RightRailExpansionSlackDp = 10
private const val RightRailMinimumVisibleToolCount = 3
private const val RightRailDefaultAccountWeight = 0.42f
private const val RightRailMinAccountWeight = 0.24f
private const val RightRailMaxAccountWeight = 0.70f
private const val RightRailSectionShiftFraction = 0.25f
private const val RightRailSectionResizeMs = 140
