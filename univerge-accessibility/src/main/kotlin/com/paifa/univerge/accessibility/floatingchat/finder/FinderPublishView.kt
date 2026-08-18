package com.paifa.univerge.accessibility.floatingchat.finder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Publish workflow: validate the server template first, then submit the exact validated payload. */
@Composable
internal fun FinderPublishView(
    session: FinderSession,
    api: FinderApi,
    modifier: Modifier = Modifier,
    onPublished: (FinderTaskOutcome) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf("") }
    var mediaUrls by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf(FinderMediaType.Video) }
    var validatedPost by remember { mutableStateOf<FinderPostRequest?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(FinderOperationUiState()) }

    fun invalidateTemplate() {
        validatedPost = null
        confirmed = false
    }

    fun buildTemplateRequest(): FinderPostTemplateRequest {
        return FinderPostTemplateRequest(
            deviceUuid = session.deviceUuid,
            weChatId = session.weChatId,
            content = finderComposeContent(content, topics),
            medias = finderParseMediaUrls(mediaUrls),
            mediaType = mediaType.code,
            cover = cover.trim().takeIf(String::isNotEmpty),
            includePostRequest = true
        )
    }

    FinderPanelSection(modifier = modifier) {
        FinderPanelHeader(
            title = "视频号发布",
            subtitle = "先校验发布模板；媒体 URL 必须由手机通过 HTTP(S) 访问。"
        )
        FinderTextInput(content, { content = it; invalidateTemplate() }, "文案", singleLine = false)
        FinderTextInput(topics, { topics = it; invalidateTemplate() }, "话题，使用逗号分隔")
        FinderTextInput(mediaUrls, { mediaUrls = it; invalidateTemplate() }, "媒体 URL，每行一条", singleLine = false)
        FinderTextInput(cover, { cover = it; invalidateTemplate() }, "封面 URL，可选")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FinderActionButton(
                label = "视频",
                enabled = !state.loading,
                accent = mediaType == FinderMediaType.Video,
                onClick = { mediaType = FinderMediaType.Video; invalidateTemplate() }
            )
            FinderActionButton(
                label = "图片",
                enabled = !state.loading,
                accent = mediaType == FinderMediaType.Image,
                onClick = { mediaType = FinderMediaType.Image; invalidateTemplate() }
            )
        }
        FinderInlineActions {
            FinderActionButton(
                label = "校验模板",
                enabled = !state.loading,
                accent = true,
                onClick = {
                    scope.launch {
                        state = FinderOperationUiState(loading = true, status = "正在校验发布模板")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val request = buildTemplateRequest()
                                val response = api.buildPostTemplate(request)
                                validatedFinderPostRequest(response)
                            }
                        }.onSuccess { post ->
                            validatedPost = post
                            state = FinderOperationUiState(status = "模板已通过校验，请确认后发布")
                        }.onFailure { error ->
                            validatedPost = null
                            state = FinderOperationUiState(error = error.toFinderUserMessage())
                        }
                    }
                }
            )
            TextLabel(
                text = if (validatedPost == null) "未校验" else "已校验",
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = validatedPost != null && !state.loading)
            TextLabel("我确认提交视频号发布任务", 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        }
        FinderActionButton(
            label = "确认发布",
            enabled = validatedPost != null && confirmed && !state.loading,
            danger = true,
            onClick = {
                val request = checkNotNull(validatedPost)
                scope.launch {
                    state = FinderOperationUiState(loading = true, status = "正在提交并等待发布任务终态")
                    runCatching {
                        withContext(Dispatchers.IO) {
                            FinderTaskAwaiter(api).await { api.publishPost(request) }
                        }
                    }.onSuccess { outcome ->
                        state = FinderOperationUiState(status = outcome.message)
                        onPublished(outcome)
                    }.onFailure { error ->
                        state = FinderOperationUiState(error = error.toFinderUserMessage())
                    }
                }
            }
        )
        FinderOperationStatus(state)
    }
}
