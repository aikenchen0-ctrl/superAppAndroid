package com.paifa.univerge.accessibility

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import com.paifa.univerge.accessibility.floatingchat.media.AndroidVideoPlayerFactory
import com.paifa.univerge.accessibility.floatingchat.media.applyTextureViewAspectFillTransform
import com.paifa.univerge.accessibility.floatingchat.media.prepareVideoPlayer
import com.paifa.univerge.accessibility.floatingchat.media.releaseVideoPlayer
import com.paifa.univerge.accessibility.floatingchat.media.videoAspectRatioFromDimensions
import kotlin.math.abs
import kotlin.math.sin

/** Full-screen short-video feed used by the accessibility overlay action. */
@SuppressLint("ViewConstructor")
internal class VideoDemoOverlayView(
    context: Context,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.75f)
    private val pageHost = FrameLayout(context)
    private val tabRects = Array(VideoDemoCatalog.tabs.size) { RectF() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val heartPath = Path()
    private val likedIds = mutableSetOf<String>()
    private val followedIds = mutableSetOf<String>()
    private val savedIds = mutableSetOf<String>()

    private var selectedTab = VideoDemoTab.Recommended
    private var pageIndex = 0
    private var currentPage: VideoDemoPlaybackPage
    private var adjacentPage: VideoDemoPlaybackPage? = null
    private var adjacentIndex = 0
    private var adjacentDirection: VideoDemoPageDirection? = null
    private var transitionAnimator: ValueAnimator? = null
    private var dragOffsetY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var singleTapRunnable: Runnable? = null
    private var commentsVisible = false
    private var heartShownAt = 0L

    init {
        setWillNotDraw(false)
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.ubiki_video_demo_accessibility_description)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        pageHost.setWillNotDraw(false)
        pageHost.clipChildren = true
        addView(
            pageHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        currentPage = createPage(currentItem(), active = true)
        pageHost.addView(currentPage)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        currentPage.setActive(true)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }

    /** Explicitly releases both decoders; overlay removal is not guaranteed to trigger detach. */
    fun dispose() {
        singleTapRunnable?.let(::removeCallbacks)
        singleTapRunnable = null
        stopTransition()
        releaseAdjacentPage()
        currentPage.dispose()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawTopBar(canvas)
        drawHeartOverlay(canvas)
        if (commentsVisible) drawCommentsSheet(canvas)
        if (heartShownAt != 0L) postInvalidateOnAnimation()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (transitionAnimator != null) return true
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                dragOffsetY = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (commentsVisible || transitionAnimator != null) return true
                updateDrag(event.x - downX, event.y - downY)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (commentsVisible) {
                    handleCommentsTap(event.x, event.y)
                    return true
                }
                if (transitionAnimator != null) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (adjacentDirection != null) {
                    settleSwipe()
                } else if (
                    SystemClock.uptimeMillis() - downAt <= TAP_MAX_DURATION_MS &&
                    abs(dx) <= touchSlopPx() &&
                    abs(dy) <= touchSlopPx()
                ) {
                    handleTap(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (adjacentDirection != null) settleSwipe(commit = false)
                singleTapRunnable?.let(::removeCallbacks)
                singleTapRunnable = null
                return true
            }
        }
        return true
    }

    private fun updateDrag(dx: Float, dy: Float) {
        if (adjacentDirection == null && abs(dy) >= touchSlopPx()) {
            if (abs(dy) < abs(dx) * SWIPE_VERTICALITY_RATIO) return
            adjacentDirection = if (dy < 0f) {
                VideoDemoPageDirection.Next
            } else {
                VideoDemoPageDirection.Previous
            }
            prepareAdjacentPage(adjacentDirection ?: return)
        }
        val direction = adjacentDirection ?: return
        dragOffsetY = when (direction) {
            VideoDemoPageDirection.Next -> minOf(0f, dy)
            VideoDemoPageDirection.Previous -> maxOf(0f, dy)
        }.coerceIn(-height.toFloat(), height.toFloat())
        applySwipeLayout(direction, dragOffsetY)
        invalidate()
    }

    private fun settleSwipe(commit: Boolean = videoDemoShouldCommitSwipe(dragOffsetY, height.toFloat())) {
        val direction = adjacentDirection ?: return
        val candidate = adjacentPage ?: run {
            resetSwipeState()
            return
        }
        val viewportHeight = height.toFloat().coerceAtLeast(1f)
        val startCurrent = currentPage.translationY
        val startAdjacent = candidate.translationY
        val targetCurrent = if (commit) {
            if (direction == VideoDemoPageDirection.Next) -viewportHeight else viewportHeight
        } else {
            0f
        }
        val targetAdjacent = if (commit) {
            0f
        } else if (direction == VideoDemoPageDirection.Next) {
            viewportHeight
        } else {
            -viewportHeight
        }
        lateinit var runningAnimator: ValueAnimator
        runningAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PAGE_TRANSITION_MILLIS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedValue as Float
                currentPage.translationY = lerp(startCurrent, targetCurrent, fraction)
                candidate.translationY = lerp(startAdjacent, targetAdjacent, fraction)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (transitionAnimator !== runningAnimator) return
                    transitionAnimator = null
                    if (commit) commitSwipe(direction, candidate) else rollbackSwipe(candidate)
                }
            })
        }
        transitionAnimator = runningAnimator
        runningAnimator.start()
    }

    private fun prepareAdjacentPage(direction: VideoDemoPageDirection) {
        releaseAdjacentPage()
        val items = VideoDemoCatalog.itemsFor(selectedTab)
        if (items.isEmpty()) return
        adjacentIndex = videoDemoPageIndex(pageIndex, direction, items.size)
        adjacentPage = createPage(items[adjacentIndex], active = false).also { page ->
            pageHost.addView(page)
            page.translationY = if (direction == VideoDemoPageDirection.Next) {
                height.toFloat()
            } else {
                -height.toFloat()
            }
        }
    }

    private fun applySwipeLayout(direction: VideoDemoPageDirection, offsetY: Float) {
        val layout = videoDemoSwipeLayout(direction, offsetY, height.toFloat())
        currentPage.translationY = layout.currentTranslationY
        adjacentPage?.translationY = layout.adjacentTranslationY
    }

    private fun commitSwipe(direction: VideoDemoPageDirection, candidate: VideoDemoPlaybackPage) {
        val oldPage = currentPage
        pageHost.removeView(oldPage)
        oldPage.dispose()
        candidate.translationY = 0f
        candidate.setActive(true)
        currentPage = candidate
        pageIndex = adjacentIndex
        adjacentPage = null
        adjacentDirection = null
        dragOffsetY = 0f
        invalidate()
        announceAction(currentItem().author)
    }

    private fun rollbackSwipe(candidate: VideoDemoPlaybackPage) {
        pageHost.removeView(candidate)
        candidate.dispose()
        resetSwipeState()
    }

    private fun resetSwipeState() {
        currentPage.translationY = 0f
        adjacentPage?.translationY = 0f
        adjacentDirection = null
        dragOffsetY = 0f
        invalidate()
    }

    private fun stopTransition() {
        val animator = transitionAnimator ?: return
        transitionAnimator = null
        animator.removeAllListeners()
        animator.removeAllUpdateListeners()
        animator.cancel()
        adjacentDirection = null
        dragOffsetY = 0f
        currentPage.translationY = 0f
    }

    private fun releaseAdjacentPage() {
        adjacentPage?.let { page ->
            pageHost.removeView(page)
            page.dispose()
        }
        adjacentPage = null
        adjacentDirection = null
        dragOffsetY = 0f
    }

    private fun createPage(item: VideoDemoItem, active: Boolean): VideoDemoPlaybackPage {
        return VideoDemoPlaybackPage(
            context = context,
            isLiked = { id -> id in likedIds },
            isSaved = { id -> id in savedIds },
            onStateChanged = ::invalidate
        ).also { page ->
            page.bind(item, active)
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val topBarHeight = dp(TOP_BAR_HEIGHT_DP)
        if (y <= topBarHeight) {
            when {
                x <= dp(72f) -> {
                    onDismiss()
                    return
                }
                x >= width - dp(72f) -> {
                    showDemoMessage(context.getString(R.string.ubiki_video_demo_search_hint))
                    return
                }
                else -> tabRects.forEachIndexed { index, rect ->
                    if (rect.contains(x, y)) {
                        selectTab(VideoDemoCatalog.tabs[index])
                        return
                    }
                }
            }
        }

        currentPage.actionIndexAt(x, y - currentPage.translationY)?.let { actionIndex ->
            handleAction(actionIndex)
            return
        }

        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastTapAt <= DOUBLE_TAP_WINDOW_MS &&
            distanceSquared(x, y, lastTapX, lastTapY) <= dp(56f) * dp(56f)
        if (isDoubleTap) {
            singleTapRunnable?.let(::removeCallbacks)
            singleTapRunnable = null
            heartShownAt = now
            currentPage.invalidate()
            announceAction(context.getString(R.string.ubiki_video_demo_liked))
            lastTapAt = 0L
            invalidate()
            return
        }
        lastTapAt = now
        lastTapX = x
        lastTapY = y
        singleTapRunnable?.let(::removeCallbacks)
        val pendingTap = Runnable {
            currentPage.togglePlayback()
            announceAction(
                context.getString(
                    if (currentPage.isPlaying) R.string.ubiki_video_demo_playing
                    else R.string.ubiki_video_demo_paused
                )
            )
            singleTapRunnable = null
            invalidate()
        }
        singleTapRunnable = pendingTap
        postDelayed(pendingTap, DOUBLE_TAP_WINDOW_MS)
    }

    private fun handleAction(index: Int) {
        val item = currentItem()
        when (index) {
            ACTION_AVATAR -> {
                val next = toggleVideoDemoId(followedIds, item.id)
                followedIds.clear()
                followedIds.addAll(next)
                showDemoMessage(
                    context.getString(
                        if (item.id in followedIds) R.string.ubiki_video_demo_followed
                        else R.string.ubiki_video_demo_unfollowed
                    )
                )
            }
            ACTION_LIKE -> {
                val next = toggleVideoDemoId(likedIds, item.id)
                likedIds.clear()
                likedIds.addAll(next)
                currentPage.invalidate()
                if (item.id in likedIds) {
                    heartShownAt = SystemClock.uptimeMillis()
                    invalidate()
                }
            }
            ACTION_COMMENT -> {
                commentsVisible = true
                announceAction(context.getString(R.string.ubiki_video_demo_comments))
                invalidate()
            }
            ACTION_SAVE -> {
                val next = toggleVideoDemoId(savedIds, item.id)
                savedIds.clear()
                savedIds.addAll(next)
                currentPage.invalidate()
                showDemoMessage(
                    context.getString(
                        if (item.id in savedIds) R.string.ubiki_video_demo_saved
                        else R.string.ubiki_video_demo_unsaved
                    )
                )
            }
            ACTION_SHARE -> showDemoMessage(context.getString(R.string.ubiki_video_demo_share_hint))
        }
    }

    private fun selectTab(tab: VideoDemoTab) {
        if (selectedTab == tab) return
        stopTransition()
        releaseAdjacentPage()
        pageHost.removeView(currentPage)
        currentPage.dispose()
        selectedTab = tab
        pageIndex = 0
        currentPage = createPage(currentItem(), active = true)
        pageHost.addView(currentPage)
        announceAction(tabLabel(tab))
        invalidate()
    }

    private fun handleCommentsTap(x: Float, y: Float) {
        val sheetTop = height - dp(COMMENTS_SHEET_HEIGHT_DP)
        if (y < sheetTop || x >= width - dp(72f)) {
            commentsVisible = false
            announceAction(context.getString(R.string.ubiki_video_demo_comments_closed))
            invalidate()
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        fillPaint.color = Color.argb(92, 3, 6, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), dp(TOP_BAR_HEIGHT_DP), fillPaint)
        val left = dp(60f)
        val right = width - dp(76f)
        val tabWidth = (right - left) / VideoDemoCatalog.tabs.size.toFloat()
        VideoDemoCatalog.tabs.forEachIndexed { index, tab ->
            val tabLeft = left + index * tabWidth
            tabRects[index].set(tabLeft, 0f, tabLeft + tabWidth, dp(TOP_BAR_HEIGHT_DP))
            val selected = tab == selectedTab
            textPaint.textSize = sp(if (selected) 16f else 15f)
            textPaint.color = if (selected) Color.WHITE else Color.argb(166, 255, 255, 255)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = if (selected) boldPaint.typeface else textPaint.typeface
            canvas.drawText(tabLabel(tab), tabLeft + tabWidth / 2f, dp(42f), textPaint)
            if (selected) {
                fillPaint.color = Color.rgb(255, 107, 122)
                canvas.drawRoundRect(
                    tabLeft + tabWidth / 2f - dp(14f), dp(53f),
                    tabLeft + tabWidth / 2f + dp(14f), dp(56f), dp(2f), dp(2f), fillPaint
                )
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
        drawCloseIcon(canvas, dp(28f), dp(28f))
        drawSearchIcon(canvas, width - dp(30f), dp(28f))
    }

    private fun drawHeartOverlay(canvas: Canvas) {
        val shownAt = heartShownAt
        if (shownAt == 0L) return
        val elapsed = SystemClock.uptimeMillis() - shownAt
        if (elapsed >= HEART_ANIMATION_MILLIS) {
            heartShownAt = 0L
            return
        }
        val progress = elapsed.toFloat() / HEART_ANIMATION_MILLIS
        val scale = 0.72f + 0.42f * sin((progress * Math.PI).toFloat())
        buildHeartPath(heartPath, width / 2f, height / 2f, dp(66f) * scale)
        fillPaint.color = Color.argb(((1f - progress) * 230f).toInt(), 255, 93, 111)
        canvas.drawPath(heartPath, fillPaint)
        postInvalidateOnAnimation()
    }

    private fun drawCommentsSheet(canvas: Canvas) {
        fillPaint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        val top = height - dp(COMMENTS_SHEET_HEIGHT_DP)
        fillPaint.color = Color.rgb(25, 29, 35)
        canvas.drawRoundRect(0f, top, width.toFloat(), height.toFloat() + dp(20f), dp(18f), dp(18f), fillPaint)
        boldPaint.color = Color.WHITE
        boldPaint.textSize = sp(16f)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_comments), dp(20f), top + dp(34f), boldPaint)
        textPaint.color = Color.argb(180, 255, 255, 255)
        textPaint.textSize = sp(13f)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_comments_demo), dp(20f), top + dp(74f), textPaint)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_comments_second), dp(20f), top + dp(112f), textPaint)
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(width - dp(36f), top + dp(24f), width - dp(20f), top + dp(40f), linePaint)
        canvas.drawLine(width - dp(20f), top + dp(24f), width - dp(36f), top + dp(40f), linePaint)
    }

    private fun drawCloseIcon(canvas: Canvas, x: Float, y: Float) {
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(x - dp(8f), y - dp(8f), x + dp(8f), y + dp(8f), linePaint)
        canvas.drawLine(x + dp(8f), y - dp(8f), x - dp(8f), y + dp(8f), linePaint)
    }

    private fun drawSearchIcon(canvas: Canvas, x: Float, y: Float) {
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(2f)
        canvas.drawCircle(x - dp(3f), y - dp(3f), dp(8f), linePaint)
        canvas.drawLine(x + dp(3f), y + dp(3f), x + dp(10f), y + dp(10f), linePaint)
    }

    private fun currentItem(): VideoDemoItem {
        val items = VideoDemoCatalog.itemsFor(selectedTab)
        return items.getOrNull(pageIndex) ?: items.firstOrNull()
            ?: VideoDemoCatalog.itemsFor(VideoDemoTab.Recommended).first()
    }

    private fun tabLabel(tab: VideoDemoTab): String = when (tab) {
        VideoDemoTab.Recommended -> context.getString(R.string.ubiki_video_demo_tab_recommended)
        VideoDemoTab.Following -> context.getString(R.string.ubiki_video_demo_tab_following)
        VideoDemoTab.Nearby -> context.getString(R.string.ubiki_video_demo_tab_nearby)
    }

    private fun announceAction(label: String) {
        announceForAccessibility(label)
    }

    private fun showDemoMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        announceAction(message)
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * density * resources.configuration.fontScale.coerceAtLeast(0.8f)

    private fun touchSlopPx(): Float = dp(12f)

    private fun buildHeartPath(target: Path, centerX: Float, centerY: Float, size: Float) {
        target.reset()
        target.moveTo(centerX, centerY + size * 0.78f)
        target.cubicTo(centerX - size * 1.1f, centerY + size * 0.08f, centerX - size * 0.72f, centerY - size * 0.78f, centerX, centerY - size * 0.2f)
        target.cubicTo(centerX + size * 0.72f, centerY - size * 0.78f, centerX + size * 1.1f, centerY + size * 0.08f, centerX, centerY + size * 0.78f)
        target.close()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

    private companion object {
        const val ACTION_AVATAR = 0
        const val ACTION_LIKE = 1
        const val ACTION_COMMENT = 2
        const val ACTION_SAVE = 3
        const val ACTION_SHARE = 4
        const val TOP_BAR_HEIGHT_DP = 64f
        const val COMMENTS_SHEET_HEIGHT_DP = 270f
        const val ACTION_COUNT = 5
        const val ACTION_SPACING_DP = 76f
        const val TAP_MAX_DURATION_MS = 360L
        const val DOUBLE_TAP_WINDOW_MS = 280L
        const val HEART_ANIMATION_MILLIS = 720L
        const val PAGE_TRANSITION_MILLIS = 260L
        const val SWIPE_VERTICALITY_RATIO = 1.2f
    }
}

