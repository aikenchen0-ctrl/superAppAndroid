package com.paifa.ubikitouch.accessibility.floatingchat.finder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

private enum class FinderWorkspaceTab(val label: String) {
    Publish("发布"),
    UserPage("主页"),
    Interaction("互动"),
    Navigation("导航")
}

/** Hosts the four Finder workflows without issuing a request while the panel opens. */
@Composable
internal fun FinderWorkspaceView(
    session: FinderSession?,
    api: FinderApi?,
    initialSphUserName: String?,
    userPageRequestKey: Int,
    configurationError: String?,
    modifier: Modifier = Modifier
) {
    if (session == null || api == null) {
        FinderPanelSection(modifier = modifier) {
            FinderPanelHeader(title = "视频号", subtitle = "需要当前 SCRM 账号路由和 API 配置后才能操作。")
            FinderOperationStatus(FinderOperationUiState(error = configurationError ?: "当前账号不可用于视频号操作"))
        }
        return
    }

    var selectedTab by remember(userPageRequestKey) {
        mutableStateOf(
            if (userPageRequestKey > 0) FinderWorkspaceTab.UserPage else FinderWorkspaceTab.Publish
        )
    }
    var feedIdInput by remember { mutableStateOf("") }
    var nonceIdInput by remember { mutableStateOf("") }
    var feedAuthInput by remember { mutableStateOf("") }
    val selectedIndex = FinderWorkspaceTab.entries.indexOf(selectedTab)

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = OverlayTokens.panel,
            contentColor = OverlayTokens.accent
        ) {
            FinderWorkspaceTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            color = if (selectedIndex == index) OverlayTokens.panelPrimaryText else OverlayTokens.panelSecondaryText,
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }
        when (selectedTab) {
            FinderWorkspaceTab.Publish -> FinderPublishView(session = session, api = api)
            FinderWorkspaceTab.UserPage -> FinderUserPageView(
                session = session,
                api = api,
                initialSphUserName = initialSphUserName,
                requestKey = userPageRequestKey
            )
            FinderWorkspaceTab.Interaction -> {
                val feedId = feedIdInput.toLongOrNull()
                if (feedId != null && feedId > 0L && nonceIdInput.isNotBlank() && feedAuthInput.isNotBlank()) {
                    FinderInteractionPanel(
                        session = session,
                        api = api,
                        feedId = feedId,
                        nonceId = nonceIdInput.trim(),
                        feedAuth = feedAuthInput.trim()
                    )
                } else {
                    FinderInteractionContextInput(
                        feedId = feedIdInput,
                        onFeedIdChanged = { feedIdInput = it },
                        nonceId = nonceIdInput,
                        onNonceIdChanged = { nonceIdInput = it },
                        feedAuth = feedAuthInput,
                        onFeedAuthChanged = { feedAuthInput = it }
                    )
                }
            }
            FinderWorkspaceTab.Navigation -> FinderNavigationView(session = session, api = api)
        }
    }
}

@Composable
private fun FinderInteractionContextInput(
    feedId: String,
    onFeedIdChanged: (String) -> Unit,
    nonceId: String,
    onNonceIdChanged: (String) -> Unit,
    feedAuth: String,
    onFeedAuthChanged: (String) -> Unit
) {
    FinderPanelSection {
        FinderPanelHeader(
            title = "视频号互动",
            subtitle = "仅使用已确认作品的 feedId、nonceId 和 feedAuth，不从消息标题推测。"
        )
        FinderTextInput(feedId, onFeedIdChanged, "feedId")
        FinderTextInput(nonceId, onNonceIdChanged, "nonceId")
        FinderTextInput(feedAuth, onFeedAuthChanged, "feedAuth")
        FinderOperationStatus(
            FinderOperationUiState(status = "填写完整作品上下文后可进行点赞或评论。")
        )
    }
}
