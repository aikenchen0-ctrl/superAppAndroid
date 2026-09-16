package com.paifa.univerge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.TouchInteractionController
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.paifa.univerge.core.gesture.BackGestureOption
import com.paifa.univerge.core.gesture.BackGestureProgress
import com.paifa.univerge.core.gesture.hitTestBackGestureOption
import com.paifa.univerge.core.gesture.runtime.BottomBarConfig
import com.paifa.univerge.core.gesture.runtime.BottomGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.ConfigSnapshot
import com.paifa.univerge.core.gesture.runtime.GestureSignal
import com.paifa.univerge.core.gesture.runtime.HotZoneSegment
import com.paifa.univerge.core.gesture.runtime.PointerSample
import com.paifa.univerge.core.gesture.runtime.SideGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.SideGestureThresholds
import com.paifa.univerge.core.gesture.runtime.BottomGestureThresholds
import com.paifa.univerge.core.gesture.runtime.configuredSideGestureThresholdsDp
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import kotlin.math.hypot
import kotlin.math.roundToInt
import java.util.concurrent.Executor

internal class NativeEdgeGestureController(
    private val service: AccessibilityService,
    private val onGesture: (EdgeSide, GestureType, GestureAction, GestureData) -> Unit,
    private val onGestureProgress: (EdgeSide, GestureAction, GestureData) -> Unit = { _, _, _ -> },
    private val onGestureEnd: () -> Unit = {},
    private val onBottomGesture: (BottomGestureBarGestureType, GestureAction, GestureData) -> Unit,
    private val onBackGestureProgress: (NativeTouchInterceptRect, BackGestureProgress) -> Unit,
    private val onBackGestureCommit: (NativeTouchInterceptRect, BackGestureProgress, GestureAction, GestureData) -> Boolean,
    private val onBackGestureEnd: (NativeTouchInterceptRect, BackGestureProgress) -> Unit,
    private val onBackGestureCancel: () -> Unit
) {
    private var controller: TouchInteractionController? = null
    private var callback: TouchInteractionController.Callback? = null
    private var config: NativeEdgeGestureConfig? = null
    private var floatingChatExpanded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
    private val previewData = GestureData(0f, 0f, 0f, 0f)
    private var coreBridge: NativeGestureCoreBridge? = null
    private var activeConfig: NativeEdgeGestureConfig? = null
    private val visualDispatch = NativeVisualDispatch<NativeVisualProgress>(
        post = { runnable -> mainHandler.post(runnable) },
        onProgress = ::renderVisualProgress,
        onTerminal = onGestureEnd
    )
    private val sessionAbort = NativeGestureSessionAbort(
        cancelCore = { coreBridge?.onCancel() },
        cancelBackProgress = ::cancelBackProgressIfNeeded,
        endPreview = ::postVisualTerminal,
        clearFields = ::resetGestureFields
    )
    private val bottomHoldPollRunnable = object : Runnable {
        override fun run() {
            dispatchNativeHoldTimer()
        }
    }
    /**
     * A directional bottom gesture may commit before Android delivers its
     * terminal event. Keep the transaction alive briefly so a delayed UP can
     * finish it, but reclaim it when the platform drops that terminal event.
     * Each runnable captures its session generation; an old queued runnable
     * can therefore never abort a newer touch transaction.
     */
    @Volatile
    private var bottomTerminalWatchdog: Runnable? = null

    var isRunning: Boolean = false
        private set

    val hasActiveGesture: Boolean
        get() = coreBridge?.hasActiveGesture == true

    fun start(config: NativeEdgeGestureConfig): Boolean {
        if (Build.VERSION.SDK_INT < NATIVE_TOUCH_INTERACTION_MIN_SDK) return false
        visualDispatch.resumeVisuals()
        val touchController = runCatching {
            service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
        }.onFailure {
            Log.w(TAG, "failed to get native touch interaction controller", it)
        }.getOrNull() ?: return false

        if (isRunning) {
            this.config = config
            coreBridge?.updateConfig(config)
            return applyNativeTouchPassthrough(config, floatingChatExpanded)
        }

        val newCallback = object : TouchInteractionController.Callback {
            override fun onMotionEvent(event: MotionEvent) {
                handleMotionEvent(touchController, event)
            }

            override fun onStateChanged(state: Int) {
                if (shouldAbortNativeSessionForState(
                        state = state,
                        sessionActive = nativeEventSessionActive || hasActiveGesture
                    )
                ) {
                    abortActiveSession()
                    nativeEventSessionActive = false
                }
            }
        }

        return runCatching {
            touchController.registerCallback(
                // The platform already serializes callbacks for the supplied
                // executor. Running the command directly avoids putting every
                // MOVE ahead of ACTION_UP on the service main looper.
                Executor { command -> command.run() },
                newCallback
            )
            controller = touchController
            callback = newCallback
            this.config = config
            coreBridge = NativeGestureCoreBridge(config)
            isRunning = true
            if (applyNativeTouchPassthrough(config, floatingChatExpanded)) {
                true
            } else {
                touchController.unregisterCallback(newCallback)
                controller = null
                callback = null
                this.config = null
                coreBridge = null
                isRunning = false
                false
            }
        }.onFailure {
            Log.w(TAG, "failed to register native touch callback", it)
        }.getOrDefault(false)
    }

    fun stop() {
        abortActiveSession()
        visualDispatch.clearPendingVisuals()
        val touchController = controller
        val registeredCallback = callback
        config?.let { currentConfig ->
            applyNativeTouchPassthrough(
                config = currentConfig,
                floatingChatExpanded = true,
                edgeGesturesEnabled = false
            )
        }
        if (touchController != null && registeredCallback != null) {
            runCatching { touchController.unregisterCallback(registeredCallback) }
                .onFailure { Log.w(TAG, "failed to unregister native touch callback", it) }
        }
        controller = null
        callback = null
        config = null
        coreBridge = null
        activeConfig = null
        floatingChatExpanded = false
        isRunning = false
        resetGestureFields()
        nativeEventSessionActive = false
    }

    /** Cancels only the current transaction while keeping the Native callback registered. */
    fun cancelActiveSession() {
        abortActiveSession()
        nativeEventSessionActive = false
    }

    fun setFloatingChatExpanded(expanded: Boolean) {
        if (floatingChatExpanded == expanded) return
        floatingChatExpanded = expanded
        config?.let { currentConfig ->
            applyNativeTouchPassthrough(currentConfig, expanded)
        }
    }

    private fun applyNativeTouchPassthrough(
        config: NativeEdgeGestureConfig,
        floatingChatExpanded: Boolean,
        edgeGesturesEnabled: Boolean = true
    ): Boolean {
        val passthroughRegion = Region(0, 0, config.screenWidthPx, config.screenHeightPx)
        if (edgeGesturesEnabled) {
            nativeTouchInterceptRects(config, floatingChatExpanded).forEach { intercept ->
                passthroughRegion.op(
                    Rect(intercept.left, intercept.top, intercept.right, intercept.bottom),
                    Region.Op.DIFFERENCE
                )
            }
        }
        nativeBottomGestureInterceptRect(config, floatingChatExpanded)?.let { intercept ->
            passthroughRegion.op(
                Rect(intercept.left, intercept.top, intercept.right, intercept.bottom),
                Region.Op.DIFFERENCE
            )
        }
        return runCatching {
            service.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY, passthroughRegion)
            service.setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, passthroughRegion)
            true
        }.onFailure { error ->
            Log.e(TAG, "failed to apply native touch passthrough region", error)
        }.getOrDefault(false)
    }

    @Synchronized
    private fun handleMotionEvent(
        touchController: TouchInteractionController,
        event: MotionEvent
    ) {
        traceNativeBoundary(
            action = event.actionMasked,
            eventTime = event.eventTime,
            controllerState = touchController.state
        )
        if (shouldIgnoreOutOfOrderNativeEvent(
                action = event.actionMasked,
                eventTime = event.eventTime,
                sessionStartTime = nativeSessionStartTime,
                lastAcceptedEventTime = nativeLastAcceptedEventTime,
                sessionActive = nativeEventSessionActive,
                sessionDownTime = nativeSessionDownTime,
                eventDownTime = event.downTime
            )
        ) {
            return
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // A lost UP/CANCEL must not leak the previous BackWave selection
            // into the next transaction. Abort before accepting the new DOWN.
            abortActiveSession()
            nativeSessionGeneration += 1L
            nativeSessionStartTime = event.eventTime
            nativeLastAcceptedEventTime = event.eventTime
            nativeSessionDownTime = event.downTime
            nativeEventSessionActive = true
        } else if (gestureDelegated) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                abortActiveSession()
                nativeEventSessionActive = false
            }
            return
        } else {
            nativeLastAcceptedEventTime = maxOf(nativeLastAcceptedEventTime, event.eventTime)
        }
        val currentConfig = config ?: run {
            requestDelegatingOnce(touchController)
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(touchController, currentConfig, event)
            MotionEvent.ACTION_MOVE -> handleMove(touchController, currentConfig, event)
            MotionEvent.ACTION_UP -> handleUp(touchController, currentConfig, event)
            MotionEvent.ACTION_CANCEL -> {
                abortActiveSession()
                nativeEventSessionActive = false
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                abortActiveSession()
                nativeEventSessionActive = false
            }
            else -> Unit
        }
    }

    private fun handleDown(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        val bridge = coreBridge ?: NativeGestureCoreBridge(config).also { coreBridge = it }
        val signal = bridge.onDown(
            xPx = event.x,
            yPx = event.y,
            timeMillis = event.eventTime,
            pointerId = event.getPointerId(0),
            pointerCount = event.pointerCount
        )
        if (signal === NativeCoreSignal.Ignored ||
            (signal is NativeCoreSignal.Bottom && floatingChatExpanded)
        ) {
            bridge.onCancel()
            requestDelegatingOnce(touchController)
            clearGestureTrackingFields()
            return
        }
        activeConfig = config
        startX = event.x
        startY = event.y
        latestX = event.x
        latestY = event.y
        activeSide = (signal as? NativeCoreSignal.Side)?.side
        activeIntercept = activeSide?.let { side ->
            nativeEdgeGestureInterceptAt(
                x = event.x,
                y = event.y,
                screenWidthPx = config.screenWidthPx,
                screenHeightPx = config.screenHeightPx,
                density = config.density,
                leftConfigs = if (side == EdgeSide.LEFT) config.leftConfigs else emptyList(),
                rightConfigs = if (side == EdgeSide.RIGHT) config.rightConfigs else emptyList()
            )
        }
        activeBottomGesture = signal is NativeCoreSignal.Bottom
        consumingGesture = activeBottomGesture
        bottomCommitGestureId = 0L
        if (activeBottomGesture) scheduleNativeHoldPoll()
        latestBackProgress = null
        sentBackCancel = false
    }

    private fun handleMove(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        val signal = coreBridge?.onMove(
            xPx = event.x,
            yPx = event.y,
            timeMillis = event.eventTime,
            pointerId = event.getPointerId(0),
            pointerCount = event.pointerCount
        ) ?: NativeCoreSignal.Ignored
        if (signal === NativeCoreSignal.Ignored) {
            val distance = hypot(event.x - startX, event.y - startY)
            if (shouldDelegateNativeIgnoredMove(
                    activeSide = activeSide,
                    activeBottomGesture = activeBottomGesture,
                    consumingGesture = consumingGesture,
                    distancePx = distance,
                    touchSlopPx = touchSlopPx,
                    gestureThresholdPx = config.shortThresholdPx
                )
            ) {
                coreBridge?.onCancel()
                requestDelegatingOnce(touchController)
                clearGestureTrackingFields()
            } else if (activeSide == null && !activeBottomGesture) {
                requestDelegatingOnce(touchController)
            }
            return
        }
        latestX = event.x
        latestY = event.y
        if (signal is NativeCoreSignal.Bottom) {
            when (signal.signal) {
                is GestureSignal.Preview -> consumingGesture = true
                is GestureSignal.Commit -> {
                    consumingGesture = true
                    stopNativeHoldPoll()
                    dispatchBottomCommit(signal, config, fromMove = true)
                }
                is GestureSignal.Cancel -> {
                    stopNativeHoldPoll()
                    abortActiveSession()
                }
                else -> Unit
            }
            return
        }
        signal as NativeCoreSignal.Side
        val side = signal.side
        val intercept = activeIntercept ?: run {
            coreBridge?.onCancel()
            requestDelegatingOnce(touchController)
            clearGestureTrackingFields()
            return
        }
        val active = activeConfig ?: config
        updatePreviewData(startX, startY, latestX, latestY)
        val previewAction = (signal.signal as? GestureSignal.Preview)?.action ?: GestureAction.None
        val dx = latestX - startX
        val dy = latestY - startY
        val distance = hypot(dx, dy)
        val backProgress = backProgressFor(
            side = side,
            config = active,
            touchX = latestX,
            touchY = latestY
        )
        var backProgressCancelled = false

        if (backProgress != null) {
            latestBackProgress = backProgress
            sentBackCancel = false
            if (distance > touchSlopPx || consumingGesture) {
                consumingGesture = true
            }
        } else if (latestBackProgress != null && !sentBackCancel) {
            latestBackProgress = null
            sentBackCancel = true
            visualBackCancelPending = true
            backProgressCancelled = true
        }
        postCoalescedVisualProgress(
            NativeVisualProgress(
                side = side,
                previewAction = previewAction,
                data = previewData.copy(),
                intercept = intercept,
                backProgress = backProgress,
                backProgressCancelled = backProgressCancelled
            )
        )
        when (signal.signal) {
            is GestureSignal.Preview -> {
                consumingGesture = true
            }
            is GestureSignal.Cancel -> {
                abortActiveSession()
                requestDelegatingOnce(touchController)
                return
            }
            else -> Unit
        }
        if (!consumingGesture && distance > touchSlopPx) {
            coreBridge?.onCancel()
            requestDelegatingOnce(touchController)
            clearGestureTrackingFields()
            return
        }
        if (!consumingGesture && signal.signal is GestureSignal.Preview) {
            consumingGesture = true
        }
    }

    private fun handleUp(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        stopNativeHoldPoll()
        stopBottomTerminalWatchdog()
        // onUp() finishes the core transaction, so capture its id before the
        // recognizer clears the active session. The visual queue uses this id
        // to coalesce only duplicate terminals from the same gesture.
        val terminalGestureId = coreBridge?.activeGestureId ?: 0L
        val signal = coreBridge?.onUp(
            xPx = event.x,
            yPx = event.y,
            timeMillis = event.eventTime,
            pointerId = event.getPointerId(0),
            pointerCount = event.pointerCount
        ) ?: NativeCoreSignal.Ignored
        latestX = event.x
        latestY = event.y
        if (signal is NativeCoreSignal.Bottom) {
            val commit = signal.signal as? GestureSignal.Commit
            if (commit != null) {
                dispatchBottomCommit(signal, config, fromMove = false)
            }
            postVisualTerminal(gestureKey = terminalGestureId)
            resetGestureFields()
            nativeEventSessionActive = false
            return
        }
        if (signal !is NativeCoreSignal.Side) {
            requestDelegatingOnce(touchController)
            postVisualTerminal(gestureKey = terminalGestureId)
            resetGestureFields()
            nativeEventSessionActive = false
            return
        }
        val side = signal.side
        val intercept = activeIntercept
        val active = activeConfig ?: config
        val commit = signal.signal as? GestureSignal.Commit
        val data = commit?.data?.toPx(active.density)
            ?: GestureData(startX, startY, latestX, latestY)
        val finalBackProgress = backProgressFor(
            side = side,
            config = active,
            touchX = latestX,
            touchY = latestY
        ) ?: latestBackProgress?.copy(selectedOption = BackGestureOption.None)
        val visualBackEnd = if (commit != null && finalBackProgress != null && intercept != null) {
            { onBackGestureEnd(intercept, finalBackProgress) }
        } else {
            null
        }

        if (commit != null && finalBackProgress != null && intercept != null) {
            if (!onBackGestureCommit(intercept, finalBackProgress, commit.action, data)) {
                onGesture(side, commit.gesture, commit.action, data)
            }
        } else if (commit != null) {
            onGesture(side, commit.gesture, commit.action, data)
        } else {
            cancelBackProgressIfNeeded()
            requestDelegatingOnce(touchController)
        }
        // The terminal action has crossed its action boundary before visual
        // cleanup is posted. WindowManager work cannot delay ACTION_UP.
        postVisualTerminal(
            gestureKey = terminalGestureId,
            afterBackEnd = visualBackEnd
        )
        resetGestureFields()
        nativeEventSessionActive = false
    }

    private fun backProgressFor(
        side: EdgeSide,
        config: NativeEdgeGestureConfig,
        touchX: Float,
        touchY: Float
    ): BackGestureProgress? {
        val dx = touchX - startX
        val dy = touchY - startY
        val baseProgress = BackGestureProgress.fromDelta(
            side = side,
            dx = dx,
            dy = dy,
            thresholdPx = config.shortThresholdPx,
            longThresholdPx = config.longThresholdPx,
            touchY = touchY,
            startY = startY,
            minimumDragDistancePx = 0f
        ) ?: return null
        return baseProgress.copy(
            selectedOption = hitTestBackGestureOption(
                side = side,
                startX = startX,
                startY = startY,
                touchX = touchX,
                touchY = touchY,
                progress = baseProgress.progress,
                density = config.density,
                viewportHeightPx = config.screenHeightPx.toFloat()
            )
        )
    }

    private fun requestDelegatingOnce(touchController: TouchInteractionController) {
        val platformIsDelegating = touchController.state == TouchInteractionController.STATE_DELEGATING
        if (!shouldRequestNativeTouchDelegation(gestureDelegated, platformIsDelegating)) {
            gestureDelegated = true
            return
        }
        traceDelegationRequest(touchController.state)
        var requestSucceeded = false
        try {
            touchController.requestDelegating()
            requestSucceeded = true
        } catch (error: IllegalStateException) {
            Log.e(TAG, "native touch delegation rejected state=${touchController.state}", error)
        } finally {
            gestureDelegated = shouldMarkNativeTouchDelegated(
                requestSucceeded = requestSucceeded,
                platformIsDelegating = touchController.state == TouchInteractionController.STATE_DELEGATING
            )
        }
    }

    /** Enqueue only the newest visual state; recognition and terminal dispatch stay synchronous. */
    private fun postCoalescedVisualProgress(progress: NativeVisualProgress) {
        visualDispatch.postCoalescedVisualProgress(progress)
    }

    private fun renderVisualProgress(progress: NativeVisualProgress) {
        onGestureProgress(progress.side, progress.previewAction, progress.data)
        if (progress.backProgressCancelled) {
            onBackGestureCancel()
            visualBackCancelPending = false
        }
        progress.backProgress?.let { backProgress ->
            onBackGestureProgress(progress.intercept, backProgress)
        }
    }

    /**
     * Clear pending MOVE visuals, then run terminal visual cleanup on the main
     * handler. The callback is captured before gesture fields are reset.
     */
    private fun postVisualTerminal(afterBackEnd: (() -> Unit)? = null) {
        val activeGestureKey = coreBridge?.activeGestureId ?: 0L
        postVisualTerminal(
            gestureKey = activeGestureKey.takeIf { it > 0L } ?: bottomCommitGestureId,
            afterBackEnd = afterBackEnd
        )
    }

    private fun postVisualTerminal(
        gestureKey: Long,
        afterBackEnd: (() -> Unit)? = null
    ) {
        val cancelBack = shouldCancelBackProgressAtVisualTerminal(
            hasBackEnd = afterBackEnd != null,
            cancellationPending = visualBackCancelPending,
            backProgressActive = latestBackProgress != null && !sentBackCancel
        )
        visualBackCancelPending = false
        if (cancelBack) sentBackCancel = true
        visualDispatch.postVisualTerminal(gestureKey = gestureKey) {
            if (cancelBack) onBackGestureCancel()
            afterBackEnd?.invoke()
            onGestureEnd()
        }
    }

    private fun cancelBackProgressIfNeeded() {
        if (latestBackProgress == null || sentBackCancel) return
        sentBackCancel = true
        visualBackCancelPending = true
    }

    private fun abortActiveSession(): Boolean = sessionAbort.abort(
        coreActive = coreBridge?.hasActiveGesture == true,
        backProgressActive = latestBackProgress != null,
        previewActive = activeSide != null || activeBottomGesture || gestureDelegated
    )

    private fun traceNativeBoundary(
        action: Int,
        eventTime: Long,
        controllerState: Int
    ) {
        if (!BuildConfig.DEBUG) return
        if (
            action != MotionEvent.ACTION_DOWN &&
            action != MotionEvent.ACTION_UP &&
            action != MotionEvent.ACTION_CANCEL
        ) return
        Log.d(
            TAG,
            "[DEBUG-native-event] action=$action eventTime=$eventTime " +
                "callbackUptime=${android.os.SystemClock.uptimeMillis()} " +
                "state=$controllerState gestureId=${coreBridge?.activeGestureId ?: 0L} " +
                "delegated=$gestureDelegated"
        )
    }

    private fun traceDelegationRequest(state: Int) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "[DEBUG-native-delegation] request state=$state " +
                "gestureId=${coreBridge?.activeGestureId ?: 0L} " +
                "uptime=${android.os.SystemClock.uptimeMillis()}"
        )
    }

    private fun resetGestureFields() {
        stopNativeHoldPoll()
        stopBottomTerminalWatchdog()
        clearGestureTrackingFields()
        gestureDelegated = false
    }

    private fun scheduleNativeHoldPoll() {
        mainHandler.removeCallbacks(bottomHoldPollRunnable)
        mainHandler.postDelayed(bottomHoldPollRunnable, NATIVE_HOLD_POLL_INTERVAL_MS)
    }

    private fun stopNativeHoldPoll() {
        mainHandler.removeCallbacks(bottomHoldPollRunnable)
    }

    private fun scheduleBottomTerminalWatchdog() {
        stopBottomTerminalWatchdog()
        val generation = nativeSessionGeneration
        val watchdog = Runnable {
            dispatchBottomTerminalWatchdog(generation)
        }
        bottomTerminalWatchdog = watchdog
        mainHandler.postDelayed(watchdog, NATIVE_TERMINAL_WATCHDOG_TIMEOUT_MS)
    }

    private fun stopBottomTerminalWatchdog() {
        bottomTerminalWatchdog?.let(mainHandler::removeCallbacks)
        bottomTerminalWatchdog = null
    }

    @Synchronized
    private fun dispatchBottomTerminalWatchdog(generation: Long) {
        if (!shouldRunNativeBottomTerminalWatchdog(
                watchdogGeneration = generation,
                currentGeneration = nativeSessionGeneration,
                sessionActive = nativeEventSessionActive,
                bottomGestureActive = activeBottomGesture
            )
        ) {
            return
        }
        bottomTerminalWatchdog = null
        // The action already crossed the boundary at MOVE/hold time. This
        // timeout only performs the missing terminal cleanup; it never emits
        // the bottom action a second time.
        abortActiveSession()
        nativeEventSessionActive = false
    }

    @Synchronized
    private fun dispatchNativeHoldTimer() {
        if (!nativeEventSessionActive || !activeBottomGesture) return
        val currentConfig = activeConfig ?: config ?: return
        val signal = coreBridge?.onHoldTimer(SystemClock.uptimeMillis())
        if (signal is NativeCoreSignal.Bottom) {
            if (signal.signal is GestureSignal.Commit) {
                dispatchBottomCommit(signal, currentConfig, fromMove = true)
                stopNativeHoldPoll()
                return
            }
        }
        if (nativeEventSessionActive && activeBottomGesture) {
            scheduleNativeHoldPoll()
        }
    }

    private fun dispatchBottomCommit(
        signal: NativeCoreSignal.Bottom,
        config: NativeEdgeGestureConfig,
        fromMove: Boolean
    ): Boolean {
        val commit = signal.signal as? GestureSignal.Commit ?: return false
        if (commit.gestureId <= 0L || commit.gestureId == bottomCommitGestureId) return false
        val type = bottomGestureTypeFor(commit.gesture) ?: return false
        if (fromMove && !shouldDispatchBottomGestureDuringMove(type)) return false
        bottomCommitGestureId = commit.gestureId
        onBottomGesture(
            type,
            commit.action,
            commit.data.toPx(activeConfig?.density ?: config.density)
        )
        if (fromMove) scheduleBottomTerminalWatchdog()
        return true
    }

    private fun clearGestureTrackingFields() {
        activeSide = null
        activeIntercept = null
        activeConfig = null
        startX = 0f
        startY = 0f
        latestX = 0f
        latestY = 0f
        consumingGesture = false
        activeBottomGesture = false
        bottomCommitGestureId = 0L
        latestBackProgress = null
        sentBackCancel = false
        visualBackCancelPending = false
    }

    private fun updatePreviewData(startX: Float, startY: Float, endX: Float, endY: Float) {
        previewData.startX = startX
        previewData.startY = startY
        previewData.endX = endX
        previewData.endY = endY
    }

    private var activeSide: EdgeSide? = null
    private var activeIntercept: NativeTouchInterceptRect? = null
    private var startX = 0f
    private var startY = 0f
    private var latestX = 0f
    private var latestY = 0f
    private var consumingGesture = false
    private var activeBottomGesture = false
    private var bottomCommitGestureId = 0L
    private var latestBackProgress: BackGestureProgress? = null
    private var sentBackCancel = false
    @Volatile
    private var visualBackCancelPending = false
    private var gestureDelegated = false
    private var nativeSessionStartTime = Long.MIN_VALUE
    private var nativeLastAcceptedEventTime = Long.MIN_VALUE
    private var nativeSessionDownTime = Long.MIN_VALUE
    private var nativeSessionGeneration = 0L
    private var nativeEventSessionActive = false

    private companion object {
        const val TAG = "NativeEdgeGesture"
        const val NATIVE_HOLD_POLL_INTERVAL_MS = 50L
        const val NATIVE_TERMINAL_WATCHDOG_TIMEOUT_MS = 1_500L
    }
}

