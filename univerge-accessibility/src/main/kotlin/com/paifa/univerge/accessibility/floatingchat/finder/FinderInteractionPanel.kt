package com.paifa.univerge.accessibility.floatingchat.finder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FinderInteractionPanel(
    session: FinderSession,
    api: FinderApi,
    feedId: Long,
    nonceId: String,
    feedAuth: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(FinderOperationUiState()) }

    FinderPanelSection(modifier = modifier) {
        FinderPanelHeader(title = "视频号互动", subtitle = "互动请求必须使用已确认作品上下文，不猜测 nonceId/feedAuth。")
        FinderTextInput(comment, { comment = it }, "评论内容")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FinderActionButton(
                label = if (liked) "取消点赞" else "点赞",
                enabled = !state.loading,
                accent = !liked,
                onClick = {
                    scope.launch {
                        state = FinderOperationUiState(loading = true, status = "正在提交点赞任务")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                FinderTaskAwaiter(api).await {
                                    api.setLike(
                                        FinderLikeRequest(
                                            deviceUuid = session.deviceUuid,
                                            weChatId = session.weChatId,
                                            feedId = feedId,
                                            isCancel = liked
                                        )
                                    )
                                }
                            }
                        }.onSuccess { outcome ->
                            liked = !liked
                            state = FinderOperationUiState(status = outcome.message)
                        }.onFailure { error ->
                            state = FinderOperationUiState(error = error.toFinderUserMessage())
                        }
                    }
                }
            )
            FinderActionButton(
                label = "发表评论",
                enabled = !state.loading && comment.isNotBlank(),
                accent = true,
                onClick = {
                    scope.launch {
                        state = FinderOperationUiState(loading = true, status = "正在提交评论任务")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                FinderTaskAwaiter(api).await {
                                    api.createComment(
                                        FinderCommentRequest(
                                            deviceUuid = session.deviceUuid,
                                            weChatId = session.weChatId,
                                            feedId = feedId,
                                            nonceId = nonceId,
                                            feedAuth = feedAuth,
                                            content = comment.trim()
                                        )
                                    )
                                }
                            }
                        }.onSuccess { outcome ->
                            comment = ""
                            state = FinderOperationUiState(status = outcome.message)
                        }.onFailure { error ->
                            state = FinderOperationUiState(error = error.toFinderUserMessage())
                        }
                    }
                }
            )
        }
        FinderOperationStatus(state)
        TextLabel("feedId=$feedId", 9.sp, maxLines = 1)
    }
}
