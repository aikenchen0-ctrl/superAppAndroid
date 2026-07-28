package com.paifa.ubikitouch.accessibility.floatingchat.chat

internal enum class UnrepliedDraftMode {
    Inline,
    Bottom
}

internal data class UnrepliedDraftKey(
    val accountId: String,
    val threadId: String
)

internal data class UnrepliedRecipientIndicators(
    val watermarkVisible: Boolean = true,
    val colorDotVisible: Boolean = true
)

internal data class UnrepliedOverviewState(
    val visible: Boolean = false,
    val accountFilterId: String? = null,
    val selectedItemId: String? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val draftMode: UnrepliedDraftMode = UnrepliedDraftMode.Bottom,
    val drafts: Map<UnrepliedDraftKey, String> = emptyMap(),
    val indicators: UnrepliedRecipientIndicators = UnrepliedRecipientIndicators()
)

internal fun restoreUnrepliedOverviewState(
    saved: UnrepliedOverviewState,
    availableAccountIds: Set<String>,
    availableItemIds: List<String>
): UnrepliedOverviewState {
    if (availableItemIds.isEmpty()) {
        return saved.copy(
            visible = true,
            accountFilterId = saved.accountFilterId?.takeIf(availableAccountIds::contains),
            selectedItemId = null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0
        )
    }

    return saved.copy(
        visible = true,
        accountFilterId = saved.accountFilterId?.takeIf(availableAccountIds::contains),
        selectedItemId = saved.selectedItemId?.takeIf(availableItemIds::contains),
        firstVisibleItemIndex = saved.firstVisibleItemIndex.coerceIn(0, availableItemIds.lastIndex)
    )
}