internal fun shouldCancelBackProgressAtVisualTerminal(
    hasBackEnd: Boolean,
    cancellationPending: Boolean,
    backProgressActive: Boolean
): Boolean {
    if (hasBackEnd) return false
    return cancellationPending || backProgressActive
}

private data class NativeVisualProgress(
    val side: EdgeSide,
    val previewAction: GestureAction,
    val data: GestureData,
    val intercept: NativeTouchInterceptRect,
    val backProgress: BackGestureProgress?,
    val backProgressCancelled: Boolean
)

/**
 * Main-thread visual boundary for native input. MOVE frames are latest-value
 * state, while a terminal event invalidates any frame that has not run yet.
 */
internal class NativeVisualDispatch<T>(
    private val post: ((() -> Unit) -> Unit),
    private val onProgress: (T) -> Unit,
    private val onTerminal: () -> Unit = {}
) {
    private val lock = Any()
    private var generation = 0L
    private var acceptingVisuals = true
    private var pendingProgress: PendingProgress<T>? = null
    private var scheduledProgressGeneration: Long? = null
    private val pendingTerminals = java.util.ArrayDeque<PendingTerminal>()

    fun resumeVisuals() {
        synchronized(lock) {
            acceptingVisuals = true
        }
    }

    /** Invalidates work already posted to the main thread without removing its Runnable. */
    fun clearPendingVisuals() {
        synchronized(lock) {
            acceptingVisuals = false
            generation += 1L
            pendingProgress = null
            scheduledProgressGeneration = null
            pendingTerminals.clear()
        }
    }

    fun postCoalescedVisualProgress(progress: T) {
        val generationToSchedule = synchronized(lock) {
            if (!acceptingVisuals) {
                null
            } else {
                // A new frame belongs to the current generation. Keep a queued
                // terminal callback intact: Handler FIFO must finish the previous
                // gesture before this frame is rendered.
                pendingProgress = PendingProgress(generation, progress)
                if (scheduledProgressGeneration == generation) {
                    null
                } else {
                    scheduledProgressGeneration = generation
                    generation
                }
            }
        }
        generationToSchedule?.let { value -> post { consumeProgress(value) } }
    }

    fun postVisualTerminal(gestureKey: Long = 0L, after: (() -> Unit)? = null) {
        var scheduleTerminal = false
        synchronized(lock) {
            if (acceptingVisuals) {
                generation += 1L
                pendingProgress = null
                val callback = after ?: onTerminal
                // Repeated terminal notifications for one gesture are noise, but
                // terminals from different gestures must remain ordered.
                if (pendingTerminals.none { it.gestureKey == gestureKey }) {
                    pendingTerminals.addLast(PendingTerminal(gestureKey, callback))
                    // Each distinct terminal owns one FIFO main-thread task. This
                    // keeps a second gesture from waiting on an implicit reschedule
                    // that a test executor (or a saturated Handler) may not drain.
                    scheduleTerminal = true
                }
            }
        }
        if (scheduleTerminal) post { consumeNextTerminal() }
    }

    private fun consumeProgress(generationValue: Long) {
        val frame: T?
        synchronized(lock) {
            if (scheduledProgressGeneration == generationValue) {
                scheduledProgressGeneration = null
            }
            val pending = pendingProgress
            frame = if (
                acceptingVisuals &&
                generation == generationValue &&
                pending?.generation == generationValue
            ) {
                pendingProgress = null
                pending.value
            } else {
                null
            }
        }
        frame?.let(onProgress)

        // A producer may replace the frame while rendering. Schedule only one
        // bounded follow-up for the newer state.
        val followUpGeneration: Long?
        synchronized(lock) {
            val pending = pendingProgress
            followUpGeneration = if (
                pending != null &&
                pending.generation == generation &&
                scheduledProgressGeneration == null
            ) {
                scheduledProgressGeneration = generation
                generation
            } else {
                null
            }
        }
        followUpGeneration?.let { value -> post { consumeProgress(value) } }
    }

    private fun consumeNextTerminal() {
        val terminal: (() -> Unit)?
        synchronized(lock) {
            terminal = if (acceptingVisuals) {
                pendingTerminals.pollFirst()?.callback
            } else {
                null
            }
        }
        if (terminal == null) return

        terminal.invoke()
    }

    private data class PendingTerminal(
        val gestureKey: Long,
        val callback: () -> Unit
    )

    private data class PendingProgress<T>(
        val generation: Long,
        val value: T
    )

}

