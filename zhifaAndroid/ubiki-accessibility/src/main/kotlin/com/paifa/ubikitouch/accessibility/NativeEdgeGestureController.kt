package com.paifa.ubikitouch.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.TouchInteractionController
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.gesture.SwipeClassifier
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class NativeEdgeGestureController(
    private val service: AccessibilityService,
    private val mainHandler: Handler,
    private val onGesture: (EdgeSide, GestureType, GestureData) -> Unit,
    private val onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit,
    private val onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean,
    private val onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit,
    private val onBackGestureCancel: () -> Unit
) {
    private var controller: TouchInteractionController? = null
    private var callback: TouchInteractionController.Callback? = null
    private var config: NativeEdgeGestureConfig? = null
    private var floatingChatExpanded = false
    private val classifier = SwipeClassifier()
    private val touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop.toFloat()

    var isRunning: Boolean = false
        private set

    fun start(config: NativeEdgeGestureConfig): Boolean {
        if (Build.VERSION.SDK_INT < NATIVE_TOUCH_INTERACTION_MIN_SDK) return false
        val touchController = runCatching {
            service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
        }.onFailure {
            Log.w(TAG, "failed to get native touch interaction controller", it)
        }.getOrNull() ?: return false

        if (isRunning) {
            this.config = config
            return applyNativeTouchPassthrough(config, floatingChatExpanded)
        }

        val newCallback = object : TouchInteractionController.Callback {
            override fun onMotionEvent(event: MotionEvent) {
                handleMotionEvent(touchController, event)
            }

            override fun onStateChanged(state: Int) = Unit
        }

        return runCatching {
            touchController.registerCallback({ command -> mainHandler.post(command) }, newCallback)
            controller = touchController
            callback = newCallback
            this.config = config
            isRunning = true
            if (applyNativeTouchPassthrough(config, floatingChatExpanded)) {
                true
            } else {
                touchController.unregisterCallback(newCallback)
                controller = null
                callback = null
                this.config = null
                isRunning = false
                false
            }
        }.onFailure {
            Log.w(TAG, "failed to register native touch callback", it)
        }.getOrDefault(false)
    }

    fun stop() {
        val touchController = controller
        val registeredCallback = callback
        config?.let { currentConfig ->
            applyNativeTouchPassthrough(currentConfig, floatingChatExpanded = true)
        }
        if (touchController != null && registeredCallback != null) {
            runCatching { touchController.unregisterCallback(registeredCallback) }
                .onFailure { Log.w(TAG, "failed to unregister native touch callback", it) }
        }
        controller = null
        callback = null
        config = null
        floatingChatExpanded = false
        isRunning = false
        resetGesture()
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
        floatingChatExpanded: Boolean
    ): Boolean {
        val passthroughRegion = Region(0, 0, config.screenWidthPx, config.screenHeightPx)
        nativeTouchInterceptRects(config, floatingChatExpanded).forEach { intercept ->
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetGesture()
        } else if (gestureDelegated) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                resetGesture()
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
                cancelBackProgressIfNeeded()
                resetGesture()
            }
            else -> Unit
        }
    }

    private fun handleDown(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        val hit = nativeEdgeGestureHitTest(
            x = event.x,
            y = event.y,
            screenWidthPx = config.screenWidthPx,
            screenHeightPx = config.screenHeightPx,
            density = config.density,
            leftConfigs = config.leftConfigs,
            rightConfigs = config.rightConfigs
        )
        if (hit == null) {
            requestDelegatingOnce(touchController)
            clearGestureTracking()
            return
        }
        activeSide = hit.side
        startX = event.x
        startY = event.y
        latestX = event.x
        latestY = event.y
        consumingGesture = false
        latestBackProgress = null
        sentBackCancel = false
    }

    private fun handleMove(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        val side = activeSide ?: run {
            requestDelegatingOnce(touchController)
            return
        }
        latestX = event.x
        latestY = event.y
        val dx = latestX - startX
        val dy = latestY - startY
        val distance = hypot(dx, dy)
        val backProgress = BackGestureProgress.fromDelta(
            side = side,
            dx = dx,
            dy = dy,
            thresholdPx = config.shortThresholdPx,
            longThresholdPx = config.longThresholdPx,
            touchY = latestY,
            startY = startY,
            minimumDragDistancePx = 0f
        )

        if (backProgress != null) {
            latestBackProgress = backProgress
            sentBackCancel = false
            onBackGestureProgress(side, backProgress)
            if (distance > touchSlopPx || consumingGesture) {
                consumingGesture = true
            }
        } else if (latestBackProgress != null && !sentBackCancel) {
            latestBackProgress = null
            sentBackCancel = true
            onBackGestureCancel()
        } else if (!consumingGesture && distance > touchSlopPx) {
            requestDelegatingOnce(touchController)
            clearGestureTracking()
            return
        }

        val classified = classifier.classify(side, dx, dy)
        if (!consumingGesture && classified != null && distance >= config.shortThresholdPx) {
            consumingGesture = true
        }
    }

    private fun handleUp(
        touchController: TouchInteractionController,
        config: NativeEdgeGestureConfig,
        event: MotionEvent
    ) {
        val side = activeSide ?: run {
            requestDelegatingOnce(touchController)
            resetGesture()
            return
        }
        latestX = event.x
        latestY = event.y
        val dx = latestX - startX
        val dy = latestY - startY
        val distance = hypot(dx, dy)
        val data = GestureData(startX, startY, latestX, latestY)
        val finalBackProgress = latestBackProgress
        val classified = classifier.classify(side, dx, dy)

        if (consumingGesture && finalBackProgress != null) {
            onBackGestureEnd(side, finalBackProgress)
            if (!onBackGestureCommit(side, finalBackProgress, data)) {
                onGesture(side, finalBackProgress.gestureType, data)
            }
        } else if (consumingGesture && classified != null && distance >= config.shortThresholdPx) {
            onGesture(side, classified, data)
        } else {
            cancelBackProgressIfNeeded()
            requestDelegatingOnce(touchController)
        }
        resetGesture()
    }

    private fun requestDelegatingOnce(touchController: TouchInteractionController) {
        val platformIsDelegating = touchController.state == TouchInteractionController.STATE_DELEGATING
        if (!shouldRequestNativeTouchDelegation(gestureDelegated, platformIsDelegating)) {
            gestureDelegated = true
            return
        }
        try {
            touchController.requestDelegating()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "native touch delegation rejected state=${touchController.state}", error)
        } finally {
            gestureDelegated = true
        }
    }

    private fun cancelBackProgressIfNeeded() {
        if (latestBackProgress == null || sentBackCancel) return
        sentBackCancel = true
        onBackGestureCancel()
    }

    private fun resetGesture() {
        clearGestureTracking()
        gestureDelegated = false
    }

    private fun clearGestureTracking() {
        activeSide = null
        startX = 0f
        startY = 0f
        latestX = 0f
        latestY = 0f
        consumingGesture = false
        latestBackProgress = null
        sentBackCancel = false
    }

    private var activeSide: EdgeSide? = null
    private var startX = 0f
    private var startY = 0f
    private var latestX = 0f
    private var latestY = 0f
    private var consumingGesture = false
    private var latestBackProgress: BackGestureProgress? = null
    private var sentBackCancel = false
    private var gestureDelegated = false

    private companion object {
        const val TAG = "NativeEdgeGesture"
    }
}

