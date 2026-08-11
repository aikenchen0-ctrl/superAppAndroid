package com.paifa.ubikitouch.accessibility.floatingchat.moments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.AppMomentComment
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.AppMomentPost
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedMediaEvent
import com.paifa.ubikitouch.accessibility.MomentMediaKind
import com.paifa.ubikitouch.accessibility.ScrmMomentMaterialsPanelState
import com.paifa.ubikitouch.accessibility.ScrmMomentsPanelState
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.archiveScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.commentScrmMoment
import com.paifa.ubikitouch.accessibility.copyScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.createScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.ScrmPanelButton
import com.paifa.ubikitouch.accessibility.floatingchat.media.loadImageThumbnailBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.media.loadVideoPreviewBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.tools.PanelTextInput
import com.paifa.ubikitouch.accessibility.floatingchat.tools.SmallChoiceButton
import com.paifa.ubikitouch.accessibility.likeScrmMoment
import com.paifa.ubikitouch.accessibility.loadScrmMomentMaterialDetail
import com.paifa.ubikitouch.accessibility.loadScrmMomentMaterials
import com.paifa.ubikitouch.accessibility.loadScrmMoments
import com.paifa.ubikitouch.accessibility.localScrmMomentPostForSubmittedDraft
import com.paifa.ubikitouch.accessibility.publishScrmMoment
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.scrm.scrmReadableText
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialDetail
import com.paifa.ubikitouch.accessibility.scrm.toScrmContactsPanelMessage
import com.paifa.ubikitouch.accessibility.scrmMomentPostsFromTaskData
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MomentsTimelinePanel(
    route: ScrmFloatingAccountRoute?,
    posts: List<AppMomentPost>,
    pendingMedia: AppMomentMedia?,
    onPickMedia: () -> Unit,
    onClearMedia: () -> Unit,
    onPreviewMedia: (AppMomentPost) -> Unit,
    onUpdatePost: (AppMomentPost) -> Unit,
    onRemotePostsLoaded: (List<AppMomentPost>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var commentingPostId by remember { mutableStateOf<String?>(null) }
    var activeMomentMenuPostId by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var showAdvancedTools by remember { mutableStateOf(false) }
    var advancedToolStatus by remember { mutableStateOf<String?>(null) }
    var state by remember(route) { mutableStateOf(ScrmMomentsPanelState()) }

    fun loadMoments() {
        val currentRoute = route
        if (currentRoute == null) {
            state = state.copy(
                loading = false,
                status = null,
                error = "当前账号缺少 SCRM 路由，无法同步真实朋友圈"
            )
            return
        }
        scope.launch {
            state = state.copy(loading = true, status = "正在同步真实朋友圈", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    loadScrmMoments(context.applicationContext, currentRoute)
                }
            }.onSuccess { result ->
                if (result.posts.isNotEmpty()) {
                    onRemotePostsLoaded(result.posts)
                }
                state = state.copy(loading = false, status = result.message, error = null)
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun submitMoment(content: String) {
        val currentRoute = route
        if (currentRoute == null) {
            state = state.copy(error = "当前账号缺少 SCRM 路由，无法发表朋友圈")
            return
        }
        val media = pendingMedia
        val trimmedContent = content.trim()
        val clientRequestId = "moment-${System.currentTimeMillis()}"
        scope.launch {
            state = state.copy(loading = true, status = "正在发表朋友圈", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    publishScrmMoment(
                        context = context.applicationContext,
                        route = currentRoute,
                        content = trimmedContent,
                        media = media,
                        clientRequestId = clientRequestId
                    )
                }
            }.onSuccess { outcome ->
                state = state.copy(loading = false, status = outcome.message, error = null)
                val submittedPosts = outcome.data
                    .flatMap { data -> scrmMomentPostsFromTaskData(data) }
                    .distinctBy { post -> post.id }
                if (submittedPosts.isNotEmpty()) {
                    onRemotePostsLoaded(submittedPosts)
                } else {
                    onUpdatePost(
                        localScrmMomentPostForSubmittedDraft(
                            clientRequestId = clientRequestId,
                            weChatId = currentRoute.weChatId,
                            content = trimmedContent,
                            media = media
                        )
                    )
                }
                draft = ""
                onClearMedia()
                if (outcome.completed) {
                    loadMoments()
                }
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun submitLike(post: AppMomentPost) {
        val currentRoute = route
        val circleId = scrmCircleIdForMomentPostId(post.id)
        if (currentRoute == null || circleId == null) {
            state = state.copy(error = "当前朋友圈缺少 circleId，无法点赞")
            return
        }
        val cancel = post.likedBy.contains(CurrentUserMomentLikeName)
        scope.launch {
            state = state.copy(loading = true, status = "正在提交朋友圈点赞", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    likeScrmMoment(
                        context = context.applicationContext,
                        route = currentRoute,
                        circleId = circleId,
                        cancel = cancel
                    )
                }
            }.onSuccess { outcome ->
                state = state.copy(loading = false, status = outcome.message, error = null)
                if (outcome.completed) {
                    val nextLikedBy = if (cancel) {
                        post.likedBy - CurrentUserMomentLikeName
                    } else {
                        (post.likedBy + CurrentUserMomentLikeName).distinct()
                    }
                    onUpdatePost(post.copy(likedBy = nextLikedBy))
                }
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun submitComment(post: AppMomentPost, text: String) {
        val currentRoute = route
        val circleId = scrmCircleIdForMomentPostId(post.id)
        if (currentRoute == null || circleId == null) {
            state = state.copy(error = "当前朋友圈缺少 circleId，无法评论")
            return
        }
        scope.launch {
            state = state.copy(loading = true, status = "正在提交朋友圈评论", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    commentScrmMoment(
                        context = context.applicationContext,
                        route = currentRoute,
                        circleId = circleId,
                        text = text
                    )
                }
            }.onSuccess { outcome ->
                state = state.copy(loading = false, status = outcome.message, error = null)
                if (outcome.completed) {
                    onUpdatePost(
                        post.copy(
                            comments = post.comments + AppMomentComment(CurrentUserMomentLikeName, text)
                        )
                    )
                    commentDraft = ""
                    commentingPostId = null
                }
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    LaunchedEffect(route) {
        loadMoments()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLabel(
                text = "朋友圈",
                size = 12.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            SmallChoiceButton(label = "工具", onClick = { showAdvancedTools = !showAdvancedTools })
            Spacer(modifier = Modifier.width(6.dp))
            SmallChoiceButton(label = "刷新", onClick = ::loadMoments)
            Spacer(modifier = Modifier.width(6.dp))
            SmallChoiceButton(label = "图片/视频", onClick = onPickMedia)
            Spacer(modifier = Modifier.width(6.dp))
            SmallChoiceButton(
                label = "发表",
                onClick = {
                    val content = draft.trim()
                    if (content.isNotEmpty() || pendingMedia != null) {
                        submitMoment(content)
                        draft = ""
                    }
                }
            )
        }
        if (showAdvancedTools) {
            MomentAdvancedToolsMenu(
                status = advancedToolStatus,
                onSelect = { label ->
                    advancedToolStatus = "$label：已打开 UI 预览，接口接入后再执行"
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        state.status?.let { status ->
            TextLabel(
                text = status,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        state.error?.let { error ->
            TextLabel(
                text = error,
                size = 10.sp,
                color = Color(0xFFB65757),
                maxLines = 2,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            maxLines = 3,
            textStyle = TextStyle.Default.copy(
                color = OverlayTokens.panelPrimaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp, max = 64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OverlayTokens.momentsComposer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    if (draft.isBlank()) {
                        TextLabel(
                            text = "这一刻的想法...",
                            size = 9.sp,
                            color = OverlayTokens.panelSecondaryText,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
        pendingMedia?.let { media ->
            MomentPendingMediaPreview(media = media, onClear = onClearMedia)
            Spacer(modifier = Modifier.height(6.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 410.dp)
                .padding(top = 6.dp)
                .background(OverlayTokens.momentsBackground)
        ) {
            itemsIndexed(posts) { _, post ->
                val liked = post.likedBy.contains(CurrentUserMomentLikeName)
                val comments = post.comments
                MomentPostRow(
                    post = post,
                    liked = liked,
                    comments = comments,
                    menuOpen = activeMomentMenuPostId == post.id,
                    commenting = commentingPostId == post.id,
                    commentDraft = if (commentingPostId == post.id) commentDraft else "",
                    onToggleMenu = {
                        activeMomentMenuPostId = if (activeMomentMenuPostId == post.id) null else post.id
                    },
                    onLike = {
                        submitLike(post)
                        activeMomentMenuPostId = null
                    },
                    onComment = {
                        commentingPostId = if (commentingPostId == post.id) null else post.id
                        commentDraft = ""
                        activeMomentMenuPostId = null
                    },
                    onCommentChange = { next -> commentDraft = next },
                    onPreviewMedia = { onPreviewMedia(post) },
                    onSendComment = {
                        val text = commentDraft.trim()
                        if (text.isNotEmpty()) {
                            submitComment(post, text)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MomentAdvancedToolsMenu(status: String?, onSelect: (String) -> Unit) {
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    val tools = remember {
        listOf(
            "互动消息：未读与已读",
            "可见范围与置顶",
            "删除动态与好友刷新",
            "批量发布计划",
            "从素材复制发布"
        )
    }
    MaterialSurface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7FAFB),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OverlayTokens.panelBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(text = "朋友圈工具", size = 11.sp, weight = FontWeight.SemiBold, color = OverlayTokens.panelPrimaryText, maxLines = 1)
            tools.forEach { label ->
                TextButton(
                    onClick = { selectedTool = label; confirmed = false },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(label, modifier = Modifier.weight(1f), color = OverlayTokens.panelPrimaryText, fontSize = 12.sp)
                    Text("预览", color = Color(0xFFB26A00), fontSize = 10.sp)
                }
            }
            selectedTool?.let { tool ->
                if (tool == "互动消息：未读与已读") {
                    MomentInteractionPreviewEditor(
                        confirmed = confirmed,
                        onConfirmedChange = { confirmed = it },
                        onGeneratePreview = { onSelect(tool) }
                    )
                } else if (tool == "可见范围与置顶") {
                    MomentVisibilityPreviewEditor(
                        confirmed = confirmed,
                        onConfirmedChange = { confirmed = it },
                        onGeneratePreview = { onSelect(tool) }
                    )
                } else if (tool == "删除动态与好友刷新") {
                    MomentCleanupPreviewEditor(
                        confirmed = confirmed,
                        onConfirmedChange = { confirmed = it },
                        onGeneratePreview = { onSelect(tool) }
                    )
                } else if (tool == "从素材复制发布") {
                    MomentMaterialPublishPreviewEditor(
                        confirmed = confirmed,
                        onConfirmedChange = { confirmed = it },
                        onGeneratePreview = { onSelect(tool) }
                    )
                } else if (tool == "批量发布计划") {
                    MomentBatchPublishPlanEditor(
                        confirmed = confirmed,
                        onConfirmedChange = { confirmed = it },
                        onGeneratePreview = { onSelect(tool) }
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(6.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("$tool", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("当前仅生成 UI 预览，后续接口接入后会显示实际影响范围与任务结果。", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                            Text("我已确认当前操作范围", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                enabled = confirmed,
                                onClick = { onSelect(tool) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                            ) { Text("生成预览", fontSize = 10.sp) }
                        }
                    }
                }
            }
            status?.let { TextLabel(it, 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 2) }
        }
    }
}

@Composable
private fun MomentMaterialPublishPreviewEditor(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit
) {
    var materialId by remember { mutableStateOf("") }
    var contentSummary by remember { mutableStateOf("") }
    var targetSummary by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf("") }
    val preview = MomentMaterialPublishPreviewDraft(
        materialId = materialId,
        contentSummary = contentSummary,
        targetSummary = targetSummary,
        scheduledAt = scheduledAt
    ).toPreview()
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("从素材复制发布预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("素材详情和实际发布结果需由接口回读，当前不创建副本。", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        PanelTextInput(materialId, {
            materialId = it
            onConfirmedChange(false)
        }, "素材 ID")
        PanelTextInput(contentSummary, {
            contentSummary = it
            onConfirmedChange(false)
        }, "发布文案摘要，可留空使用原文")
        PanelTextInput(targetSummary, {
            targetSummary = it
            onConfirmedChange(false)
        }, "目标账号或范围")
        PanelTextInput(scheduledAt, {
            scheduledAt = it
            onConfirmedChange(false)
        }, "计划时间，可留空")
        Text(preview.materialSummary, color = OverlayTokens.panelPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text("文案：${preview.contentSummary}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 2)
        Text("范围：${preview.targetSummary} · 时间：${preview.scheduledAt}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 2)
        Text(preview.validationMessage, color = Color(0xFF9A5B00), fontSize = 10.sp)
        Text(preview.executionStatement, color = Color(0xFF9A5B00), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = onConfirmedChange)
            Text("我已确认素材、目标范围和计划时间", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = preview.canGeneratePreview && confirmed,
                onClick = onGeneratePreview,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("生成预览", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun MomentCleanupPreviewEditor(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit
) {
    var action by remember { mutableStateOf(MomentCleanupAction.DeleteMoment) }
    var momentId by remember { mutableStateOf("") }
    var targetSummary by remember { mutableStateOf("") }
    var affectedCount by remember { mutableStateOf("") }
    var confirmationText by remember { mutableStateOf("") }
    val preview = MomentCleanupPreviewDraft(
        action = action,
        momentId = momentId,
        targetSummary = targetSummary,
        affectedCount = affectedCount.toIntOrNull(),
        confirmationText = confirmationText
    ).toPreview()
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF4F1), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("删除动态与好友刷新预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("删除不可撤销，当前仅生成预览，不会修改朋友圈数据。", color = Color(0xFF9A3E20), fontSize = 10.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MomentCleanupAction.values().forEach { option ->
                TextButton(
                    onClick = {
                        action = option
                        onConfirmedChange(false)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (action == option) Color(0xFFF8D8CE) else Color.Transparent
                    )
                ) { Text(option.label, fontSize = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1) }
            }
        }
        PanelTextInput(targetSummary, {
            targetSummary = it
            onConfirmedChange(false)
        }, "目标账号或范围")
        PanelTextInput(affectedCount, {
            affectedCount = it.filter(Char::isDigit)
            onConfirmedChange(false)
        }, if (action == MomentCleanupAction.DeleteMoment) "影响动态数，通常为 1" else "预计刷新好友数，可留空")
        if (action == MomentCleanupAction.DeleteMoment) {
            PanelTextInput(momentId, {
                momentId = it
                onConfirmedChange(false)
            }, "要删除的朋友圈动态 ID")
            PanelTextInput(confirmationText, {
                confirmationText = it
                onConfirmedChange(false)
            }, "请输入“删除动态”")
        }
        Text("${preview.actionLabel} · ${preview.targetItemSummary}", color = OverlayTokens.panelPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text("范围：${preview.targetSummary} · ${preview.impactSummary}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 2)
        Text(preview.confirmationHint, color = Color(0xFF9A3E20), fontSize = 10.sp)
        Text(preview.executionStatement, color = Color(0xFF9A3E20), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = onConfirmedChange)
            Text("我已确认当前账号、目标范围与影响数量", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = preview.canGeneratePreview && confirmed,
                onClick = onGeneratePreview,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB44727))
            ) { Text("生成预览", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun MomentVisibilityPreviewEditor(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit
) {
    var visibility by remember { mutableStateOf(MomentVisibilityScope.AllFriends) }
    var targetSummary by remember { mutableStateOf("") }
    var affectedCount by remember { mutableStateOf("") }
    var sticky by remember { mutableStateOf(false) }
    val preview = MomentVisibilityPreviewDraft(
        visibility = visibility,
        targetSummary = targetSummary,
        affectedAccountCount = affectedCount.toIntOrNull(),
        sticky = sticky
    ).toPreview()
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("可见范围与置顶预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("仅核对范围与置顶意图，接口接入后才会变更朋友圈状态。", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        MomentVisibilityScope.values().forEach { option ->
            TextButton(
                onClick = {
                    visibility = option
                    onConfirmedChange(false)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (visibility == option) OverlayTokens.accent.copy(alpha = 0.15f) else Color.Transparent
                )
            ) { Text(option.label, modifier = Modifier.fillMaxWidth(), fontSize = 10.sp, color = OverlayTokens.panelPrimaryText) }
        }
        PanelTextInput(targetSummary, {
            targetSummary = it
            onConfirmedChange(false)
        }, "目标账号、标签或朋友范围")
        PanelTextInput(affectedCount, {
            affectedCount = it.filter(Char::isDigit)
            onConfirmedChange(false)
        }, "预计影响账号数，可留空")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = sticky,
                onCheckedChange = {
                    sticky = it
                    onConfirmedChange(false)
                }
            )
            Text("置顶到当前账号的朋友圈列表", color = OverlayTokens.panelPrimaryText, fontSize = 10.sp)
        }
        Text("${preview.visibilityLabel} · ${preview.stickyLabel}", color = OverlayTokens.panelPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text("范围：${preview.targetSummary}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 1)
        Text(preview.affectedSummary, color = Color(0xFF9A5B00), fontSize = 10.sp, maxLines = 1)
        Text(preview.executionStatement, color = Color(0xFF9A5B00), fontSize = 10.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = onConfirmedChange)
            Text("我已确认当前账号、可见范围和置顶影响", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = confirmed,
                onClick = onGeneratePreview,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("生成预览", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun MomentInteractionPreviewEditor(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit
) {
    var action by remember { mutableStateOf(MomentInteractionAction.FetchUnread) }
    var targetSummary by remember { mutableStateOf("") }
    var unreadCount by remember { mutableStateOf("") }
    val preview = MomentInteractionPreviewDraft(
        action = action,
        targetSummary = targetSummary,
        unreadCount = unreadCount.toIntOrNull()
    ).toPreview()
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("互动消息预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("未读数由接口读取，当前输入仅用于核对影响范围。", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MomentInteractionAction.values().forEach { option ->
                TextButton(
                    onClick = {
                        action = option
                        onConfirmedChange(false)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (action == option) OverlayTokens.accent.copy(alpha = 0.15f) else Color.Transparent
                    )
                ) { Text(option.label, fontSize = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1) }
            }
        }
        PanelTextInput(targetSummary, { targetSummary = it }, "目标账号或范围")
        PanelTextInput(unreadCount, { unreadCount = it.filter(Char::isDigit) }, "未读互动数量，仅用于预览")
        Text("${preview.actionLabel} · ${preview.unreadSummary}", color = OverlayTokens.panelPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text("范围：${preview.targetSummary}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 1)
        Text(preview.impactSummary, color = Color(0xFF9A5B00), fontSize = 10.sp, maxLines = 2)
        Text(preview.executionStatement, color = Color(0xFF9A5B00), fontSize = 10.sp, maxLines = 1)
        if (preview.requiresConfirmation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmed, onCheckedChange = onConfirmedChange)
                Text("我已确认当前账号、范围和未读影响数量", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = !preview.requiresConfirmation || confirmed,
                onClick = onGeneratePreview,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("生成预览", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun MomentBatchPublishPlanEditor(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit
) {
    var planName by remember { mutableStateOf("") }
    var contentSummary by remember { mutableStateOf("") }
    var targetSummary by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(MomentBatchPlanStatus.Draft) }
    var estimatedImpact by remember { mutableStateOf("") }
    val preview = MomentBatchPublishPlanDraft(
        planName = planName,
        contentSummary = contentSummary,
        targetSummary = targetSummary,
        scheduledAt = scheduledAt,
        status = status,
        estimatedImpactCount = estimatedImpact.trim().toIntOrNull()
    ).toPreview()
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("批量发布计划预览", color = OverlayTokens.panelPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("先整理草稿，接口完善后再接入创建与执行。", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        PanelTextInput(planName, { planName = it }, "计划名称")
        PanelTextInput(contentSummary, { contentSummary = it }, "素材 / 文案摘要")
        PanelTextInput(targetSummary, { targetSummary = it }, "目标账号或范围")
        PanelTextInput(scheduledAt, { scheduledAt = it }, "计划时间，例如 2026-08-12 10:00")
        PanelTextInput(estimatedImpact, { estimatedImpact = it.filter(Char::isDigit) }, "预计影响账号数，可留空")
        Text("计划状态", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MomentBatchPlanStatus.values().forEach { option ->
                TextButton(
                    onClick = { status = option },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (status == option) OverlayTokens.accent.copy(alpha = 0.15f) else Color.Transparent
                    )
                ) { Text(option.shortLabel, fontSize = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1) }
            }
        }
        Text("${preview.planName} · ${preview.statusLabel}", color = OverlayTokens.panelPrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text("内容：${preview.contentSummary}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 2)
        Text("范围：${preview.targetSummary} · 时间：${preview.scheduledAt}", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp, maxLines = 2)
        Text("${preview.estimatedImpact}。${preview.executionStatement}", color = Color(0xFF9A5B00), fontSize = 10.sp, maxLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = onConfirmedChange)
            Text("我已确认当前账号、目标范围和影响数量", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                enabled = confirmed,
                onClick = onGeneratePreview,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("生成预览", fontSize = 10.sp) }
        }
    }
}

private const val CurrentUserMomentLikeName = "\u6211"
private const val ScrmMomentPostIdPrefix = "scrm-moment:"
private const val LocalScrmMomentPostIdPrefix = "local-scrm-moment:"

@Composable
private fun MomentPostRow(
    post: AppMomentPost,
    liked: Boolean,
    comments: List<AppMomentComment>,
    menuOpen: Boolean,
    commenting: Boolean,
    commentDraft: String,
    onToggleMenu: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCommentChange: (String) -> Unit,
    onPreviewMedia: () -> Unit,
    onSendComment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OverlayTokens.momentsBackground)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(post.avatarColor),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = post.avatarText,
                size = 9.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TextLabel(
                text = post.author,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
            TextLabel(
                text = post.content,
                size = 11.sp,
                color = OverlayTokens.panelPrimaryText,
                lineHeight = 15.sp,
                maxLines = 4
            )
            MomentMediaPreview(post = post, onPreviewMedia = onPreviewMedia)
            if (post.sourceLabel != null) {
                TextLabel(
                    text = post.sourceLabel,
                    size = 10.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.momentsName,
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextLabel(
                    text = post.time,
                    size = 9.sp,
                    color = OverlayTokens.momentsTime,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.width(148.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MomentMoreButton(onClick = onToggleMenu)
                    if (menuOpen) {
                        MomentLikeCommentPopup(
                            liked = liked,
                            onLike = onLike,
                            onComment = onComment,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-30).dp)
                        )
                    }
                }
            }
            if (liked || comments.isNotEmpty()) {
                MomentInteractionSummary(
                    liked = liked,
                    comments = comments
                )
            }
            if (commenting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelTextInput(
                        value = commentDraft,
                        onValueChange = onCommentChange,
                        placeholder = "评论",
                        modifier = Modifier.weight(1f)
                    )
                    SmallChoiceButton(label = "发送", onClick = onSendComment)
                }
            }
        }
    }
}

@Composable
private fun MomentMoreButton(onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(width = 28.dp, height = 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OverlayTokens.momentsMore),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = OverlayTokens.momentsName
            )
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MomentLikeCommentPopup(
    liked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialSurface(
        modifier = modifier
            .width(118.dp)
            .height(36.dp),
        shape = RoundedCornerShape(4.dp),
        color = OverlayTokens.momentsActionMenu,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MomentActionMenuButton(
                icon = Icons.Filled.ThumbUp,
                label = if (liked) "取消" else "赞",
                onClick = onLike,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(OverlayTokens.momentsActionDivider)
            )
            MomentActionMenuButton(
                icon = Icons.Filled.Textsms,
                label = "评论",
                onClick = onComment,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MomentActionMenuButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OverlayTokens.momentsActionText
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OverlayTokens.momentsActionText,
                modifier = Modifier.size(14.dp)
            )
            TextLabel(
                text = label,
                size = 10.sp,
                weight = FontWeight.Medium,
                color = OverlayTokens.momentsActionText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MomentMediaPreview(
    post: AppMomentPost,
    onPreviewMedia: () -> Unit,
    detail: Boolean = false
) {
    val media = post.media ?: return
    val widthDp = if (detail) {
        when (media.kind) {
            MomentMediaKind.Image, MomentMediaKind.Video -> media.widthDp.coerceIn(120, 220)
            MomentMediaKind.Link -> media.widthDp
        }
    } else {
        media.widthDp
    }
    val heightDp = if (detail) {
        when (media.kind) {
            MomentMediaKind.Image, MomentMediaKind.Video -> media.heightDp.coerceIn(90, 300)
            MomentMediaKind.Link -> media.heightDp
        }
    } else {
        media.heightDp
    }
    when (media.kind) {
        MomentMediaKind.Image -> {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(media.color)
                    .clickable(onClick = onPreviewMedia),
                contentAlignment = Alignment.Center
            ) {
                MomentMediaBitmap(media)
            }
        }
        MomentMediaKind.Video -> {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(media.color)
                    .clickable(onClick = onPreviewMedia),
                contentAlignment = Alignment.Center
            ) {
                MomentMediaBitmap(media)
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OverlayTokens.primaryText,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        MomentMediaKind.Link -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(OverlayTokens.momentsLinkCard)
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(media.color),
                    contentAlignment = Alignment.Center
                ) {
                    TextLabel(
                        text = media.label.orEmpty(),
                        size = 8.sp,
                        color = OverlayTokens.primaryText,
                        maxLines = 1
                    )
                }
                TextLabel(
                    text = post.linkTitle.orEmpty(),
                    size = 11.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MomentPendingMediaPreview(
    media: AppMomentMedia,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(OverlayTokens.momentsComposer)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(media.widthDp.coerceAtMost(92).dp)
                .height(media.heightDp.coerceAtMost(70).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(media.color),
            contentAlignment = Alignment.Center
        ) {
            MomentMediaBitmap(media)
            if (media.kind == MomentMediaKind.Video) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OverlayTokens.primaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        TextLabel(
            text = if (media.kind == MomentMediaKind.Video) "已选择视频" else "已选择图片",
            size = 10.sp,
            weight = FontWeight.Medium,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        SmallChoiceButton(label = "绉婚櫎", onClick = onClear)
    }
}

@Composable
private fun MomentMediaBitmap(media: AppMomentMedia) {
    val context = LocalContext.current
    val bitmap = remember(media.previewUri, media.uri) {
        when (media.kind) {
            MomentMediaKind.Image -> loadImageThumbnailBitmap(context, media.previewUri ?: media.uri)
            MomentMediaKind.Video -> loadVideoPreviewBitmap(context, media.previewUri, media.uri)
            MomentMediaKind.Link -> null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        TextLabel(
            text = media.label ?: if (media.kind == MomentMediaKind.Video) "视频" else "图片",
            size = 9.sp,
            color = OverlayTokens.primaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun MomentInteractionSummary(
    liked: Boolean,
    comments: List<AppMomentComment>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(OverlayTokens.momentsLinkCard)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (liked) {
            TextLabel(
                text = "我觉得不错",
                size = 9.sp,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
        }
        comments.forEach { comment ->
            TextLabel(
                text = "${comment.author}: ${comment.text}",
                size = 9.sp,
                color = OverlayTokens.panelPrimaryText,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

internal fun FloatingChatPickedMediaEvent.toMomentMedia(): AppMomentMedia {
    val isVideo = mediaKind == FloatingChatPrototype.PickedMediaKind.Video
    val aspect = aspectRatio?.coerceIn(0.45f, 2.2f) ?: if (isVideo) 16f / 9f else 1f
    val maxWidth = if (isVideo || aspect > 1f) 150 else 92
    val maxHeight = if (isVideo || aspect > 1f) 86 else 132
    val width = if (aspect >= 1f) maxWidth else (maxHeight * aspect).toInt().coerceIn(62, maxWidth)
    val height = if (aspect >= 1f) (maxWidth / aspect).toInt().coerceIn(58, maxHeight) else maxHeight
    return AppMomentMedia(
        kind = if (isVideo) MomentMediaKind.Video else MomentMediaKind.Image,
        uri = mediaUri,
        previewUri = previewUri,
        orientation = orientation,
        aspectRatio = aspectRatio,
        widthDp = width,
        heightDp = height,
        color = if (isVideo) Color(0xFF536878) else Color(0xFF7E806B),
        label = if (isVideo) "视频" else "图片"
    )
}

@Composable
internal fun MomentMaterialsPanel(
    route: ScrmFloatingAccountRoute?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var nameDraft by remember { mutableStateOf("") }
    var categoryDraft by remember { mutableStateOf("") }
    var contentDraft by remember { mutableStateOf("") }
    var createMaterialConfirmed by remember { mutableStateOf(false) }
    var materialOperationDraft by remember { mutableStateOf<MomentMaterialOperationPreviewDraft?>(null) }
    var materialOperationConfirmation by remember { mutableStateOf("") }
    var materialOperationConfirmed by remember { mutableStateOf(false) }
    var state by remember(route) { mutableStateOf(ScrmMomentMaterialsPanelState()) }

    fun requireRoute(): ScrmFloatingAccountRoute? {
        val currentRoute = route
        if (currentRoute == null) {
            state = state.copy(
                loading = false,
                status = null,
                error = "当前账号缺少 SCRM 路由，无法加载朋友圈素材"
            )
        }
        return currentRoute
    }

    fun loadMaterials() {
        val currentRoute = requireRoute() ?: return
        scope.launch {
            state = state.copy(loading = true, status = "正在加载朋友圈素材", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    loadScrmMomentMaterials(context.applicationContext, currentRoute)
                }
            }.onSuccess { materials ->
                val selected = state.selectedMaterial?.let { selected ->
                    materials.firstOrNull { material -> material.id == selected.id }
                }
                state = state.copy(
                    loading = false,
                    materials = materials,
                    selectedMaterial = selected,
                    selectedDetail = if (selected == null) null else state.selectedDetail,
                    detailOpen = selected != null && state.detailOpen,
                    status = "当前账号已加载 ${materials.size} 条素材",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun loadDetail(material: ScrmMomentMaterial) {
        val currentRoute = requireRoute() ?: return
        scope.launch {
            state = state.copy(
                loading = true,
                selectedMaterial = material,
                detailOpen = true,
                status = "正在加载素材详情",
                error = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    loadScrmMomentMaterialDetail(context.applicationContext, currentRoute, material)
                }
            }.onSuccess { detail ->
                state = state.copy(
                    loading = false,
                    selectedDetail = detail,
                    detailOpen = true,
                    status = "已加载素材详情 #${detail.id}",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun createMaterial() {
        val currentRoute = requireRoute() ?: return
        val content = contentDraft.trim()
        val name = nameDraft.trim()
        if (content.isBlank()) {
            state = state.copy(error = "请输入朋友圈素材内容")
            return
        }
        scope.launch {
            state = state.copy(loading = true, status = "正在创建朋友圈素材", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    createScrmMomentMaterial(
                        context.applicationContext,
                        currentRoute,
                        content,
                        name.takeIf { it.isNotEmpty() },
                        categoryDraft.trim().takeIf { it.isNotEmpty() }
                    )
                }
            }.onSuccess { material ->
                nameDraft = ""
                categoryDraft = ""
                contentDraft = ""
                state = state.copy(
                    loading = false,
                    materials = listOf(material) + state.materials.filterNot { it.id == material.id },
                    selectedMaterial = material,
                    selectedDetail = null,
                    detailOpen = false,
                    status = "已创建朋友圈素材 #${material.id}",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun copyMaterial(material: ScrmMomentMaterial) {
        requireRoute() ?: return
        scope.launch {
            state = state.copy(loading = true, status = "正在复制朋友圈素材", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    copyScrmMomentMaterial(context.applicationContext, material)
                }
            }.onSuccess { copied ->
                state = state.copy(
                    loading = false,
                    materials = listOf(copied) + state.materials,
                    selectedMaterial = copied,
                    selectedDetail = null,
                    detailOpen = false,
                    status = "已复制素材为 #${copied.id}",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun archiveMaterial(material: ScrmMomentMaterial) {
        requireRoute() ?: return
        scope.launch {
            state = state.copy(loading = true, status = "正在归档朋友圈素材", error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    archiveScrmMomentMaterial(context.applicationContext, material)
                }
            }.onSuccess { archived ->
                state = state.copy(
                    loading = false,
                    materials = state.materials.map { existing ->
                        if (existing.id == archived.id) archived else existing
                    },
                    selectedMaterial = archived,
                    selectedDetail = null,
                    detailOpen = false,
                    status = "已归档素材 #${archived.id}",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    status = null,
                    error = error.toScrmContactsPanelMessage()
                )
            }
        }
    }

    fun copyDetailText() {
        val content = scrmReadableText(state.selectedDetail?.template?.content)
            ?.takeIf { it.isNotBlank() }
            ?: run {
                state = state.copy(error = "当前素材详情没有可复制文案")
                return
            }
        clipboard.setText(AnnotatedString(content))
        state = state.copy(status = "已复制素材文案", error = null)
    }

    LaunchedEffect(route) {
        loadMaterials()
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            TextLabel(
                text = "朋友圈素材",
                size = 13.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            ScrmPanelButton(label = "刷新", enabled = !state.loading, onClick = ::loadMaterials)
            ScrmPanelButton(label = "关闭", onClick = onClose)
        }

        state.status?.let { status ->
            TextLabel(
                text = status,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2,
                lineHeight = 13.sp
            )
        }
        state.error?.let { error ->
            TextLabel(
                text = error,
                size = 10.sp,
                color = Color(0xFFB65757),
                maxLines = 2,
                lineHeight = 13.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(OverlayTokens.resourcePanel)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(9.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PanelTextInput(
                    value = nameDraft,
                    onValueChange = { nameDraft = it; createMaterialConfirmed = false },
                    placeholder = "素材名称",
                    modifier = Modifier.weight(0.9f)
                )
                PanelTextInput(
                    value = categoryDraft,
                    onValueChange = { categoryDraft = it; createMaterialConfirmed = false },
                    placeholder = "分类",
                    modifier = Modifier.weight(0.65f)
                )
            }
            PanelTextInput(
                value = contentDraft,
                onValueChange = { contentDraft = it; createMaterialConfirmed = false },
                placeholder = "朋友圈素材文案",
                modifier = Modifier.fillMaxWidth()
            )
            val createPreview = MomentMaterialCreatePreviewDraft(nameDraft, categoryDraft, contentDraft).toPreview()
            TextLabel("预览：${createPreview.name} / ${createPreview.category}", 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(createMaterialConfirmed, onCheckedChange = { createMaterialConfirmed = it })
                Text("我已确认素材名称、分类和文案", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ScrmPanelButton(
                    label = "生成预览",
                    enabled = !state.loading && createPreview.canGeneratePreview && createMaterialConfirmed,
                    accent = true,
                    onClick = {
                        state = state.copy(status = "创建素材：仅生成 UI 预览，未创建", error = null)
                    }
                )
            }
        }

        materialOperationDraft?.let { draft ->
            val preview = draft.copy(confirmationText = materialOperationConfirmation).toPreview()
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF4F1), RoundedCornerShape(7.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextLabel(text = "${preview.actionLabel}预览", size = 10.sp, weight = FontWeight.SemiBold, color = Color(0xFF9A3E20), maxLines = 1)
                TextLabel(preview.materialSummary, 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                if (draft.action == MomentMaterialOperation.Archive) {
                    PanelTextInput(materialOperationConfirmation, { materialOperationConfirmation = it; materialOperationConfirmed = false }, "请输入“归档素材”")
                }
                TextLabel(preview.confirmationHint, 10.sp, color = Color(0xFF9A3E20), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(materialOperationConfirmed, onCheckedChange = { materialOperationConfirmed = it })
                    Text("我已确认素材和影响范围", color = OverlayTokens.panelSecondaryText, fontSize = 10.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(enabled = preview.canGeneratePreview && materialOperationConfirmed, onClick = {
                        state = state.copy(status = "${preview.actionLabel}：仅生成 UI 预览，未执行", error = null)
                    }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text("生成预览", fontSize = 10.sp) }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 305.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.materials.isEmpty()) {
                    item {
                        TextLabel(
                            text = if (state.loading) "正在加载..." else "暂无朋友圈素材",
                            size = 10.sp,
                            color = OverlayTokens.panelSecondaryText,
                            modifier = Modifier.padding(vertical = 12.dp),
                            maxLines = 1
                        )
                    }
                }
                itemsIndexed(
                    items = state.materials,
                    key = { _, item -> item.id }
                ) { _, material ->
                    MomentMaterialRow(
                        material = material,
                        selected = state.selectedMaterial?.id == material.id,
                        enabled = !state.loading,
                        onSelect = { loadDetail(material) },
                        onCopy = {
                            materialOperationDraft = MomentMaterialOperationPreviewDraft(MomentMaterialOperation.Copy, material.id.toString(), material.displayName)
                            materialOperationConfirmation = ""
                            materialOperationConfirmed = false
                        },
                        onArchive = {
                            materialOperationDraft = MomentMaterialOperationPreviewDraft(MomentMaterialOperation.Archive, material.id.toString(), material.displayName)
                            materialOperationConfirmation = ""
                            materialOperationConfirmed = false
                        }
                    )
                }
            }
            if (state.detailOpen) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(OverlayTokens.centerPanelScrim)
                        .clickable {
                            state = state.copy(
                                selectedMaterial = null,
                                selectedDetail = null,
                                detailOpen = false
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSurface(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {})
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = OverlayTokens.panel,
                        border = BorderStroke(1.dp, OverlayTokens.panelBorder),
                        shadowElevation = 8.dp
                    ) {
                        MomentMaterialDetailPanel(
                            detail = state.selectedDetail,
                            material = state.selectedMaterial,
                            enabled = !state.loading,
                            modifier = Modifier.padding(10.dp),
                            onCopyText = ::copyDetailText,
                            onClose = {
                                state = state.copy(
                                    selectedMaterial = null,
                                    selectedDetail = null,
                                    detailOpen = false
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentMaterialRow(
    material: ScrmMomentMaterial,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onArchive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OverlayTokens.inputFocus.copy(alpha = 0.22f) else OverlayTokens.resourcePanel)
            .border(
                1.dp,
                if (selected) OverlayTokens.accent else OverlayTokens.resourcePanelBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF5A7CA5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextLabel(
                    text = material.displayName,
                    size = 11.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                TextLabel(
                    text = listOfNotNull(
                        scrmReadableText(material.category)?.takeIf { it.isNotBlank() },
                        scrmReadableText(material.statusName)?.takeIf { it.isNotBlank() }
                            ?: "状态 ${material.status}",
                        "${material.attachmentCount} 个附件"
                    ).joinToString(" / "),
                    size = 8.sp,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 1
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScrmPanelButton(label = "详情", enabled = enabled, onClick = onSelect)
            ScrmPanelButton(label = "复制预览", enabled = enabled, accent = true, onClick = onCopy)
            ScrmPanelButton(label = "归档预览", enabled = enabled, danger = true, onClick = onArchive)
        }
    }
}

@Composable
private fun MomentMaterialDetailPanel(
    detail: ScrmMomentMaterialDetail?,
    material: ScrmMomentMaterial?,
    enabled: Boolean,
    modifier: Modifier,
    onCopyText: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextLabel(
            text = "素材详情",
            size = 11.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            ScrmPanelButton(label = "关闭", enabled = enabled, onClick = onClose)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 305.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OverlayTokens.resourcePanel)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val title = detail?.displayName ?: material?.displayName ?: "未选择素材"
            TextLabel(
                text = title,
                size = 11.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 2,
                lineHeight = 14.sp
            )
            val content = scrmReadableText(detail?.template?.content)?.takeIf { it.isNotBlank() }
            TextLabel(
                text = content ?: "点击左侧素材加载详情后显示文案",
                size = 10.sp,
                color = if (content == null) OverlayTokens.panelSecondaryText else OverlayTokens.panelPrimaryText,
                maxLines = 6,
                lineHeight = 14.sp
            )
            TextLabel(
                text = detail?.let {
                    val status = scrmReadableText(it.statusName)?.takeIf(String::isNotBlank) ?: it.status
                    "附件 ${it.attachmentCount} / 评论 ${it.extCommentCount} / 状态 $status"
                } ?: "真实接口未返回详情前不展示模拟数据",
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2,
                lineHeight = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScrmPanelButton(
                    label = "复制文案",
                    enabled = enabled && content != null,
                    accent = true,
                    onClick = onCopyText
                )
            }
        }
    }
}