internal fun shouldDispatchBottomGestureDuringMove(gestureType: BottomGestureBarGestureType): Boolean {
    return gestureType != BottomGestureBarGestureType.Tap
}

internal fun shouldRunNativeBottomTerminalWatchdog(
    watchdogGeneration: Long,
    currentGeneration: Long,
    sessionActive: Boolean,
    bottomGestureActive: Boolean
): Boolean = watchdogGeneration == currentGeneration && sessionActive && bottomGestureActive

internal fun shouldRequestNativeTouchDelegation(
    alreadyDelegated: Boolean,
    platformIsDelegating: Boolean
): Boolean {
    return !alreadyDelegated && !platformIsDelegating
}

internal fun shouldDelegateNativeIgnoredMove(
    activeSide: EdgeSide?,
    activeBottomGesture: Boolean,
    consumingGesture: Boolean,
    distancePx: Float,
    touchSlopPx: Float,
    gestureThresholdPx: Float
): Boolean {
    return (activeSide == null || distancePx > gestureThresholdPx) &&
        !activeBottomGesture &&
        !consumingGesture &&
        distancePx > touchSlopPx
}

internal fun shouldIgnoreOutOfOrderNativeEvent(
    action: Int,
    eventTime: Long,
    sessionStartTime: Long,
    lastAcceptedEventTime: Long,
    sessionActive: Boolean = true,
    sessionDownTime: Long = Long.MIN_VALUE,
    eventDownTime: Long = Long.MIN_VALUE
): Boolean {
    if (action == MotionEvent.ACTION_DOWN) return false
    if (!sessionActive || sessionStartTime == Long.MIN_VALUE) return true
    if (
        sessionDownTime != Long.MIN_VALUE &&
        eventDownTime != Long.MIN_VALUE &&
        eventDownTime != sessionDownTime
    ) return true
    return eventTime < sessionStartTime || eventTime < lastAcceptedEventTime
}

