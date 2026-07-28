package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

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
internal fun UnrepliedOverviewHeader(
    overviewState: UnrepliedOverviewState,
    accountFilterName: String?,
    conversationTitle: String,
    itemCount: Int,
    onOpenOverview: () -> Unit,
    onBackToAllAccounts: () -> Unit,
    onDraftModeChanged: (UnrepliedDraftMode) -> Unit,
    onIndicatorsChanged: (UnrepliedRecipientIndicators) -> Unit,
    modifier: Modifier = Modifier
) {
    var configExpanded by remember { mutableStateOf(false) }
    val leadingAction = unrepliedHeaderLeadingAction(
        overviewVisible = overviewState.visible,
        accountFilterId = overviewState.accountFilterId
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayTokens.control.copy(alpha = 0.94f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (leadingAction) {
            UnrepliedHeaderLeadingAction.OpenOverview -> HeaderTextButton("未回消息", onOpenOverview)
            UnrepliedHeaderLeadingAction.BackToAllAccounts -> HeaderTextButton("< 未回消息", onBackToAllAccounts)
            UnrepliedHeaderLeadingAction.None -> Unit
        }
        Text(
            text = unrepliedHeaderTitle(
                overviewVisible = overviewState.visible,
                accountFilterName = accountFilterName,
                conversationTitle = conversationTitle,
                itemCount = itemCount
            ),
            modifier = Modifier.weight(1f),
            color = OverlayTokens.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        if (overviewState.visible) {
            HeaderModeButton(
                text = "列表草稿",
                selected = overviewState.draftMode == UnrepliedDraftMode.Inline,
                onClick = { onDraftModeChanged(UnrepliedDraftMode.Inline) }
            )
            HeaderModeButton(
                text = "底部草稿",
                selected = overviewState.draftMode == UnrepliedDraftMode.Bottom,
                onClick = { onDraftModeChanged(UnrepliedDraftMode.Bottom) }
            )
            IconButton(
                onClick = { configExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "收件账号标识配置",
                    tint = OverlayTokens.primaryText
                )
            }
            DropdownMenu(
                expanded = configExpanded,
                onDismissRequest = { configExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("头像水印") },
                    leadingIcon = {
                        Checkbox(
                            checked = overviewState.indicators.watermarkVisible,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        onIndicatorsChanged(
                            overviewState.indicators.copy(
                                watermarkVisible = !overviewState.indicators.watermarkVisible
                            )
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("账号色点") },
                    leadingIcon = {
                        Checkbox(
                            checked = overviewState.indicators.colorDotVisible,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        onIndicatorsChanged(
                            overviewState.indicators.copy(
                                colorDotVisible = !overviewState.indicators.colorDotVisible
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun HeaderModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = if (selected) OverlayTokens.accent else OverlayTokens.secondaryText,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
