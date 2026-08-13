package com.paifa.ubikitouch.accessibility.floatingchat.finder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FinderNavigationView(
    session: FinderSession,
    api: FinderApi,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(FinderOperationUiState()) }

    FinderPanelSection(modifier = modifier) {
        FinderPanelHeader(title = "视频号导航", subtitle = "进入首页或提交微信原生搜索。")
        FinderTextInput(keyword, { keyword = it }, "搜索关键词（1-100 个字符）")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FinderActionButton(
                label = "视频号首页",
                enabled = !state.loading,
                accent = true,
                onClick = {
                    scope.launch {
                        state = FinderOperationUiState(loading = true, status = "正在进入视频号首页")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                FinderTaskAwaiter(api).await {
                                    api.navigate(
                                        FinderNavigationRequest(
                                            deviceUuid = session.deviceUuid,
                                            weChatId = session.weChatId,
                                            action = FinderNavigationAction.Home
                                        )
                                    )
                                }
                            }
                        }.onSuccess { outcome -> state = FinderOperationUiState(status = outcome.message) }
                            .onFailure { error -> state = FinderOperationUiState(error = error.toFinderUserMessage()) }
                    }
                }
            )
            FinderActionButton(
                label = "搜索",
                enabled = !state.loading && keyword.trim().isNotEmpty(),
                onClick = {
                    scope.launch {
                        state = FinderOperationUiState(loading = true, status = "正在提交视频号搜索")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                FinderTaskAwaiter(api).await {
                                    FinderNavigationRequest(
                                        deviceUuid = session.deviceUuid,
                                        weChatId = session.weChatId,
                                        action = FinderNavigationAction.Search,
                                        keyword = keyword
                                    ).let(api::navigate)
                                }
                            }
                        }.onSuccess { outcome -> state = FinderOperationUiState(status = outcome.message) }
                            .onFailure { error -> state = FinderOperationUiState(error = error.toFinderUserMessage()) }
                    }
                }
            )
        }
        FinderOperationStatus(state)
    }
}