internal fun shouldAbortNativeSessionForState(state: Int, sessionActive: Boolean): Boolean {
    return sessionActive && (
        state == TouchInteractionController.STATE_CLEAR ||
            state == TouchInteractionController.STATE_DELEGATING
        )
}

internal fun shouldMarkNativeTouchDelegated(
    requestSucceeded: Boolean,
    platformIsDelegating: Boolean
): Boolean = requestSucceeded || platformIsDelegating

internal data class NativeEdgeGestureConfig(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val density: Float,
    val leftConfigs: List<EdgeZoneConfig>,
    val rightConfigs: List<EdgeZoneConfig>,
    val shortThresholdPx: Float,
    val longThresholdPx: Float,
    val bottomGestureWidthDp: Int = defaultBottomGestureBarWidthDp(),
    val snapshotVersion: Long = 1L,
    val sideActions: Map<EdgeSide, Map<GestureType, GestureAction>> = emptyMap(),
    val bottomActions: Map<GestureType, GestureAction> = emptyMap()
)

internal sealed interface NativeCoreSignal {
    data object Ignored : NativeCoreSignal
    data class Side(val side: EdgeSide, val signal: GestureSignal) : NativeCoreSignal
    data class Bottom(val signal: GestureSignal) : NativeCoreSignal
}