internal fun shouldRequestNativeTouchDelegation(
    alreadyDelegated: Boolean,
    platformIsDelegating: Boolean
): Boolean {
    return !alreadyDelegated && !platformIsDelegating
}

internal data class NativeEdgeGestureConfig(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val density: Float,
    val leftConfigs: List<EdgeZoneConfig>,
    val rightConfigs: List<EdgeZoneConfig>,
    val shortThresholdPx: Float,
    val longThresholdPx: Float
)

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

internal fun nativeTouchInterceptRects(
    config: NativeEdgeGestureConfig,
    floatingChatExpanded: Boolean
): List<NativeTouchInterceptRect> {
    if (floatingChatExpanded) return emptyList()
    return nativeTouchInterceptRects(
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
    if (screenWidthPx <= 0 || screenHeightPx <= 0 || density <= 0f) return null
    return nativeTouchInterceptRects(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        density = density,
        configs = leftConfigs + rightConfigs
    ).firstOrNull { rect ->
        x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }?.let { rect ->
        NativeEdgeGestureHit(side = rect.side, zoneId = rect.zoneId)
    }
}

private fun nativeTouchInterceptRects(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    configs: List<EdgeZoneConfig>
): List<NativeTouchInterceptRect> {
    if (screenWidthPx <= 0 || screenHeightPx <= 0 || density <= 0f) return emptyList()
    return configs.mapNotNull { config ->
        val sanitized = config.sanitized()
        if (!sanitized.enabled) return@mapNotNull null
        val widthPx = (nativeTouchInteractionEdgeStartTargetDp(sanitized.thicknessDp) * density)
            .roundToInt()
            .coerceIn(1, screenWidthPx)
        val heightPercent = 100 - sanitized.topInsetPercent - sanitized.bottomInsetPercent
        val zoneHeight = (screenHeightPx * heightPercent.coerceIn(EdgeZoneConfig.MIN_LENGTH_PERCENT, 100) / 100)
            .coerceAtLeast(1)
        val zoneTop = (screenHeightPx * sanitized.topInsetPercent / 100)
            .coerceIn(0, screenHeightPx - zoneHeight)
        val zoneBottom = zoneTop + zoneHeight
        val left = when (sanitized.side) {
            EdgeSide.LEFT -> 0
            EdgeSide.RIGHT -> screenWidthPx - widthPx
        }
        NativeTouchInterceptRect(
            side = sanitized.side,
            zoneId = sanitized.zoneId,
            left = left,
            top = zoneTop,
            right = left + widthPx,
            bottom = zoneBottom
        )
    }
}

internal fun nativeGestureThresholdPx(thresholdDp: Int, density: Float): Float {
    return thresholdDp.coerceIn(8, 320) * density * NATIVE_GESTURE_THRESHOLD_RESPONSE_RATIO
}

private const val NATIVE_GESTURE_THRESHOLD_RESPONSE_RATIO = 0.70f
