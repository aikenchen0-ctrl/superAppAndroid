package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

internal enum class UnrepliedHeaderLeadingAction {
    None,
    OpenOverview,
    BackToAllAccounts
}

internal fun unrepliedHeaderLeadingAction(
    overviewVisible: Boolean,
    accountFilterId: String?
): UnrepliedHeaderLeadingAction = when {
    !overviewVisible -> UnrepliedHeaderLeadingAction.OpenOverview
    accountFilterId != null -> UnrepliedHeaderLeadingAction.BackToAllAccounts
    else -> UnrepliedHeaderLeadingAction.None
}

internal fun unrepliedHeaderTitle(
    overviewVisible: Boolean,
    accountFilterName: String?,
    conversationTitle: String,
    itemCount: Int
): String {
    if (!overviewVisible) return conversationTitle
    return "${accountFilterName ?: "全部账号"} · $itemCount 项"
}

@Composable
internal fun UnrepliedOverviewButton(
    overviewState: UnrepliedOverviewState,
    onOpenOverview: () -> Unit,
    onBackToAllAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (
        unrepliedHeaderLeadingAction(
            overviewVisible = overviewState.visible,
            accountFilterId = overviewState.accountFilterId
        )
    ) {
        UnrepliedHeaderLeadingAction.OpenOverview -> TextButton(
            onClick = onOpenOverview,
            modifier = modifier
        ) {
            Text(text = "\u672a\u56de\u6d88\u606f", fontSize = 12.sp, maxLines = 1)
        }
        UnrepliedHeaderLeadingAction.BackToAllAccounts -> TextButton(
            onClick = onBackToAllAccounts,
            modifier = modifier
        ) {
            Text(text = "< \u672a\u56de\u6d88\u606f", fontSize = 12.sp, maxLines = 1)
        }
        UnrepliedHeaderLeadingAction.None -> Unit
    }
}
