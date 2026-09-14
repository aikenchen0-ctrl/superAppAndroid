package com.paifa.univerge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.TouchInteractionController
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
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
    private val touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
    private val previewData = GestureData(0f, 0f, 0f, 0f)
    private var coreBridge: NativeGestureCoreBridge? = null
    private var activeConfig: NativeEdgeGestureConfig? = null
    private val sessionAbort = NativeGestureSessionAbort(
        cancelCore = { coreBridge?.onCancel() },
        cancelBackProgress = ::cancelBackProgressIfNeeded,
        endPreview = onGestureEnd,
        clearFields = ::resetGestureFields
    )

    var isRunning: Boolean = false
        private set

    val hasActiveGesture: Boolean
        get() = coreBridge?.hasActiveGesture == true

    fun start(config: NativeEdgeGestureConfig): Boolean {
        if (Build.VERSION.SDK_INT < NATIVE_TOUCH_INTERACTION_MIN_SDK) return false
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

            override fun onStateChanged(state: Int) = Unit
        }

        return runCatching {
            touchController.registerCallback(
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

    private fun handleMotionEvent(
        touchController: TouchInteractionController,
        event: MotionEvent
    ) {
        traceNativeBoundary(
            action = event.actionMasked,
            eventTime = event.eventTime,
            controllerState = touchController.state
        )
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // A lost UP/CANCEL must not leak the previous BackWave selection
            // into the next transaction. Abort before accepting the new DOWN.
            abortActiveSession()
        } else if (gestureDelegated) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                abortActiveSession()
            }
            return
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
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                abortActiveSession()
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
            if (activeSide == null && !activeBottomGesture) {
                requestDelegatingOnce(touchController)
            }
            return
        }
        latestX = event.x
        latestY = event.y
        if (signal is NativeCoreSignal.Bottom) {
            if (signal.signal is GestureSignal.Preview) consumingGesture = true
            if (signal.signal is GestureSignal.Cancel) {
                abortActiveSession()
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
        onGestureProgress(side, previewAction, previewData)
        val dx = latestX - startX
        val dy = latestY - startY
        val distance = hypot(dx, dy)
        val backProgress = backProgressFor(
            side = side,
            config = active,
            touchX = latestX,
            touchY = latestY
        )

        if (backProgress != null) {
            latestBackProgress = backProgress
            sentBackCancel = false
            onBackGestureProgress(intercept, backProgress)
            if (distance > touchSlopPx || consumingGesture) {
                consumingGesture = true
            }
        } else if (latestBackProgress != null && !sentBackCancel) {
            latestBackProgress = null
            sentBackCancel = true
            onBackGestureCancel()
        }
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
                val type = bottomGestureTypeFor(commit.gesture)
                if (type != null) onBottomGesture(type, commit.action, commit.data.toPx(activeConfig?.density ?: config.density))
            }
            onGestureEnd()
            resetGestureFields()
            return
        }
        if (signal !is NativeCoreSignal.Side) {
            requestDelegatingOnce(touchController)
            resetGestureFields()
            return
        }
        val side = signal.side
        val intercept = activeIntercept
        val active = activeConfig ?: config
        val commit = signal.signal as? GestureSignal.Commit
        onGestureEnd()
        val data = commit?.data?.toPx(active.density)
            ?: GestureData(startX, startY, latestX, latestY)
        val finalBackProgress = backProgressFor(
            side = side,
            config = active,
            touchX = latestX,
            touchY = latestY
        ) ?: latestBackProgress?.copy(selectedOption = BackGestureOption.None)

        if (commit != null && finalBackProgress != null && intercept != null) {
            onBackGestureEnd(intercept, finalBackProgress)
            if (!onBackGestureCommit(intercept, finalBackProgress, commit.action, data)) {
                onGesture(side, commit.gesture, commit.action, data)
            }
        } else if (commit != null) {
            onGesture(side, commit.gesture, commit.action, data)
        } else {
            cancelBackProgressIfNeeded()
            requestDelegatingOnce(touchController)
        }
        resetGestureFields()
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

    private fun cancelBackProgressIfNeeded() {
        if (latestBackProgress == null || sentBackCancel) return
        sentBackCancel = true
        onBackGestureCancel()
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
        clearGestureTrackingFields()
        gestureDelegated = false
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
        latestBackProgress = null
        sentBackCancel = false
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
    private var latestBackProgress: BackGestureProgress? = null
    private var sentBackCancel = false
    private var gestureDelegated = false

    private companion object {
        const val TAG = "NativeEdgeGesture"
    }
}

internal fun shouldDispatchBottomGestureDuringMove(gestureType: BottomGestureBarGestureType): Boolean {
    return gestureType == BottomGestureBarGestureType.SwipeUpHold ||
        gestureType == BottomGestureBarGestureType.LongPress
}

internal fun shouldRequestNativeTouchDelegation(
    alreadyDelegated: Boolean,
    platformIsDelegating: Boolean
): Boolean {
    return !alreadyDelegated && !platformIsDelegating
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
                    thicknessDp = value.thicknessDp.toFloat(),
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
    return thresholdDp.coerceIn(8, 320) * density * NATIVE_GESTURE_THRESHOLD_RESPONSE_RATIO
}

private const val NATIVE_GESTURE_THRESHOLD_RESPONSE_RATIO = 0.70f