/** A single feed page with a real MediaPlayer-backed TextureView. */
@SuppressLint("ViewConstructor")
private class VideoDemoPlaybackPage(
    context: Context,
    private val isLiked: (String) -> Boolean,
    private val isSaved: (String) -> Boolean,
    private val onStateChanged: () -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.75f)
    private val textureView = TextureView(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val heartPath = Path()
    private val bookmarkPath = Path()
    private var item: VideoDemoItem? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var generation = 0L
    private var active = false
    private var prepared = false
    private var failed = false
    private var disposed = false
    private var videoAspectRatio: Float? = null

    init {
        setWillNotDraw(false)
        clipChildren = true
        textureView.setOpaque(false)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                if (!disposed) prepareForSurface(texture)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                applyTextureTransform()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                releasePlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(newItem: VideoDemoItem, isActive: Boolean) {
        if (item?.id == newItem.id) {
            setActive(isActive)
            return
        }
        releasePlayer()
        item = newItem
        active = isActive
        prepared = false
        failed = false
        videoAspectRatio = null
        if (textureView.isAvailable) textureView.surfaceTexture?.let(::prepareForSurface)
        invalidate()
    }

    fun setActive(value: Boolean) {
        active = value
        val currentPlayer = player ?: return
        if (!prepared) return
        runCatching {
            if (value) {
                if (!currentPlayer.isPlaying) currentPlayer.start()
            } else if (currentPlayer.isPlaying) {
                currentPlayer.pause()
            }
            onStateChanged()
            invalidate()
        }.onFailure { error ->
            Log.w(TAG, "failed to change video playback state", error)
        }
    }

    fun togglePlayback() {
        val currentPlayer = player ?: return
        if (!prepared || failed) return
        runCatching {
            if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.start()
            onStateChanged()
            invalidate()
        }.onFailure { error ->
            Log.w(TAG, "failed to toggle video playback", error)
        }
    }

    val isPlaying: Boolean
        get() = player?.isPlaying == true && active

    fun actionIndexAt(x: Float, localY: Float): Int? {
        if (x < width - dp(92f)) return null
        val startY = (height * 0.35f).coerceAtLeast(dp(150f))
        for (index in 0 until ACTION_COUNT) {
            if (abs(localY - (startY + index * dp(ACTION_SPACING_DP))) <= dp(34f)) return index
        }
        return null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        textureView.surfaceTextureListener = null
        releasePlayer()
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applyTextureTransform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentItem = item ?: return
        fillPaint.color = parseColor(currentItem.backgroundHex, Color.rgb(18, 24, 31))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val currentItem = item ?: return
        if (failed || !prepared) drawPlaybackStatus(canvas)
        drawPageChrome(canvas, currentItem)
    }

    private fun drawPlaybackStatus(canvas: Canvas) {
        boldPaint.color = Color.WHITE
        boldPaint.textSize = sp(24f)
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            context.getString(
                if (failed) R.string.ubiki_video_demo_load_failed
                else R.string.ubiki_video_demo_loading
            ),
            width / 2f,
            height / 2f,
            boldPaint
        )
        boldPaint.textAlign = Paint.Align.LEFT
    }

    private fun prepareForSurface(texture: SurfaceTexture) {
        val currentItem = item ?: return
        releasePlayer()
        val token = ++generation
        val newSurface = AndroidVideoPlayerFactory.createSurface(texture)
        val newPlayer = AndroidVideoPlayerFactory.createPlayer()
        surface = newSurface
        player = newPlayer
        prepared = false
        failed = false
        newPlayer.setOnVideoSizeChangedListener { sizedPlayer, width, height ->
            if (generation == token && player === sizedPlayer && !disposed) {
                videoAspectRatio = videoAspectRatioFromDimensions(width, height)
                applyTextureTransform()
            }
        }
        runCatching {
            prepareVideoPlayer(
                context = context,
                uri = Uri.parse(currentItem.videoUrl),
                surface = newSurface,
                player = newPlayer,
                looping = true,
                onPrepared = { preparedPlayer ->
                    if (generation == token && player === preparedPlayer && !disposed) {
                        prepared = true
                        failed = false
                        if (active) {
                            runCatching { preparedPlayer.start() }
                                .onFailure { error -> Log.w(TAG, "failed to start prepared video", error) }
                        }
                        applyTextureTransform()
                        onStateChanged()
                        invalidate()
                    }
                },
                onCompleted = {
                    if (generation == token && player === newPlayer && active) {
                        runCatching { newPlayer.start() }
                            .onFailure { error -> Log.w(TAG, "failed to restart looping video", error) }
                    }
                },
                onError = {
                    if (generation == token && player === newPlayer) {
                        failed = true
                        prepared = false
                        releasePlayer(newPlayer)
                        onStateChanged()
                        invalidate()
                    }
                    true
                }
            )
        }.onFailure { error ->
            failed = true
            prepared = false
            Log.w(TAG, "failed to prepare video source ${currentItem.videoUrl}", error)
            releasePlayer(newPlayer)
            onStateChanged()
            invalidate()
        }
    }

    private fun releasePlayer(expected: MediaPlayer? = null) {
        val currentPlayer = player ?: return
        if (expected != null && currentPlayer !== expected) return
        player = null
        prepared = false
        generation += 1L
        val currentSurface = surface
        surface = null
        if (currentSurface != null) {
            releaseVideoPlayer(currentPlayer, currentSurface)
        } else {
            runCatching { currentPlayer.release() }
                .onFailure { error -> Log.w(TAG, "failed to release video player", error) }
        }
        onStateChanged()
    }

    private fun applyTextureTransform() {
        applyTextureViewAspectFillTransform(textureView, videoAspectRatio)
    }

    private fun drawPageChrome(canvas: Canvas, currentItem: VideoDemoItem) {
        val left = dp(20f)
        val bottom = height - dp(30f)
        textPaint.color = Color.WHITE
        textPaint.textSize = sp(16f)
        textPaint.typeface = boldPaint.typeface
        canvas.drawText(currentItem.author, left, bottom - dp(64f), textPaint)
        textPaint.typeface = Paint().typeface
        textPaint.textSize = sp(14f)
        textPaint.color = Color.argb(232, 255, 255, 255)
        canvas.drawText(ellipsize(currentItem.caption, 34), left, bottom - dp(38f), textPaint)
        textPaint.textSize = sp(12f)
        textPaint.color = Color.argb(170, 255, 255, 255)
        canvas.drawText(currentItem.location, left, bottom - dp(14f), textPaint)

        val centerX = width - dp(42f)
        val startY = (height * 0.35f).coerceAtLeast(dp(150f))
        val centers = FloatArray(ACTION_COUNT) { index -> startY + index * dp(ACTION_SPACING_DP) }
        drawAvatar(canvas, centerX, centers[ACTION_AVATAR], currentItem)
        drawHeartIcon(canvas, centerX, centers[ACTION_LIKE], isLiked(currentItem.id))
        drawCommentIcon(canvas, centerX, centers[ACTION_COMMENT])
        drawBookmarkIcon(canvas, centerX, centers[ACTION_SAVE], isSaved(currentItem.id))
        drawShareIcon(canvas, centerX, centers[ACTION_SHARE])
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(11f)
        textPaint.color = Color.argb(226, 255, 255, 255)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_like_count), centerX, centers[ACTION_LIKE] + dp(34f), textPaint)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_comment_count), centerX, centers[ACTION_COMMENT] + dp(34f), textPaint)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_save_label), centerX, centers[ACTION_SAVE] + dp(34f), textPaint)
        canvas.drawText(context.getString(R.string.ubiki_video_demo_share_label), centerX, centers[ACTION_SHARE] + dp(34f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        if (prepared && !isPlaying) {
            fillPaint.color = Color.argb(164, 4, 7, 10)
            canvas.drawCircle(width / 2f, height / 2f, dp(32f), fillPaint)
            linePaint.color = Color.WHITE
            linePaint.strokeWidth = dp(3f)
            canvas.drawLine(width / 2f - dp(5f), height / 2f - dp(12f), width / 2f - dp(5f), height / 2f + dp(12f), linePaint)
            canvas.drawLine(width / 2f + dp(6f), height / 2f - dp(12f), width / 2f + dp(6f), height / 2f + dp(12f), linePaint)
        }
    }

    private fun drawAvatar(canvas: Canvas, x: Float, y: Float, currentItem: VideoDemoItem) {
        fillPaint.color = Color.argb(232, 245, 247, 246)
        canvas.drawCircle(x, y, dp(27f), fillPaint)
        fillPaint.color = parseColor(currentItem.accentHex, Color.rgb(104, 190, 220))
        canvas.drawCircle(x, y, dp(22f), fillPaint)
        fillPaint.color = Color.argb(110, 20, 30, 38)
        canvas.drawCircle(x, y - dp(7f), dp(7f), fillPaint)
        canvas.drawOval(x - dp(13f), y + dp(1f), x + dp(13f), y + dp(18f), fillPaint)
        fillPaint.color = Color.rgb(255, 105, 120)
        canvas.drawCircle(x, y + dp(28f), dp(9f), fillPaint)
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(1.8f)
        canvas.drawLine(x - dp(4f), y + dp(28f), x + dp(4f), y + dp(28f), linePaint)
        canvas.drawLine(x, y + dp(24f), x, y + dp(32f), linePaint)
    }

    private fun drawHeartIcon(canvas: Canvas, x: Float, y: Float, selected: Boolean) {
        buildHeartPath(heartPath, x, y, dp(17f))
        if (selected) {
            fillPaint.color = Color.rgb(255, 91, 111)
            canvas.drawPath(heartPath, fillPaint)
        } else {
            linePaint.color = Color.WHITE
            linePaint.strokeWidth = dp(2.2f)
            canvas.drawPath(heartPath, linePaint)
        }
    }

    private fun drawCommentIcon(canvas: Canvas, x: Float, y: Float) {
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(RectF(x - dp(16f), y - dp(13f), x + dp(16f), y + dp(11f)), dp(6f), dp(6f), linePaint)
        canvas.drawLine(x - dp(7f), y + dp(10f), x - dp(12f), y + dp(17f), linePaint)
    }

    private fun drawBookmarkIcon(canvas: Canvas, x: Float, y: Float, selected: Boolean) {
        bookmarkPath.reset()
        bookmarkPath.moveTo(x - dp(12f), y - dp(16f))
        bookmarkPath.lineTo(x + dp(12f), y - dp(16f))
        bookmarkPath.lineTo(x + dp(12f), y + dp(16f))
        bookmarkPath.lineTo(x, y + dp(8f))
        bookmarkPath.lineTo(x - dp(12f), y + dp(16f))
        bookmarkPath.close()
        if (selected) {
            fillPaint.color = Color.rgb(255, 194, 86)
            canvas.drawPath(bookmarkPath, fillPaint)
        } else {
            linePaint.color = Color.WHITE
            linePaint.strokeWidth = dp(2f)
            canvas.drawPath(bookmarkPath, linePaint)
        }
    }

    private fun drawShareIcon(canvas: Canvas, x: Float, y: Float) {
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(x - dp(12f), y, x + dp(8f), y - dp(10f), linePaint)
        canvas.drawLine(x - dp(12f), y, x + dp(8f), y + dp(10f), linePaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(x - dp(13f), y, dp(5f), fillPaint)
        canvas.drawCircle(x + dp(11f), y - dp(12f), dp(5f), fillPaint)
        canvas.drawCircle(x + dp(11f), y + dp(12f), dp(5f), fillPaint)
    }

    private fun buildHeartPath(target: Path, centerX: Float, centerY: Float, size: Float) {
        target.reset()
        target.moveTo(centerX, centerY + size * 0.78f)
        target.cubicTo(centerX - size * 1.1f, centerY + size * 0.08f, centerX - size * 0.72f, centerY - size * 0.78f, centerX, centerY - size * 0.2f)
        target.cubicTo(centerX + size * 0.72f, centerY - size * 0.78f, centerX + size * 1.1f, centerY + size * 0.08f, centerX, centerY + size * 0.78f)
        target.close()
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * density * resources.configuration.fontScale.coerceAtLeast(0.8f)

    private fun parseColor(value: String, fallback: Int): Int = runCatching { Color.parseColor(value) }.getOrDefault(fallback)

    private fun ellipsize(value: String, maxCharacters: Int): String =
        if (value.length <= maxCharacters) value else value.take(maxCharacters - 1) + "..."

    private companion object {
        const val TAG = "UbikiVideoDemo"
        const val ACTION_AVATAR = 0
        const val ACTION_LIKE = 1
        const val ACTION_COMMENT = 2
        const val ACTION_SAVE = 3
        const val ACTION_SHARE = 4
        const val ACTION_COUNT = 5
        const val ACTION_SPACING_DP = 76f
    }
}