internal fun bottomGestureTypeFor(gestureType: GestureType): BottomGestureBarGestureType? = when (gestureType) {
    GestureType.TAP -> BottomGestureBarGestureType.Tap
    GestureType.SWIPE_UP -> BottomGestureBarGestureType.SwipeUp
    GestureType.SWIPE_UP_HOLD -> BottomGestureBarGestureType.SwipeUpHold
    GestureType.SWIPE_LEFT,
    GestureType.SWIPE_RIGHT -> BottomGestureBarGestureType.SwipeHorizontal
    GestureType.LONG_PRESS -> BottomGestureBarGestureType.LongPress
    else -> null
}

private fun GestureData.toPx(density: Float): GestureData = GestureData(
    startX = startX * density,
    startY = startY * density,
    endX = endX * density,
    endY = endY * density,
    gestureId = gestureId,
    snapshotVersion = snapshotVersion,
    zoneId = zoneId
)

/**
 * Platform-free bridge owned by the native input adapter. It converts px events to
 * core samples and keeps the active configuration immutable until the transaction ends.
 */
internal class NativeGestureCoreBridge(initialConfig: NativeEdgeGestureConfig) {
    private var config = initialConfig
    private var pendingConfig: NativeEdgeGestureConfig? = null
    private var left = emptySideRecognizer(initialConfig, EdgeSide.LEFT)
    private var right = emptySideRecognizer(initialConfig, EdgeSide.RIGHT)
    private var bottom = emptyBottomRecognizer(initialConfig)
    private var activeSide: EdgeSide? = null
    private var activeBottom = false

