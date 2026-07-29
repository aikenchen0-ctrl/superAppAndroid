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

internal fun openAllAccountsUnrepliedOverview(
    saved: UnrepliedOverviewState,
    availableAccountIds: Set<String>,
    availableItemIds: List<String>
): UnrepliedOverviewState {
    return restoreUnrepliedOverviewState(
        saved = saved,
        availableAccountIds = availableAccountIds,
        availableItemIds = availableItemIds
    ).copy(accountFilterId = null)
}

internal fun filterHomeUnreadSummaries(
    summaries: List<HomeUnreadThreadSummary>,
    accountFilterId: String?
): List<HomeUnreadThreadSummary> {
    return accountFilterId?.let { id ->
        summaries.filter { summary -> summary.accountId == id }
    } ?: summaries
}

internal fun shouldRenderChatConnectorLayer(homeOverviewVisible: Boolean): Boolean = !homeOverviewVisible

internal fun shouldRenderRecipientWatermark(indicators: UnrepliedRecipientIndicators): Boolean {
    return indicators.watermarkVisible
}

internal fun shouldRenderRecipientColorDot(indicators: UnrepliedRecipientIndicators): Boolean {
    return indicators.colorDotVisible
}

internal fun UnrepliedOverviewState.updateDraft(
    key: UnrepliedDraftKey,
    text: String
): UnrepliedOverviewState {
    val updatedDrafts = if (text.isEmpty()) {
        drafts - key
    } else {
        drafts + (key to text)
    }
    return copy(drafts = updatedDrafts)
}

internal fun UnrepliedOverviewState.afterDraftSend(
    key: UnrepliedDraftKey,
    succeeded: Boolean
): UnrepliedOverviewState {
    return if (succeeded) copy(drafts = drafts - key) else this
}

internal fun unrepliedOverviewEmptyText(items: List<*>): String? {
    return if (items.isEmpty()) "所有消息均已回复" else null
}
