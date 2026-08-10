/*
 * 功能概览：接收边缘 View 的 DOWN/MOVE/UP/CANCEL 事件，维护手势状态并回调上层。
 * Kotlin 语法提示：构造函数中的函数类型 `(A) -> B` 表示回调；`= {}` 是默认的空实现。
 */
package com.paifa.ubikitouch.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.paifa.ubikitouch.core.gesture.BackGestureCommitController
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.gesture.hitTestBackGestureOption
import com.paifa.ubikitouch.core.gesture.SwipeClassifier
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.hypot

// 这个类只负责“识别和报告”，具体动作由 ubiki-accessibility 的回调决定。
class EdgeGestureDetector(
    private val side: EdgeSide,
    private val minSwipeDistancePx: Float,
    private val longSwipeDistancePx: Float = minSwipeDistancePx,
    private val minPreviewDistancePx: Float = 0f,
    private val minVerticalSwipeDistancePx: Float = minSwipeDistancePx,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onGestureProgress: (GestureData) -> Unit = {},
    private val onGestureEnd: () -> Unit = {},
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCommit: (BackGestureProgress, GestureData) -> Boolean = { _, _ -> false },
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {},
    private val density: Float = 1f,
    private val viewportHeightPx: () -> Float = { Float.POSITIVE_INFINITY }
) {
    private enum class State {
        IDLE,
        TOUCH_DOWN,
        SWIPING
    }

    private val handler = Handler(Looper.getMainLooper())
    private val classifier = SwipeClassifier()
    private var state = State.IDLE
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var previousMoveX = 0f
    private var previousMoveTimeMs = 0L
    private var pullHoldArmed = false
    private var pullHoldAnchorX = 0f
    private var pullHoldAnchorY = 0f
    private var backProgressActive = false
    private var committedGestureConsumed = false
    private var gestureProgressEmitted = false
    private val backCommitController = BackGestureCommitController()

    private val pullHoldRunnable = Runnable {
        if (state == State.SWIPING && pullHoldArmed) {
            val data = GestureData(startX, startY, currentX, currentY)
            reset()
            onGesture(GestureType.PULL_INWARD_HOLD, data)
        }
    }

    // Android 会把连续触摸事件交给这里；返回 true 表示该 View 消费了事件。
    fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y
        val inwardVelocityPxPerSecond = inwardVelocityPxPerSecond(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event, inwardVelocityPxPerSecond)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> {
                finishGestureProgress()
                reset()
            }
        }
        return true
    }

    // 外层 overlay 被移除或暂停时主动取消，避免延迟任务在旧 View 上继续执行。
    fun cancel() {
        finishGestureProgress()
        reset()
    }

    private fun handleDown(event: MotionEvent) {
        startX = event.x
        startY = event.y
        currentX = event.x
        currentY = event.y
        previousMoveX = event.x
        previousMoveTimeMs = event.eventTime

        state = State.TOUCH_DOWN
    }

    private fun handleMove(event: MotionEvent, inwardVelocityPxPerSecond: Float) {
        // MOVE 阶段同时更新普通手势进度和返回手势预览。
        gestureProgressEmitted = true
        onGestureProgress(GestureData(startX, startY, event.x, event.y))
        val backProgress = updateBackGestureProgress(event, inwardVelocityPxPerSecond)
        if (backProgress != null && state == State.TOUCH_DOWN) {
            state = State.SWIPING
        }
        previousMoveX = event.x
        previousMoveTimeMs = event.eventTime
        if (state == State.SWIPING) {
            updatePullHoldCandidate(event)
            return
        }
        if (state != State.TOUCH_DOWN) return
        val distance = hypot(event.x - startX, event.y - startY)
        if (distance > minSwipeDistancePx) {
            state = State.SWIPING
            updatePullHoldCandidate(event)
        }
    }

    // 抬手时完成一次提交/取消判断，并把未消费的轨迹交给普通分类器。
    private fun handleUp(event: MotionEvent) {
        cancelPullHold()
        val dx = event.x - startX
        val dy = event.y - startY
        val distance = hypot(dx, dy)
        val data = GestureData(startX, startY, event.x, event.y)
        val backProgress = updateBackGestureProgress(event, inwardVelocityPxPerSecond = 0f)
        if (backProgress != null) {
            onBackGestureEnd(backProgress)
            if (backProgress.committed) {
                committedGestureConsumed = onBackGestureCommit(backProgress, data)
            }
            backProgressActive = false
        } else {
            cancelBackProgress()
        }

        finishGestureProgress()
        when (state) {
            State.TOUCH_DOWN -> reset()
            State.SWIPING -> {
                if (distance >= minSwipeDistancePx && !committedGestureConsumed) {
                    val type = backProgress?.gestureType ?: classifier.classify(side, dx, dy)
                    type?.takeIf {
                        it != GestureType.SWIPE_UP && it != GestureType.SWIPE_DOWN ||
                            kotlin.math.abs(dy) >= minVerticalSwipeDistancePx
                    }?.let { onGesture(it, data) }
                }
                reset()
            }
            else -> reset()
        }
    }

    private fun finishGestureProgress() {
        if (!gestureProgressEmitted) return
        gestureProgressEmitted = false
        onGestureEnd()
    }

    // 将位移转换为领域层进度，再补充当前命中的扇形选项。
    private fun updateBackGestureProgress(
        event: MotionEvent,
        inwardVelocityPxPerSecond: Float
    ): BackGestureProgress? {
        val baseProgress = BackGestureProgress.fromDelta(
            side = side,
            dx = event.x - startX,
            dy = event.y - startY,
            thresholdPx = minSwipeDistancePx,
            longThresholdPx = longSwipeDistancePx,
            touchY = event.y,
            startY = startY,
            minimumDragDistancePx = minPreviewDistancePx
        ) ?: run {
            cancelBackProgress()
            return null
        }
        val progress = baseProgress.copy(
            selectedOption = hitTestBackGestureOption(
                side = side,
                startX = startX,
                startY = startY,
                touchX = event.x,
                touchY = event.y,
                progress = baseProgress.progress,
                density = density,
                viewportHeightPx = viewportHeightPx()
            )
        )
        backProgressActive = true
        onBackGestureProgress(progress)
        backCommitController.update(progress, inwardVelocityPxPerSecond)
        return progress
    }

    private fun cancelBackProgress() {
        if (!backProgressActive) return
        backProgressActive = false
        onBackGestureCancel()
    }

    private fun updatePullHoldCandidate(event: MotionEvent) {
        val dx = event.x - startX
        val dy = event.y - startY
        val distance = hypot(dx, dy)
        val type = if (distance >= minSwipeDistancePx) {
            BackGestureProgress.fromDelta(
                side = side,
                dx = dx,
                dy = dy,
                thresholdPx = minSwipeDistancePx,
                longThresholdPx = longSwipeDistancePx
            )?.gestureType
        } else {
            null
        }
        if (type != GestureType.PULL_INWARD_SHORT && type != GestureType.PULL_INWARD_LONG) {
            cancelPullHold()
            return
        }

        if (!pullHoldArmed) {
            armPullHold(event)
            return
        }

        val movedSinceArm = hypot(event.x - pullHoldAnchorX, event.y - pullHoldAnchorY)
        if (movedSinceArm > minSwipeDistancePx * PULL_HOLD_SETTLE_RATIO) {
            armPullHold(event)
        }
    }

    private fun armPullHold(event: MotionEvent) {
        pullHoldArmed = true
        pullHoldAnchorX = event.x
        pullHoldAnchorY = event.y
        handler.removeCallbacks(pullHoldRunnable)
        handler.postDelayed(pullHoldRunnable, PULL_HOLD_TIMEOUT_MS)
    }

    private fun cancelPullHold() {
        pullHoldArmed = false
        handler.removeCallbacks(pullHoldRunnable)
    }

    private fun inwardVelocityPxPerSecond(event: MotionEvent): Float {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return 0f
        val elapsedMs = event.eventTime - previousMoveTimeMs
        if (elapsedMs <= 0L) return 0f
        val dx = event.x - previousMoveX
        val inwardDx = when (side) {
            EdgeSide.LEFT -> dx
            EdgeSide.RIGHT -> -dx
        }
        return inwardDx.coerceAtLeast(0f) * 1000f / elapsedMs
    }

    private fun reset() {
        state = State.IDLE
        pullHoldArmed = false
        committedGestureConsumed = false
        gestureProgressEmitted = false
        backCommitController.reset()
        cancelBackProgress()
        handler.removeCallbacks(pullHoldRunnable)
    }

    private companion object {
        const val PULL_HOLD_TIMEOUT_MS = 420L
        const val PULL_HOLD_SETTLE_RATIO = 0.35f
    }
}