    val hasActiveGesture: Boolean
        get() = activeSide != null || activeBottom

    val activeGestureId: Long
        get() = when {
            activeSide != null -> recognizer(activeSide!!).activeGestureId
            activeBottom -> bottom.activeGestureId
            else -> 0L
        }

    init {
        install(initialConfig)
    }

    fun updateConfig(next: NativeEdgeGestureConfig) {
        if (activeSide != null || activeBottom) {
            pendingConfig = next
        } else {
            install(next)
        }
    }

    fun onDown(xPx: Float, yPx: Float, timeMillis: Long, pointerId: Int = 0, pointerCount: Int = 1): NativeCoreSignal {
        if (activeSide != null || activeBottom) return NativeCoreSignal.Ignored
        val sample = sample(xPx, yPx, timeMillis, pointerId, pointerCount)
        val side = when {
            left.hitTest(sample.xDp, sample.yDp) -> EdgeSide.LEFT
            right.hitTest(sample.xDp, sample.yDp) -> EdgeSide.RIGHT
            else -> null
        }
        if (side != null) {
            activeSide = side
            val signal = recognizer(side).onDown(sample)
            return NativeCoreSignal.Side(side, signal)
        }
        if (bottom.hitTest(sample.xDp, sample.yDp)) {
            activeBottom = true
            return NativeCoreSignal.Bottom(bottom.onDown(sample))
        }
        return NativeCoreSignal.Ignored
    }

