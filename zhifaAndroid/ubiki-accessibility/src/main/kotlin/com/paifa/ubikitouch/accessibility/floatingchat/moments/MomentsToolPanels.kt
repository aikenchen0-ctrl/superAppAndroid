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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface as MaterialSurface
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
            SmallChoiceButton(label = "鍒锋柊", onClick = ::loadMoments)
            Spacer(modifier = Modifier.width(6.dp))
            SmallChoiceButton(label = "鍥剧墖/瑙嗛", onClick = onPickMedia)
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
                            text = "杩欎竴鍒荤殑鎯虫硶...",
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
            text = media.label ?: if (media.kind == MomentMediaKind.Video) "瑙嗛" else "鍥剧墖",
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
        label = if (isVideo) "瑙嗛" else "鍥剧墖"
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
                    status = "已加载 ${materials.size} 条素材，当前账号 ${currentRoute.weChatId}",
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
        val content = state.selectedDetail?.template?.content
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
            ScrmPanelButton(label = "鍒锋柊", enabled = !state.loading, onClick = ::loadMaterials)
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
                    onValueChange = { nameDraft = it },
                    placeholder = "素材名称",
                    modifier = Modifier.weight(0.9f)
                )
                PanelTextInput(
                    value = categoryDraft,
                    onValueChange = { categoryDraft = it },
                    placeholder = "鍒嗙被",
                    modifier = Modifier.weight(0.65f)
                )
                ScrmPanelButton(
                    label = "淇濆瓨",
                    enabled = !state.loading,
                    accent = true,
                    onClick = ::createMaterial
                )
            }
            PanelTextInput(
                value = contentDraft,
                onValueChange = { contentDraft = it },
                placeholder = "朋友圈素材文案"
            )
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
                        onCopy = { copyMaterial(material) },
                        onArchive = { archiveMaterial(material) }
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
                        material.category?.takeIf { it.isNotBlank() },
                        material.statusName?.takeIf { it.isNotBlank() } ?: "状态 ${material.status}",
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
            ScrmPanelButton(label = "复制", enabled = enabled, accent = true, onClick = onCopy)
            ScrmPanelButton(label = "归档", enabled = enabled, danger = true, onClick = onArchive)
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
            val content = detail?.template?.content?.takeIf { it.isNotBlank() }
            TextLabel(
                text = content ?: "点击左侧素材加载详情后显示文案",
                size = 10.sp,
                color = if (content == null) OverlayTokens.panelSecondaryText else OverlayTokens.panelPrimaryText,
                maxLines = 6,
                lineHeight = 14.sp
            )
            TextLabel(
                text = detail?.let {
                    "附件 ${it.attachmentCount} / 评论 ${it.extCommentCount} / 状态 ${it.statusName ?: it.status}"
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
