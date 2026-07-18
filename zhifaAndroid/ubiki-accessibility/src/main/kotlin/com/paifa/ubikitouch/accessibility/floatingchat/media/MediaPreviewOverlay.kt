package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatImageActionPill
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatMediaStatusPill
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatVideoPlayerControlBar
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaPreviewOverlay(
    mediaMessages: List<FloatingChatMessage>,
    initialIndex: Int,
    actionStatus: String?,
    favoriteMediaIds: Map<String, Boolean>,
    externalDismissSignal: Long,
    initialDismissSignal: Long,
    onClose: () -> Unit,
    onOpenActions: (FloatingChatMessage) -> Unit,
    onMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaMessages.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, mediaMessages.lastIndex),
        pageCount = { mediaMessages.size }
    )
    val scope = rememberCoroutineScope()
    val pageZoomById = remember { mutableStateMapOf<String, Float>() }
    var handledDismissSignal by remember { mutableStateOf(initialDismissSignal) }
    var dismissOffsetY by remember(mediaMessages) { mutableStateOf(0f) }
    var dismissScale by remember(mediaMessages) { mutableStateOf(1f) }
    var dismissAlpha by remember(mediaMessages) { mutableStateOf(1f) }
    var viewportHeightPx by remember(mediaMessages) { mutableStateOf(0f) }
    val currentMessage = mediaMessages.getOrNull(pagerState.currentPage) ?: mediaMessages.first()
    val currentZoomScale = pageZoomById[currentMessage.id] ?: mediaPreviewMinimumZoom()
    val canSwipePages = dismissOffsetY == 0f &&
        currentZoomScale <= mediaPreviewMinimumZoom() + 0.01f

    fun animatePreviewTransform(
        targetOffsetY: Float,
        targetScale: Float,
        targetAlpha: Float,
        closeAfter: Boolean
    ) {
        scope.launch {
            val startOffsetY = dismissOffsetY
            val startScale = dismissScale
            val startAlpha = dismissAlpha
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (closeAfter) {
                    tween(durationMillis = 220)
                } else {
                    spring(stiffness = Spring.StiffnessLow)
                }
            ) { value, _ ->
                dismissOffsetY = lerpFloat(startOffsetY, targetOffsetY, value)
                dismissScale = lerpFloat(startScale, targetScale, value)
                dismissAlpha = lerpFloat(startAlpha, targetAlpha, value)
            }
            if (closeAfter) {
                onClose()
            } else {
                dismissOffsetY = 0f
                dismissScale = 1f
                dismissAlpha = 1f
            }
        }
    }

    fun dismissWithShrinkAnimation() {
        animatePreviewTransform(
            targetOffsetY = 0f,
            targetScale = 0.78f,
            targetAlpha = 0f,
            closeAfter = true
        )
    }

    LaunchedEffect(externalDismissSignal) {
        if (externalDismissSignal > handledDismissSignal) {
            handledDismissSignal = externalDismissSignal
            dismissWithShrinkAnimation()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(OverlayTokens.blankScrim)
            .onSizeChanged { size ->
                viewportHeightPx = size.height.toFloat()
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismissOffsetY
                    scaleX = dismissScale
                    scaleY = dismissScale
                    alpha = dismissAlpha
                }
                .pointerInput(currentMessage.id, currentZoomScale, viewportHeightPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (currentZoomScale > mediaPreviewMinimumZoom() + 0.01f) {
                                return@detectVerticalDragGestures
                            }
                            val nextOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
                            if (nextOffsetY == dismissOffsetY) return@detectVerticalDragGestures
                            change.consume()
                            dismissOffsetY = nextOffsetY
                            val progress = if (viewportHeightPx <= 0f) {
                                0f
                            } else {
                                (dismissOffsetY / (viewportHeightPx * 0.72f)).coerceIn(0f, 1f)
                            }
                            dismissScale = 1f - (progress * 0.18f)
                            dismissAlpha = 1f - (progress * 0.38f)
                        },
                        onDragCancel = {
                            animatePreviewTransform(
                                targetOffsetY = 0f,
                                targetScale = 1f,
                                targetAlpha = 1f,
                                closeAfter = false
                            )
                        },
                        onDragEnd = {
                            if (dismissOffsetY > viewportHeightPx * 0.12f) {
                                animatePreviewTransform(
                                    targetOffsetY = viewportHeightPx * 0.82f,
                                    targetScale = 0.78f,
                                    targetAlpha = 0f,
                                    closeAfter = true
                                )
                            } else {
                                animatePreviewTransform(
                                    targetOffsetY = 0f,
                                    targetScale = 1f,
                                    targetAlpha = 1f,
                                    closeAfter = false
                                )
                            }
                        }
                    )
                },
            userScrollEnabled = canSwipePages,
            beyondViewportPageCount = 1,
            key = { page -> mediaMessages[page].id }
        ) { page ->
            MediaPreviewPage(
                message = mediaMessages[page],
                isActive = pagerState.currentPage == page,
                onZoomChange = { scale ->
                    pageZoomById[mediaMessages[page].id] = scale
                },
                onDismissRequest = ::dismissWithShrinkAnimation,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (currentMessage.type != FloatingChatMessageType.VideoPreview) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FloatingChatImageActionPill(
                    label = "识图",
                    onClick = { onMediaAction(currentMessage, MediaActionContract.AnalyzeImage) }
                )
                FloatingChatImageActionPill(
                    label = "找物",
                    onClick = { onMediaAction(currentMessage, MediaActionContract.FindObject) }
                )
            }
        }

        FloatingChatStandaloneImageQuickActions(
            onOpenActions = { onOpenActions(currentMessage) },
            onShare = { onMediaAction(currentMessage, MediaActionContract.Share) },
            onSave = { onMediaAction(currentMessage, MediaActionContract.Save) },
            onFavorite = { onMediaAction(currentMessage, MediaActionContract.Favorite) },
            favorite = favoriteMediaIds[currentMessage.id] == true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 18.dp)
        )

        actionStatus?.let { status ->
            FloatingChatMediaStatusPill(
                text = status,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }

        VisibilityAccessStrip(
            visibility = currentMessage.visibility,
            accessState = currentMessage.accessState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaPreviewPage(
    message: FloatingChatMessage,
    isActive: Boolean,
    onZoomChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideo = message.type == FloatingChatMessageType.VideoPreview
    var zoomScale by remember(message.id) { mutableStateOf(mediaPreviewMinimumZoom()) }
    var offsetX by remember(message.id) { mutableStateOf(0f) }
    var offsetY by remember(message.id) { mutableStateOf(0f) }
    var viewportWidthPx by remember(message.id) { mutableStateOf(0) }
    var viewportHeightPx by remember(message.id) { mutableStateOf(0) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (isVideo || !isActive) return@rememberTransformableState
        val nextScale = (zoomScale * zoomChange).coerceIn(
            mediaPreviewMinimumZoom(),
            mediaPreviewMaximumZoom()
        )
        zoomScale = nextScale
        if (nextScale == mediaPreviewMinimumZoom()) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val maxOffsetX = ((viewportWidthPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((viewportHeightPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            zoomScale = mediaPreviewMinimumZoom()
            offsetX = 0f
            offsetY = 0f
        }
    }

    LaunchedEffect(zoomScale, isActive, message.id) {
        onZoomChange(
            if (isActive) {
                zoomScale
            } else {
                mediaPreviewMinimumZoom()
            }
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(OverlayTokens.blankScrim)
                .onSizeChanged { size ->
                    viewportWidthPx = size.width
                    viewportHeightPx = size.height
                }
                .then(
                    if (isVideo) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(message.id, zoomScale, isActive) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isActive) return@detectTapGestures
                                        if (zoomScale > mediaPreviewMinimumZoom()) {
                                            zoomScale = mediaPreviewMinimumZoom()
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            onDismissRequest()
                                        }
                                    }
                                )
                            }
                            .transformable(
                                state = transformState,
                                canPan = {
                                    zoomScale > mediaPreviewMinimumZoom() + 0.01f
                                }
                            )
                    }
                )
        ) {
            if (isVideo) {
                val frame = mediaPreviewFrameSize(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    orientation = message.thumbnailOrientation,
                    mediaAspectRatio = message.mediaAspectRatio ?: VideoPreviewMetadataResolver.aspectRatio(context, message)
                )
                MediaPreviewVideoPlayer(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(frame.width)
                        .height(frame.height)
                )
            } else {
                MediaThumbnailSurface(
                    message = message,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    showChrome = false,
                    useAspectFit = mediaPreviewUsesAspectFit()
                )
            }
        }
    }
}