    fun onMove(xPx: Float, yPx: Float, timeMillis: Long, pointerId: Int = 0, pointerCount: Int = 1): NativeCoreSignal {
        val sample = sample(xPx, yPx, timeMillis, pointerId, pointerCount)
        val side = activeSide
        if (side != null) return NativeCoreSignal.Side(side, recognizer(side).onMove(sample))
        if (activeBottom) return NativeCoreSignal.Bottom(bottom.onMove(sample))
        return NativeCoreSignal.Ignored
    }

    fun onHoldTimer(timeMillis: Long): NativeCoreSignal {
        val side = activeSide
        if (side != null) return NativeCoreSignal.Side(side, recognizer(side).onHoldTimer(timeMillis))
        if (activeBottom) return NativeCoreSignal.Bottom(bottom.onHoldTimer(timeMillis))
        return NativeCoreSignal.Ignored
    }

    fun onUp(xPx: Float, yPx: Float, timeMillis: Long, pointerId: Int = 0, pointerCount: Int = 1): NativeCoreSignal {
        val sample = sample(xPx, yPx, timeMillis, pointerId, pointerCount)
        val side = activeSide
        if (side != null) {
            val signal = recognizer(side).onUp(sample)
            finish()
            return NativeCoreSignal.Side(side, signal)
        }
        if (activeBottom) {
            val signal = bottom.onUp(sample)
            finish()
            return NativeCoreSignal.Bottom(signal)
        }
        return NativeCoreSignal.Ignored
    }

    fun onCancel(): NativeCoreSignal {
        val side = activeSide
        if (side != null) {
            val signal = recognizer(side).onCancel()
            finish()
            return NativeCoreSignal.Side(side, signal)
        }
        if (activeBottom) {
            val signal = bottom.onCancel()
            finish()
            return NativeCoreSignal.Bottom(signal)
        }
        return NativeCoreSignal.Ignored
    }

    fun onFocusLost(): NativeCoreSignal {
        val side = activeSide
        if (side != null) {
            val signal = recognizer(side).onFocusLost()
            finish()
            return NativeCoreSignal.Side(side, signal)
        }
        if (activeBottom) {
            val signal = bottom.onFocusLost()
            finish()
            return NativeCoreSignal.Bottom(signal)
        }
        return NativeCoreSignal.Ignored
    }

    private fun install(next: NativeEdgeGestureConfig) {
        config = next
        pendingConfig = null
        val snapshot = next.toCoreSnapshot()
        left = SideGestureRecognizer(snapshot, EdgeSide.LEFT, next.screenWidthPx / next.density, next.screenHeightPx / next.density, sideThresholds(next))
        right = SideGestureRecognizer(snapshot, EdgeSide.RIGHT, next.screenWidthPx / next.density, next.screenHeightPx / next.density, sideThresholds(next))
        bottom = BottomGestureRecognizer(snapshot, bottomThresholds(next))
    }

    private fun finish() {
        activeSide = null
        activeBottom = false
        pendingConfig?.let(::install)
    }

    private fun recognizer(side: EdgeSide): SideGestureRecognizer = if (side == EdgeSide.LEFT) left else right

    private fun sample(xPx: Float, yPx: Float, timeMillis: Long, pointerId: Int, pointerCount: Int): PointerSample =
        PointerSample(
            xDp = xPx / config.density,
            yDp = yPx / config.density,
            timeMillis = timeMillis,
            pointerId = pointerId,
            pointerCount = pointerCount
        )

    private companion object {
        fun NativeEdgeGestureConfig.toCoreSnapshot(): ConfigSnapshot {
            val density = density.takeIf { it.isFinite() && it > 0f } ?: 1f
            val screenWidthDp = screenWidthPx.coerceAtLeast(0) / density
            val screenHeightDp = screenHeightPx.coerceAtLeast(0) / density
            fun zone(config: EdgeZoneConfig): HotZoneSegment {
                val value = config.sanitized()
                return HotZoneSegment(
                    startDp = screenHeightDp * value.topInsetPercent / 100f,
                    lengthDp = screenHeightDp * (100 - value.topInsetPercent - value.bottomInsetPercent) / 100f,
                    // Keep Core's hit width identical to the platform capture
                    // width. Otherwise a 1dp config is captured by Native's
                    // 24dp start target but rejected by Core on most points.
                    thicknessDp = nativeTouchInteractionEdgeStartTargetDp(value.thicknessDp).toFloat(),
                    enabled = value.enabled,
                    zoneId = value.zoneId,
                    edgeInsetDp = value.edgeInsetDp.toFloat()
                )
            }
            return ConfigSnapshot(
                sideZones = mapOf(
                    EdgeSide.LEFT to leftConfigs.map(::zone),
                    EdgeSide.RIGHT to rightConfigs.map(::zone)
                ),
                bottomBar = BottomBarConfig(
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                    widthDp = bottomGestureWidthDp.toFloat(),
                    heightDp = bottomGestureBarTouchHeightDp().toFloat()
                ),
                sideActions = sideActions,
                bottomActions = bottomActions,
                revision = snapshotVersion.coerceAtLeast(1L),
                density = density
            )
        }

        fun emptySideRecognizer(config: NativeEdgeGestureConfig, side: EdgeSide): SideGestureRecognizer =
            SideGestureRecognizer(
                side = side,
                screenWidthDp = config.screenWidthPx / config.density,
                screenHeightDp = config.screenHeightPx / config.density,
                zones = emptyList(),
                actions = emptyMap()
            )

        fun emptyBottomRecognizer(config: NativeEdgeGestureConfig): BottomGestureRecognizer =
            BottomGestureRecognizer(
                bar = BottomBarConfig(
                    config.screenWidthPx / config.density,
                    config.screenHeightPx / config.density,
                    config.bottomGestureWidthDp.toFloat(),
                    bottomGestureBarTouchHeightDp().toFloat()
                ),
                actions = emptyMap()
            )

        fun sideThresholds(config: NativeEdgeGestureConfig): SideGestureThresholds {
            val density = config.density.coerceAtLeast(0.01f)
            return SideGestureThresholds(
                minPullDistanceDp = config.shortThresholdPx / density,
                longPullDistanceDp = config.longThresholdPx / density,
                minSwipeDistanceDp = config.shortThresholdPx / density
            )
        }

        fun bottomThresholds(config: NativeEdgeGestureConfig): BottomGestureThresholds {
            val density = config.density.coerceAtLeast(0.01f)
            return BottomGestureThresholds(
                minSwipeDistanceDp = 56f / density,
                upwardHoldDurationMs = 500L
            )
        }
    }
}

