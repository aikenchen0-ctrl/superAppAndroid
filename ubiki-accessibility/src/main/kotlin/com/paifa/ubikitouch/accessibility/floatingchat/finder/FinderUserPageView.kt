package com.paifa.ubikitouch.accessibility.floatingchat.finder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FinderUserPageView(
    session: FinderSession,
    api: FinderApi,
    initialSphUserName: String? = null,
    requestKey: Int = 0,
    modifier: Modifier = Modifier,
    onOpenInteraction: (feedId: Long, nonceId: String, feedAuth: String) -> Unit = { _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var userName by remember(initialSphUserName, requestKey) { mutableStateOf(initialSphUserName.orEmpty()) }
    var state by remember { mutableStateOf(FinderOperationUiState()) }

    FinderPanelSection(modifier = modifier) {
        FinderPanelHeader(
            title = "视频号用户主页",
            subtitle = "点击视频号消息时传入 sphUserName，可留空刷新当前账号身份。"
        )
        FinderTextInput(userName, { userName = it }, "视频号 username，可选")
        FinderActionButton(
            label = "拉取用户主页",
            enabled = !state.loading,
            accent = true,
            onClick = {
                scope.launch {
                    state = FinderOperationUiState(loading = true, status = "正在拉取视频号用户主页")
                    runCatching {
                        withContext(Dispatchers.IO) {
                            FinderTaskAwaiter(api).await {
                                api.loadUserPage(
                                    FinderUserPageRequest(
                                        deviceUuid = session.deviceUuid,
                                        weChatId = session.weChatId,
                                        sphUserName = userName.trim().takeIf(String::isNotEmpty)
                                    )
                                )
                            }
                        }
                    }.onSuccess { outcome ->
                        state = FinderOperationUiState(status = outcome.message)
                    }.onFailure { error ->
                        state = FinderOperationUiState(error = error.toFinderUserMessage())
                    }
                }
            }
        )
        FinderOperationStatus(state)
        TextLabel(
            text = "主页结果由 Android 任务终态返回；结果未知时不会自动重试。",
            size = 9.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