@Composable
internal fun MediaPreviewVideoPlayer(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaUri = remember(message.type, message.resourceUrl, message.thumbnailUrl) {
        playableVideoUriForMessage(message)
    }
    if (mediaUri == null) {
        MediaThumbnailSurface(
            message = message,
            modifier = modifier,
            showChrome = false,
            useAspectFit = mediaPreviewVideoUsesAspectFit()
        )
        return
    }
    val tapSource = remember { MutableInteractionSource() }
    val targetAspectRatio = remember(message.id, message.mediaAspectRatio, message.thumbnailOrientation, message.resourceUrl, message.thumbnailUrl) {
        message.mediaAspectRatio ?: VideoPreviewMetadataResolver.aspectRatio(context, message)
    }
    var playbackRequested by remember(message.id) { mutableStateOf(false) }
    var surfaceTexture by remember(message.id) { mutableStateOf<SurfaceTexture?>(null) }
    var mediaPlayerRef by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(message.id) { mutableStateOf(false) }
    var playing by remember(message.id) { mutableStateOf(false) }
    var completed by remember(message.id) { mutableStateOf(false) }
    var failed by remember(message.id) { mutableStateOf(false) }
    var durationMs by remember(message.id) { mutableStateOf(0) }
    var positionMs by remember(message.id) { mutableStateOf(0) }
    var sliderPositionMs by remember(message.id) { mutableStateOf(0f) }
    var draggingProgress by remember(message.id) { mutableStateOf(false) }

    VideoPlaybackProgressTicker(
        player = mediaPlayerRef,
        playing = playing,
        dragging = draggingProgress,
        completed = completed,
        onPositionChanged = { positionMs = it; sliderPositionMs = it.toFloat() }
    )

    DisposableEffect(message.id, mediaUri, surfaceTexture, playbackRequested) {
        val texture = surfaceTexture
        if (!playbackRequested || texture == null) {
            onDispose {
                mediaPlayerRef = null
                prepared = false
                playing = false
                completed = false
                failed = false
            }
        } else {
            prepared = false
            playing = false
            completed = false
            failed = false

            val surface = AndroidVideoPlayerFactory.createSurface(texture)
            val mediaPlayer = AndroidVideoPlayerFactory.createPlayer()
            mediaPlayerRef = mediaPlayer
            var released = false

            val releasePlayer = {
                if (released) {
                    Unit
                } else {
                    released = true
                    releaseVideoPlayer(mediaPlayer, surface)
                    if (mediaPlayerRef === mediaPlayer) {
                        mediaPlayerRef = null
                    }
                }
            }

            runCatching {
                prepareVideoPlayer(
                    context = context,
                    uri = mediaUri,
                    surface = surface,
                    player = mediaPlayer,
                    onPrepared = { player ->
                        prepared = true
                        failed = false
                        completed = false
                        durationMs = player.duration.coerceAtLeast(0)
                        positionMs = 0
                        sliderPositionMs = 0f
                        player.start()
                        playing = true
                    },
                    onCompleted = {
                        playing = false
                        completed = true
                        positionMs = durationMs
                        sliderPositionMs = durationMs.toFloat()
                    },
                    onError = {
                        failed = true
                        playing = false
                        true
                    }
                )
            }.onFailure {
                Log.w(MediaPreviewOverlayTag, "failed to open preview video source: $mediaUri", it)
                failed = true
                playing = false
                releasePlayer()
            }

            onDispose {
                prepared = false
                playing = false
                releasePlayer()
            }
        }
    }

    Box(modifier = modifier) {
        if (playbackRequested) {
            VideoTextureSurface(
                videoAspectRatio = targetAspectRatio,
                modifier = Modifier.fillMaxSize(),
                onSurfaceAvailable = { texture -> surfaceTexture = texture },
                onSurfaceDestroyed = { surfaceTexture = null }
            )
        }

        val showPoster = !playbackRequested || failed
        if (showPoster) {
            MediaThumbnailSurface(
                message = message,
                modifier = Modifier.fillMaxSize(),
                showChrome = false,
                useAspectFit = mediaPreviewVideoUsesAspectFit()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = tapSource,
                    indication = null
                ) {
                    val player = mediaPlayerRef
                    when (decideVideoPlayback(
                        playbackRequested = playbackRequested,
                        failed = failed,
                        playerReady = player != null && prepared,
                        playing = player?.isPlaying == true,
                        completed = completed
                    )) {
                        VideoPlaybackDecision.RequestPlayback -> playbackRequested = true
                        VideoPlaybackDecision.Retry -> {
                            playbackRequested = false
                            surfaceTexture = null
                            prepared = false
                            completed = false
                            failed = false
                            durationMs = 0
                            positionMs = 0
                            sliderPositionMs = 0f
                            playbackRequested = true
                        }
                        VideoPlaybackDecision.Ignore -> Unit
                        VideoPlaybackDecision.Pause -> {
                            if (player == null) return@clickable
                            player.pause()
                            playing = false
                        }
                        VideoPlaybackDecision.Restart -> {
                            if (player == null) return@clickable
                            player.seekTo(0)
                            player.start()
                            completed = false
                            playing = true
                        }
                        VideoPlaybackDecision.Play -> {
                            if (player == null) return@clickable
                            player.start()
                            playing = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!playing) {
                VideoPlayGlyph(modifier = Modifier.fillMaxSize())
            }

            FloatingChatVideoPlayerControlBar(
                currentPositionMs = if (draggingProgress) sliderPositionMs.toInt() else positionMs,
                durationMs = durationMs,
                playing = playing,
                onTogglePlayback = {
                    val player = mediaPlayerRef ?: return@FloatingChatVideoPlayerControlBar
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        if (completed) {
                            player.seekTo(0)
                            completed = false
                            positionMs = 0
                            sliderPositionMs = 0f
                        }
                        player.start()
                        playing = true
                    }
                },
                sliderValue = sliderPositionMs,
                onSliderValueChange = { value ->
                    draggingProgress = true
                    sliderPositionMs = value
                },
                onSliderChangeFinished = {
                    val player = mediaPlayerRef ?: return@FloatingChatVideoPlayerControlBar
                    val seekPosition = sliderPositionMs.toInt().coerceIn(0, durationMs)
                    player.seekTo(seekPosition)
                    positionMs = seekPosition
                    draggingProgress = false
                    if (completed && seekPosition < durationMs) {
                        completed = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = mediaPreviewVideoControlBottomPaddingDp().dp)
            )
        }
    }
}

private data class MediaPreviewFrame(
    val width: Dp,
    val height: Dp
)

private fun mediaPreviewFrameSize(
    maxWidth: Dp,
    maxHeight: Dp,
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float? = null
): MediaPreviewFrame {
    val widthLimit = maxWidth * mediaPreviewMaxWidthFraction()
    val heightLimit = maxHeight * mediaPreviewMaxHeightFraction()
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    var width = widthLimit
    var height = width / aspectRatio
    if (height > heightLimit) {
        height = heightLimit
        width = height * aspectRatio
    }
    return MediaPreviewFrame(width = width, height = height)
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private const val MediaPreviewOverlayTag = "FloatingChatMediaPreview"