internal data class NativeEdgeGestureHit(
    val side: EdgeSide,
    val zoneId: Int
)

internal data class NativeTouchInterceptRect(
    val side: EdgeSide,
    val zoneId: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun backWaveAnchorForNativeIntercept(
    intercept: NativeTouchInterceptRect,
    screenWidthPx: Int
): BackWaveAnchor {
    return physicalEdgeBackWaveAnchor(
        side = intercept.side,
        screenWidthPx = screenWidthPx,
        y = intercept.top,
        height = (intercept.bottom - intercept.top).coerceAtLeast(1)
    )
}

internal fun nativeTouchInterceptRects(
    config: NativeEdgeGestureConfig,
    floatingChatExpanded: Boolean
): List<NativeTouchInterceptRect> {
    return edgeGestureInterceptRects(
        screenWidthPx = config.screenWidthPx,
        screenHeightPx = config.screenHeightPx,
        density = config.density,
        configs = config.leftConfigs + config.rightConfigs
    )
}

internal fun nativeEdgeGestureHitTest(
    x: Float,
    y: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    leftConfigs: List<EdgeZoneConfig>,
    rightConfigs: List<EdgeZoneConfig>
): NativeEdgeGestureHit? {
    return nativeEdgeGestureInterceptAt(
        x = x,
        y = y,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        density = density,
        leftConfigs = leftConfigs,
        rightConfigs = rightConfigs
    )?.let { rect ->
        NativeEdgeGestureHit(side = rect.side, zoneId = rect.zoneId)
    }
}

internal fun nativeEdgeGestureInterceptAt(
    x: Float,
    y: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    leftConfigs: List<EdgeZoneConfig>,
    rightConfigs: List<EdgeZoneConfig>
): NativeTouchInterceptRect? {
    if (screenWidthPx <= 0 || screenHeightPx <= 0 || density <= 0f) return null
    return edgeGestureInterceptRects(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        density = density,
        configs = leftConfigs + rightConfigs
    ).firstOrNull { rect ->
        x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }
}

internal fun edgeGestureInterceptRects(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    configs: List<EdgeZoneConfig>,
    fixedTouchTargetDp: Int? = null
): List<NativeTouchInterceptRect> {
    if (
        screenWidthPx <= 0 ||
        screenHeightPx <= 0 ||
        density <= 0f ||
        (fixedTouchTargetDp != null && fixedTouchTargetDp <= 0)
    ) {
        return emptyList()
    }
    return configs.mapNotNull { config ->
        val sanitized = config.sanitized()
        if (!sanitized.enabled) return@mapNotNull null
        val touchTargetDp = fixedTouchTargetDp
            ?: nativeTouchInteractionEdgeStartTargetDp(sanitized.thicknessDp)
        val widthPx = (touchTargetDp * density)
            .roundToInt()
            .coerceIn(1, screenWidthPx)
        val insetPx = (sanitized.edgeInsetDp * density)
            .roundToInt()
            .coerceIn(0, (screenWidthPx - 1).coerceAtLeast(0))
        val boundedWidthPx = widthPx.coerceIn(1, (screenWidthPx - insetPx).coerceAtLeast(1))
        val heightPercent = 100 - sanitized.topInsetPercent - sanitized.bottomInsetPercent
        val zoneHeight = (screenHeightPx * heightPercent.coerceIn(EdgeZoneConfig.MIN_LENGTH_PERCENT, 100) / 100)
            .coerceAtLeast(1)
        val zoneTop = (screenHeightPx * sanitized.topInsetPercent / 100)
            .coerceIn(0, screenHeightPx - zoneHeight)
        val zoneBottom = zoneTop + zoneHeight
        val left = when (sanitized.side) {
            EdgeSide.LEFT -> insetPx
            EdgeSide.RIGHT -> screenWidthPx - insetPx - boundedWidthPx
        }
        NativeTouchInterceptRect(
            side = sanitized.side,
            zoneId = sanitized.zoneId,
            left = left,
            top = zoneTop,
            right = left + boundedWidthPx,
            bottom = zoneBottom
        )
    }
}

internal fun nativeGestureThresholdPx(thresholdDp: Int, density: Float): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return configuredSideGestureThresholdsDp(
        shortPullDistanceDp = thresholdDp.toFloat(),
        longPullDistanceDp = thresholdDp.toFloat()
    ).minPullDistanceDp * safeDensity
}

internal fun nativeGestureLongThresholdPx(
    shortThresholdDp: Int,
    longThresholdDp: Int,
    density: Float
): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return configuredSideGestureThresholdsDp(
        shortPullDistanceDp = shortThresholdDp.toFloat(),
        longPullDistanceDp = longThresholdDp.toFloat()
    ).longPullDistanceDp * safeDensity
}

internal fun nativeGestureVerticalThresholdPx(thresholdDp: Int, density: Float): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return configuredSideGestureThresholdsDp(
        shortPullDistanceDp = thresholdDp.toFloat(),
        longPullDistanceDp = thresholdDp.toFloat()
    ).minSwipeDistanceDp * safeDensity
}

internal fun edgeOverlayVerticalSwipeThresholdPx(shortThresholdDp: Int, density: Float): Float =
    nativeGestureVerticalThresholdPx(shortThresholdDp, density)
