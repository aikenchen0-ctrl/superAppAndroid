package com.paifa.ubikitouch.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.paifa.ubikitouch.core.gesture.BackGestureOption
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.gesture.SwipeClassifier
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.core.sidefunction.SideFunctionConfig
import com.paifa.ubikitouch.core.sidefunction.DEFAULT_SIDE_FUNCTION_VERTICAL_SAFE_INSET_DP
import com.paifa.ubikitouch.core.sidefunction.sideFunctionGroupShiftY
import com.paifa.ubikitouch.core.sidefunction.sideFunctionLayout
import com.paifa.ubikitouch.core.model.sanitizeEdgeInsetDp
import com.paifa.ubikitouch.core.overlay.ScreenInteractiveState
import com.paifa.ubikitouch.overlay.EdgeOutlineView
import com.paifa.ubikitouch.overlay.EdgeOverlayView
import com.paifa.ubikitouch.overlay.edgeOutlinePlacement
import kotlin.math.roundToInt

class UbikiAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: UbikiPreferences
    private lateinit var actionExecutor: UbikiActionExecutor
    private lateinit var backWaveOverlayController: BackWaveOverlayController
    private lateinit var bottomGestureBarOverlayController: BottomGestureBarOverlayController
    private lateinit var bottomGestureBarPreviewController: BottomGestureBarPreviewController
    private lateinit var bottomGestureBarIndicatorController: BottomGestureBarIndicatorController
    private lateinit var floatingChatOverlayController: FloatingChatOverlayController
    private lateinit var videoDemoOverlayController: VideoDemoOverlayController
    private lateinit var meteorSwipeEffectController: MeteorSwipeEffectController
    private lateinit var pullDistancePreviewController: PullDistancePreviewController
    private lateinit var gestureHintOverlayController: GestureHintOverlayController
    private lateinit var nativeGestureExclusionOverlayController: NativeGestureExclusionOverlayController
    private lateinit var nativeBackGestureTakeoverController: NativeBackGestureTakeoverController
    private val overlays = mutableMapOf<OverlayKey, EdgeOverlayView>()
    private val edgeOutlines = mutableMapOf<OverlayKey, EdgeOutlineView>()
    private var edgeConfigAdjustmentOutline: EdgeOutlineView? = null
    private var edgeConfigAdjustmentPreviewSide: EdgeSide? = null
    private var edgeConfigAdjustmentPreviewZoneId: Int? = null
    private var backWavePresentedForGesture = false
    private var selectedSideFunctionIndex: Int? = null
    private val gestureHintClassifier = SwipeClassifier()
    private var nativeEdgeGestureController: NativeEdgeGestureController? = null
    private var nativeBackTakeoverApplied = false
    private var nativeTouchInteractionRuntimeFailed = false
    private var floatingChatExpanded = false
    private var floatingChatExternalActivityVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissEdgeConfigAdjustmentPreviewRunnable = Runnable {
        removeEdgeConfigAdjustmentPreview()
    }
    private val pauseExpiredRunnable = Runnable {
        Log.d(TAG, "temporary pause expired")
        recreateOverlays()
    }
    private val overlayRefreshRunnable = Runnable {
        recreateOverlays()
    }
    private val floatingChatAppearanceRefreshRunnable = Runnable {
        if (!::floatingChatOverlayController.isInitialized) return@Runnable
        runCatching { floatingChatOverlayController.refreshAppearance() }
            .onFailure { Log.w(TAG, "failed to refresh floating chat appearance", it) }
    }
    private var wakeResumeReason = "unknown"
    private val wakeResumeRunnable = Runnable {
        Log.d(TAG, "resume overlays after wake reason=$wakeResumeReason interactive=${screenInteractiveState.isInteractive}")
        if (!screenInteractiveState.isInteractive) return@Runnable
        recreateOverlays()
        showFloatingChatOverlayIfAllowed()
    }
    private val screenInteractiveState = ScreenInteractiveState()
    private var currentPackageBlocked = false
    private var isKeyboardVisible = false

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        applyServiceRuntimeConfig()
        if (isFloatingChatAppearancePreferenceKey(key)) {
            requestFloatingChatAppearanceRefresh()
        } else if (isBottomGestureBarPreferenceKey(key)) {
            if (::bottomGestureBarOverlayController.isInitialized) {
                bottomGestureBarOverlayController.recreate()
            }
            requestOverlayRefresh()
        } else {
            requestOverlayRefresh()
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "screen receiver action=${intent?.action}")
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractiveState.markNotInteractive()
                    mainHandler.removeCallbacks(wakeResumeRunnable)
                    removeFloatingChatOverlay()
                    if (::videoDemoOverlayController.isInitialized) {
                        videoDemoOverlayController.dismissImmediately()
                    }
                    if (::meteorSwipeEffectController.isInitialized) {
                        meteorSwipeEffectController.dismissImmediately()
                    }
                    removeAllOverlays()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractiveState.markInteractive()
                    requestWakeOverlayResume("screen_on", SCREEN_ON_RESUME_DELAY_MS)
                }
                Intent.ACTION_USER_PRESENT -> {
                    screenInteractiveState.markInteractive()
                    requestWakeOverlayResume("user_present", USER_PRESENT_RESUME_DELAY_MS)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "service connected")
        instance = this
        isRunning = true
        startPersistentForeground()
        UbikiGesturePersistence.scheduleRecoveryWatchdog(this)
        startKeepAliveService()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        videoDemoOverlayController = VideoDemoOverlayController(this, windowManager)
        meteorSwipeEffectController = MeteorSwipeEffectController(this, windowManager)
        pullDistancePreviewController = PullDistancePreviewController(this, windowManager)
        gestureHintOverlayController = GestureHintOverlayController(this, windowManager)
        backWaveOverlayController = BackWaveOverlayController(this, windowManager)
        nativeGestureExclusionOverlayController = NativeGestureExclusionOverlayController(this, windowManager)
        nativeBackGestureTakeoverController = NativeBackGestureTakeoverController(this)
        floatingChatOverlayController = FloatingChatOverlayController(
            context = this,
            windowManager = windowManager,
            onEdgeGesture = ::handleFloatingChatEdgeGesture,
            onBottomGesture = ::handleFloatingChatBottomGesture,
            onBackGestureProgress = ::handleFloatingChatBackGestureProgress,
            onBackGestureCommit = ::handleFloatingChatBackGestureCommit,
            onBackGestureEnd = ::handleFloatingChatBackGestureEnd,
            onBackGestureCancel = ::handleFloatingChatBackGestureCancel,
            onExpandedChanged = ::handleFloatingChatExpandedChanged,
            onOverlayRecreated = ::scheduleBottomGestureBarZOrderRefresh
        )
        preferences = UbikiPreferences(this)
        actionExecutor = UbikiActionExecutor(
            this,
            isHapticFeedbackEnabled = { preferences.hapticFeedback },
            floatingChatOverlayController = floatingChatOverlayController,
            videoDemoOverlayController = videoDemoOverlayController
        )
        bottomGestureBarOverlayController = BottomGestureBarOverlayController(
            context = this,
            windowManager = windowManager,
            preferences = preferences,
            onGesture = ::executeConfiguredGestureAction
        )
        bottomGestureBarPreviewController = BottomGestureBarPreviewController(this, windowManager)
        bottomGestureBarIndicatorController = BottomGestureBarIndicatorController(this, windowManager)
        screenInteractiveState.updateFromSystem(isDeviceInteractive())
        preferences.registerChangeListener(preferenceListener)
        registerScreenReceiver()
        applyServiceRuntimeConfig()
        createOverlays()
        syncBottomGestureBar()
        showFloatingChatOverlayIfAllowed()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::preferences.isInitialized) return

        refreshScreenInteractiveFor("accessibility_event_${event.eventType}")
        val keyboardChanged = refreshKeyboardVisibilityFor(event)
        val foregroundBlockChanged = refreshForegroundPackageFor(event)
        if (keyboardChanged || foregroundBlockChanged) {
            requestOverlayRefresh()
        }
    }

    private fun refreshForegroundPackageFor(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        if (isInputMethodEvent(event)) return false

        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return false
        if (packageName == currentForegroundPackage) return false
        currentForegroundPackage = packageName
        Log.d(TAG, "foreground package=$packageName")
        return updateForegroundBlockState()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::bottomGestureBarOverlayController.isInitialized) {
            bottomGestureBarOverlayController.recreate()
        }
        recreateOverlays()
    }

    override fun onDestroy() {
        Log.d(TAG, "service destroyed")
        instance = null
        isRunning = false
        if (::preferences.isInitialized) {
            preferences.unregisterChangeListener(preferenceListener)
        }
        runCatching { unregisterReceiver(screenStateReceiver) }
        mainHandler.removeCallbacks(pauseExpiredRunnable)
        mainHandler.removeCallbacks(overlayRefreshRunnable)
        mainHandler.removeCallbacks(floatingChatAppearanceRefreshRunnable)
        mainHandler.removeCallbacks(wakeResumeRunnable)
        mainHandler.removeCallbacks(dismissEdgeConfigAdjustmentPreviewRunnable)
        removeEdgeConfigAdjustmentPreview()
        if (::bottomGestureBarOverlayController.isInitialized) {
            bottomGestureBarOverlayController.remove()
        }
        if (::bottomGestureBarPreviewController.isInitialized) {
            bottomGestureBarPreviewController.dismiss()
        }
        if (::bottomGestureBarIndicatorController.isInitialized) {
            bottomGestureBarIndicatorController.dismiss()
        }
        if (::videoDemoOverlayController.isInitialized) {
            videoDemoOverlayController.dismissImmediately()
        }
        if (::meteorSwipeEffectController.isInitialized) {
            meteorSwipeEffectController.dismissImmediately()
        }
        if (::pullDistancePreviewController.isInitialized) {
            pullDistancePreviewController.dismiss()
        }
        removeFloatingChatOverlay()
        removeAllOverlays()
        if (UbikiGesturePersistence.isAccessibilityServiceEnabled(this)) {
            UbikiGesturePersistence.scheduleRecoveryWatchdog(this, ACCESSIBILITY_DESTROYED_RECOVERY_DELAY_MS)
        } else {
            UbikiGesturePersistence.cancelRecoveryWatchdog(this)
        }
        stopPersistentForeground()
        super.onDestroy()
    }

    fun requestOverlayRefresh() {
        mainHandler.removeCallbacks(overlayRefreshRunnable)
        mainHandler.removeCallbacks(floatingChatAppearanceRefreshRunnable)
        mainHandler.postDelayed(overlayRefreshRunnable, OVERLAY_REFRESH_DEBOUNCE_MS)
    }

    /** 供 Debug 主界面展开当前服务管理的悬浮聊天窗口。 */
    fun requestFloatingChatExpandForDebug(): Boolean {
        if (!::floatingChatOverlayController.isInitialized) {
            Log.w(TAG, "skip debug floating chat expand before overlay initialization")
            return false
        }
        return runCatching {
            floatingChatOverlayController.expand()
            true
        }.onFailure { error ->
            Log.e(TAG, "failed to expand floating chat from debug entry", error)
        }.getOrDefault(false)
    }

    fun showPullDistancePreview(shortDistanceDp: Int, longDistanceDp: Int) {
        if (!::pullDistancePreviewController.isInitialized) return
        pullDistancePreviewController.show(shortDistanceDp, longDistanceDp)
    }

    fun showBottomGestureBarPreview(widthDp: Int) {
        if (!::preferences.isInitialized) return
        if (preferences.showIndicators && ::bottomGestureBarIndicatorController.isInitialized) {
            bottomGestureBarIndicatorController.show(widthDp)
            return
        }
        if (!::bottomGestureBarPreviewController.isInitialized) return
        bottomGestureBarPreviewController.show(widthDp)
    }

    fun showEdgeConfigAdjustmentPreview(config: EdgeZoneConfig) {
        if (!::windowManager.isInitialized || !::preferences.isInitialized) return
        if (shouldUpdatePersistentEdgeOutline(preferences.showIndicators)) {
            updatePersistentEdgeOutline(config)
            return
        }
        if (!shouldShowEdgeConfigAdjustmentPreview(preferences.showIndicators)) return

        mainHandler.removeCallbacks(dismissEdgeConfigAdjustmentPreviewRunnable)
        if (!config.enabled) {
            removeEdgeConfigAdjustmentPreview()
            return
        }

        val (screenWidth, screenHeight) = currentDisplaySize()
        val params = createEdgeOutlineLayoutParams(config, screenWidth, screenHeight)
        val existingOutline = edgeConfigAdjustmentOutline
        if (
            existingOutline != null &&
            canReuseEdgeConfigAdjustmentPreview(
                edgeConfigAdjustmentPreviewSide,
                edgeConfigAdjustmentPreviewZoneId,
                config
            )
        ) {
            runCatching { windowManager.updateViewLayout(existingOutline, params) }
                .onSuccess {
                    mainHandler.postDelayed(
                        dismissEdgeConfigAdjustmentPreviewRunnable,
                        EDGE_CONFIG_ADJUSTMENT_PREVIEW_HIDE_DELAY_MS
                    )
                }
                .onFailure { error -> Log.w(TAG, "failed to update edge configuration preview", error) }
            return
        }

        removeEdgeConfigAdjustmentPreview()
        val outline = EdgeOutlineView(this, config.side, config.zoneId)
        runCatching {
            windowManager.addView(outline, params)
            edgeConfigAdjustmentOutline = outline
            edgeConfigAdjustmentPreviewSide = config.side
            edgeConfigAdjustmentPreviewZoneId = config.zoneId
            mainHandler.postDelayed(
                dismissEdgeConfigAdjustmentPreviewRunnable,
                EDGE_CONFIG_ADJUSTMENT_PREVIEW_HIDE_DELAY_MS
            )
        }.onFailure { error ->
            Log.w(TAG, "failed to show edge configuration preview", error)
        }
    }

    fun requestOverlayRecoveryCheck() {
        mainHandler.post {
            if (
                !::windowManager.isInitialized ||
                !::preferences.isInitialized ||
                !::floatingChatOverlayController.isInitialized ||
                !screenInteractiveState.isInteractive
            ) {
                return@post
            }
            if (
                resolvedGestureInputMode() == ResolvedGestureInputMode.NativeTouchInteraction &&
                overlays.isEmpty()
            ) {
                val nativeRunning = nativeEdgeGestureController?.isRunning == true
                if (
                    nativeTouchRecoveryNeeded(
                        floatingChatExpanded = floatingChatExpanded,
                        controllerRunning = nativeRunning,
                        externalActivityVisible = floatingChatExternalActivityVisible
                    )
                ) {
                    Log.d(TAG, "native gesture controller missing, recover")
                    requestOverlayRefresh()
                }
                return@post
            }
            val overlaysMissing = overlays.isEmpty() || overlays.values.any { overlay ->
                !overlay.isAttachedToWindow
            }
            if (overlaysMissing) {
                Log.d(TAG, "gesture overlays missing, recover")
                requestOverlayRefresh()
            }
        }
    }

    private fun requestFloatingChatAppearanceRefresh() {
        mainHandler.removeCallbacks(overlayRefreshRunnable)
        mainHandler.removeCallbacks(floatingChatAppearanceRefreshRunnable)
        mainHandler.postDelayed(floatingChatAppearanceRefreshRunnable, OVERLAY_REFRESH_DEBOUNCE_MS)
    }

    fun requestFloatingChatMediaPick(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        hideFloatingChatForExternalActivity("media picker")
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatMediaPickerActivity"
            )
            .addFloatingChatBridgeFlags()
            .putExtra(FloatingChatMediaPickerBridge.EXTRA_MEDIA_KIND, mediaKind.name)
            .putExtra(FloatingChatMediaPickerBridge.EXTRA_MEDIA_TARGET, target.name)
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start media picker", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun requestFloatingChatMediaCapture() {
        hideFloatingChatForExternalActivity("camera")
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatCameraActivity"
            )
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start camera capture", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun requestFloatingChatScan() {
        hideFloatingChatForExternalActivity("scan")
        val intent = Intent()
            .setClassName(packageName, "com.paifa.ubikitouch.app.FloatingChatCameraActivity")
            .addFloatingChatBridgeFlags()
            .putExtra(FloatingChatMediaPickerBridge.EXTRA_SCAN_MODE, true)
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start scanner", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun requestFloatingChatBlinkVoiceCapture() {
        hideFloatingChatForExternalActivity("BlinkVoice")
        val intent = Intent()
            .setClassName(packageName, blinkVoiceBridgeActivityClassName())
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start BlinkVoice capture", it)
            onFloatingChatBlinkVoiceClosed()
        }
    }

    fun requestFloatingChatBlinkVoiceHeadlessCapture() {
        val intent = Intent()
            .setClassName(packageName, blinkVoiceBridgeActivityClassName())
            .putExtra(BlinkVoiceHeadlessExtraName, true)
            .addFloatingChatBridgeFlags()
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start headless BlinkVoice capture", it)
        }
    }

    fun requestFloatingChatDocumentPick() {
        hideFloatingChatForExternalActivity("document picker")
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatDocumentPickerActivity"
            )
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start document picker", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun requestFloatingChatCouponWallet() {
        hideFloatingChatForExternalActivity("coupon wallet")
        val intent = Intent()
            .setClassName(packageName, "com.paifa.ubikitouch.app.CouponWalletActivity")
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start coupon wallet", it)
            onFloatingChatCouponWalletClosed()
        }
    }

    fun requestFloatingChatTransfer() {
        hideFloatingChatForExternalActivity("transfer")
        val intent = Intent()
            .setClassName(packageName, "com.paifa.ubikitouch.app.TransferFlowActivity")
            .addFloatingChatBridgeFlags()
        runCatching { startActivity(intent) }.onFailure {
            Log.e(TAG, "failed to start transfer", it)
            onFloatingChatTransferClosed()
        }
    }

    fun requestFloatingChatRedPacket() {
        hideFloatingChatForExternalActivity("red packet")
        val intent = Intent()
            .setClassName(packageName, "com.paifa.ubikitouch.app.RedPacketActivity")
            .addFloatingChatBridgeFlags()
        runCatching { startActivity(intent) }.onFailure {
            Log.e(TAG, "failed to start red packet", it)
            onFloatingChatRedPacketClosed()
        }
    }

    private fun hideFloatingChatForExternalActivity(source: String) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching {
            floatingChatOverlayController.hideForMediaPicker()
            setFloatingChatExternalActivityVisible(true)
        }.onFailure { error ->
            setFloatingChatExternalActivityVisible(false)
            Log.w(TAG, "failed to hide floating chat for $source", error)
        }
    }

    private fun setFloatingChatExternalActivityVisible(visible: Boolean) {
        if (floatingChatExternalActivityVisible == visible) return
        floatingChatExternalActivityVisible = visible
        if (visible) {
            removeAllOverlays()
            createOverlays()
            return
        }

        removeAllOverlays()
        applyServiceRuntimeConfig()
    }

    fun onFloatingChatBlinkVoiceResult(
        eventType: String,
        durationMs: Long,
        confidence: Float,
        headless: Boolean = false
    ) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching {
            floatingChatOverlayController.addBlinkVoiceResult(
                eventType = eventType,
                durationMs = durationMs,
                confidence = confidence,
                headless = headless
            )
        }.onFailure {
            Log.e(TAG, "failed to deliver BlinkVoice result to floating chat", it)
            onFloatingChatBlinkVoiceClosed()
        }
    }

    fun onFloatingChatBlinkVoiceClosed() {
        onFloatingChatMediaPickerClosed()
    }

    fun onFloatingChatMediaPicked(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: String,
        previewUri: String,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching {
            floatingChatOverlayController.addPickedMediaMessage(
                mediaKind = mediaKind,
                mediaUri = mediaUri,
                previewUri = previewUri,
                orientation = orientation,
                aspectRatio = aspectRatio,
                target = target
            )
        }.onFailure {
            Log.e(TAG, "failed to deliver picked media to floating chat", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun onFloatingChatDocumentPicked(document: FloatingChatPickedDocument) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching {
            floatingChatOverlayController.addPickedDocumentMessage(document)
        }.onFailure {
            Log.e(TAG, "failed to deliver picked document to floating chat", it)
            onFloatingChatMediaPickerClosed()
        }
    }

    fun onFloatingChatMediaPickerClosed() {
        setFloatingChatExternalActivityVisible(false)
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.restoreAfterMediaPicker() }
                .onFailure { Log.w(TAG, "failed to restore floating chat after media picker", it) }
        }
    }

    fun onFloatingChatCouponWalletClosed() {
        onFloatingChatMediaPickerClosed()
    }

    fun onFloatingChatTransferClosed() {
        onFloatingChatMediaPickerClosed()
    }

    fun onFloatingChatRedPacketClosed() {
        onFloatingChatMediaPickerClosed()
    }

    fun requestFloatingChatMediaPreview() {
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.hideForMediaPreview() }
                .onFailure { Log.w(TAG, "failed to hide floating chat for media preview", it) }
        }
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatMediaPreviewActivity"
            )
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start media preview", it)
            onFloatingChatMediaPreviewClosed()
        }
    }

    fun requestFloatingChatVoicePermission() {
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatVoicePermissionActivity"
            )
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start voice permission activity", it)
        }
    }

    fun onFloatingChatVoicePermissionResult(granted: Boolean) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching { floatingChatOverlayController.onVoicePermissionResult(granted) }
            .onFailure { Log.w(TAG, "failed to handle voice permission result", it) }
    }

    fun requestFloatingChatLocationPermission() {
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.hideForPermissionPrompt() }
                .onFailure { Log.w(TAG, "failed to hide floating chat for location permission", it) }
        }
        val intent = Intent()
            .setClassName(
                packageName,
                "com.paifa.ubikitouch.app.FloatingChatLocationPermissionActivity"
            )
            .addFloatingChatBridgeFlags()
        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "failed to start location permission activity", it)
            if (::floatingChatOverlayController.isInitialized) {
                floatingChatOverlayController.restoreAfterPermissionPrompt()
            }
        }
    }

    fun onFloatingChatLocationPermissionResult(granted: Boolean) {
        if (!::floatingChatOverlayController.isInitialized) return
        runCatching {
            floatingChatOverlayController.restoreAfterPermissionPrompt()
            floatingChatOverlayController.onLocationPermissionResult(granted)
        }
            .onFailure { Log.w(TAG, "failed to handle location permission result", it) }
    }

    fun onFloatingChatMediaPreviewClosed() {
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.restoreAfterMediaPreview() }
                .onFailure { Log.w(TAG, "failed to restore floating chat after media preview", it) }
        }
    }

    fun onFloatingChatExternalDocumentClosed() {
        setFloatingChatExternalActivityVisible(false)
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.restoreAfterExternalDocument() }
                .onFailure { Log.w(TAG, "failed to restore floating chat after external document", it) }
        }
    }

    fun recreateOverlays() {
        mainHandler.removeCallbacks(overlayRefreshRunnable)
        mainHandler.removeCallbacks(floatingChatAppearanceRefreshRunnable)
        if (
            !::windowManager.isInitialized ||
            !::preferences.isInitialized ||
            !::floatingChatOverlayController.isInitialized
        ) {
            Log.w(TAG, "skip recreate overlays before service initialization is complete")
            return
        }
        Log.d(TAG, "recreate overlays")
        runCatching {
            removeAllOverlays(restoreNavigationProtection = false)
            actionExecutor = UbikiActionExecutor(
                this,
                isHapticFeedbackEnabled = { preferences.hapticFeedback },
                floatingChatOverlayController = floatingChatOverlayController,
                videoDemoOverlayController = videoDemoOverlayController
            )
            createOverlays()
            floatingChatOverlayController.refreshEdgeGestureConfig()
            syncBottomGestureBar()
            showFloatingChatOverlayIfAllowed()
        }.onFailure {
            Log.e(TAG, "failed to recreate overlays", it)
        }
    }

    private fun requestWakeOverlayResume(reason: String, delayMs: Long) {
        wakeResumeReason = reason
        mainHandler.removeCallbacks(wakeResumeRunnable)
        mainHandler.postDelayed(wakeResumeRunnable, delayMs)
        Log.d(TAG, "scheduled wake overlay resume reason=$reason delayMs=$delayMs")
    }

    private fun refreshScreenInteractiveFor(reason: String) {
        val actualInteractive = isDeviceInteractive()
        val shouldResume = screenInteractiveState.updateFromSystem(actualInteractive)
        if (shouldResume) {
            Log.d(TAG, "detected interactive recovery reason=$reason")
            requestWakeOverlayResume(reason, ACCESSIBILITY_EVENT_RESUME_DELAY_MS)
        }
    }

    private fun isDeviceInteractive(): Boolean {
        return (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
    }

    private fun synchronizeGestureNavigationProtection(screenWidth: Int, screenHeight: Int) {
        if (
            !::preferences.isInitialized ||
            !::nativeGestureExclusionOverlayController.isInitialized ||
            !::nativeBackGestureTakeoverController.isInitialized
        ) {
            return
        }
        val density = resources.displayMetrics.density
        val configs = EdgeSide.entries.flatMap(preferences::edgeConfigs)
        val floatingChatOwnsSurface = floatingChatOwnsGestureSurface(
            floatingChatExpanded,
            floatingChatExternalActivityVisible
        )
        val initialPlan = gestureNavigationProtectionPlan(
            sdkInt = Build.VERSION.SDK_INT,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            density = density,
            configs = configs,
            resolvedMode = resolvedGestureInputMode(),
            floatingChatOwnsSurface = floatingChatOwnsSurface
        )
        val result = nativeBackGestureTakeoverController.synchronize(
            requestedSides = initialPlan.rootTakeoverSides,
            sdkInt = Build.VERSION.SDK_INT,
            navBarHeightPx = legacyNavigationBarHeightPx()
        )
        nativeBackTakeoverApplied = result.applied
        result.errorMessage?.let { error ->
            Log.w(TAG, "native back gesture takeover not applied error=$error")
        }

        val finalPlan = gestureNavigationProtectionPlan(
            sdkInt = Build.VERSION.SDK_INT,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            density = density,
            configs = configs,
            resolvedMode = resolvedGestureInputMode(),
            floatingChatOwnsSurface = floatingChatOwnsSurface
        )
        nativeGestureExclusionOverlayController.synchronize(
            sourceWidthPx = screenWidth,
            sourceHeightPx = screenHeight,
            intercepts = finalPlan.systemExclusionIntercepts
        )
    }

    private fun clearGestureNavigationProtection() {
        if (::nativeGestureExclusionOverlayController.isInitialized) {
            nativeGestureExclusionOverlayController.remove()
        }
        if (::nativeBackGestureTakeoverController.isInitialized) {
            val result = nativeBackGestureTakeoverController.restore()
            result.errorMessage?.let { error ->
                Log.w(TAG, "failed to restore native back gesture takeover error=$error")
            }
        }
        nativeBackTakeoverApplied = false
    }

    private fun legacyNavigationBarHeightPx(): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resourceId
            .takeIf { it > 0 }
            ?.let(resources::getDimensionPixelSize)
            ?.takeIf { it > 0 }
    }

    private fun startPersistentForeground() {
        runCatching {
            UbikiGesturePersistence.startForeground(this)
        }.onFailure {
            Log.w(TAG, "failed to start persistent foreground notification", it)
        }
    }

    private fun startKeepAliveService() {
        runCatching {
            UbikiGesturePersistence.startKeepAliveService(this)
        }.onFailure {
            Log.w(TAG, "failed to start keep alive service", it)
        }
    }

    private fun stopPersistentForeground() {
        runCatching {
            UbikiGesturePersistence.stopForeground(this)
        }.onFailure {
            Log.w(TAG, "failed to stop persistent foreground notification", it)
        }
    }

    private fun updateForegroundBlockState(): Boolean {
        if (!::preferences.isInitialized) return false
        val blocked = preferences.isPackageBlocked(currentForegroundPackage)
        if (blocked == currentPackageBlocked) return false
        currentPackageBlocked = blocked
        Log.d(TAG, "foreground blocked=$blocked package=$currentForegroundPackage")
        return true
    }

    private fun createOverlays() {
        if (!::preferences.isInitialized) return
        schedulePauseExpiryIfNeeded()
        currentPackageBlocked = preferences.isPackageBlocked(currentForegroundPackage)
        val disabledByLandscape = preferences.disableInLandscape &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val keyboardVisible = if (preferences.disableWhenKeyboardShown) queryKeyboardVisibility() else false
        isKeyboardVisible = keyboardVisible
        val disabledByKeyboard = preferences.disableWhenKeyboardShown && keyboardVisible
        val paused = preferences.isTemporarilyPaused() || preferences.isQuietHoursActive()
        applyServiceRuntimeConfig()
        //没有开启边缘触摸条||没有屏幕交互||
        if (!preferences.globalEnabled || !screenInteractiveState.isInteractive || currentPackageBlocked || disabledByLandscape || disabledByKeyboard || paused) {

            stopNativeEdgeGestures()
            removeEdgeOutlines()
            clearGestureNavigationProtection()
            Log.d(
                TAG,
                "skip overlays enabled=${preferences.globalEnabled} interactive=${screenInteractiveState.isInteractive} blocked=$currentPackageBlocked landscape=$disabledByLandscape keyboard=$disabledByKeyboard paused=$paused"
            )
            return
        }

        val (screenWidth, screenHeight) = currentDisplaySize()
        removeEdgeOutlines()

        if (!edgeGestureOverlayWindowsAllowed(floatingChatExpanded, floatingChatExternalActivityVisible)) {
            removeEdgeGestureOverlays()
            syncBottomGestureBar()
            applyServiceRuntimeConfig()
            synchronizeGestureNavigationProtection(screenWidth, screenHeight)
            if (resolvedGestureInputMode() == ResolvedGestureInputMode.NativeTouchInteraction &&
                startNativeEdgeGestures(screenWidth, screenHeight)
            ) {
                Log.d(TAG, "native gesture input active while floating chat is expanded")
                return
            }
            synchronizeGestureNavigationProtection(screenWidth, screenHeight)
            stopNativeEdgeGestures()
            Log.d(TAG, "use floating chat internal edge input fallback while floating chat is expanded")
            return
        }

        synchronizeGestureNavigationProtection(screenWidth, screenHeight)
        val resolvedInputMode = resolvedGestureInputMode()
        if (!shouldCreateGestureOverlayWindows(resolvedInputMode)) {
            val nativeGestureStarted = startNativeEdgeGestures(screenWidth, screenHeight)
            if (shouldSynchronizeEdgeIndicators(
                    mode = resolvedInputMode,
                    edgeGestureWindowsAllowed = edgeGestureOverlayWindowsAllowed(
                        floatingChatExpanded,
                        floatingChatExternalActivityVisible
                    ),
                    nativeGestureStarted = nativeGestureStarted
                )
            ) {
                synchronizeEdgeOutlines(screenWidth, screenHeight)
                Log.d(TAG, "native gesture input active mode=$resolvedInputMode")
                return
            }
            nativeTouchInteractionRuntimeFailed = true
            applyServiceRuntimeConfig()
            synchronizeGestureNavigationProtection(screenWidth, screenHeight)
            Log.w(TAG, "native gesture input unavailable, falling back to slim overlay")
        }
        stopNativeEdgeGestures()
        EdgeSide.entries.forEach { side ->
            preferences.edgeConfigs(side).forEach { config ->
                addEdgeGestureOverlay(config, screenWidth, screenHeight)
            }
        }
        synchronizeEdgeOutlines(screenWidth, screenHeight)
    }

    private fun addEdgeGestureOverlay(
        config: EdgeZoneConfig,
        screenWidth: Int,
        screenHeight: Int,
        touchTargetDp: Int = gestureOverlayTouchTargetDp(config.thicknessDp)
    ) {
        if (!config.enabled) return
        val side = config.side
        val params = createLayoutParams(
            config = config,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            touchTargetDp = touchTargetDp
        )
        val backWaveAnchor = physicalEdgeBackWaveAnchor(
            side = side,
            screenWidthPx = screenWidth,
            y = params.y,
            height = params.height
        )
        val overlay = EdgeOverlayView(
            context = this,
            side = side,
            showTouchFeedback = false,
            visibleThicknessDp = gestureOverlayThicknessDp(config.thicknessDp),
            swipeThresholdDp = preferences.shortPullThresholdDp,
            longSwipeThresholdDp = preferences.longPullThresholdDp,
            minVerticalSwipeDistancePx = 96f * resources.displayMetrics.density,
            onGesture = { type, data ->
                Log.d(TAG, "gesture side=${side.id} type=${type.id}")
                executeConfiguredGestureAction(
                    preferences.actionFor(side, type),
                    data.toScreenCoordinates(params.x, params.y)
                )
            },
            onGestureProgress = { data ->
                handleGesturePreview(
                    side = side,
                    data = data.toScreenCoordinates(params.x, params.y)
                )
            },
            onGestureEnd = ::handleGesturePreviewEnd,
            onBackGestureProgress = { progress ->
                handleBackGestureProgress(
                    side,
                    backWaveAnchor,
                    progress.toScreenCoordinates(params.y)
                )
            },
            onBackGestureCommit = { progress, data ->
                handleBackGestureCommit(
                    side,
                    progress,
                    data.toScreenCoordinates(params.x, params.y)
                )
            },
            onBackGestureEnd = { progress ->
                handleBackGestureEnd(
                    side,
                    backWaveAnchor,
                    progress.toScreenCoordinates(params.y)
                )
            },
            onBackGestureCancel = {
                if (::backWaveOverlayController.isInitialized) {
                    backWaveOverlayController.dismiss()
                }
                backWavePresentedForGesture = false
                gestureHintOverlayController.hide()
            }
        )
        runCatching {
            windowManager.addView(overlay, params)
            overlays[OverlayKey(side, config.zoneId)] = overlay
            Log.d(
                TAG,
                "overlay added side=${side.id} zone=${config.zoneId} width=${params.width} height=${params.height} x=${params.x} y=${params.y}"
            )
        }.onFailure {
            Log.e(TAG, "failed to add overlay side=${side.id} zone=${config.zoneId}", it)
        }
    }

    private fun handleBackGestureProgress(
        side: EdgeSide,
        anchor: BackWaveAnchor,
        progress: BackGestureProgress
    ) {
        if (!shouldShowBackWave(side, progress.gestureType)) {
            if (::backWaveOverlayController.isInitialized) {
                backWaveOverlayController.dismiss()
            }
            backWavePresentedForGesture = false
            selectedSideFunctionIndex = null
            return
        }
        val items = sideFunctionItems()
        selectedSideFunctionIndex = sideFunctionSelectionIndex(
            progress = progress,
            itemCount = items.size,
            density = resources.displayMetrics.density,
            viewportHeightPx = resources.displayMetrics.heightPixels.toFloat()
        )
        val actionIds = SideFunctionConfig.fromCustomActionIds(
            preferences.sideFunctionCustomActionIds
        ).allActionIds
        selectedSideFunctionIndex
            ?.let(actionIds::getOrNull)
            ?.let { actionId -> gestureHintOverlayController.show(gestureHintLabel(actionId)) }
        val panelIsPresented = backWaveOverlayController.update(progress, anchor, items)
        if (
            side == EdgeSide.RIGHT &&
            shouldVibrateForSideFunctionPanelOpen(backWavePresentedForGesture, panelIsPresented)
        ) {
            actionExecutor.performHapticFeedback()
        }
        backWavePresentedForGesture = panelIsPresented
    }

    private fun handleBackGestureCommit(
        side: EdgeSide,
        progress: BackGestureProgress,
        data: GestureData
    ): Boolean {
        val optionWasPresented = backWavePresentedForGesture
        backWavePresentedForGesture = false
        val index = selectedSideFunctionIndex
        selectedSideFunctionIndex = null
        val ids = SideFunctionConfig.fromCustomActionIds(preferences.sideFunctionCustomActionIds).allActionIds
        val selectedActionId = index?.let(ids::getOrNull)
        if (optionWasPresented && selectedActionId != null) {
            executeSideFunction(selectedActionId, data)
            return true
        }
        if (!::preferences.isInitialized) return false
        val action = preferences.actionFor(side, progress.gestureType)
        if (action == GestureAction.None) return false
        executeConfiguredGestureAction(action, data)
        return true
    }

    private fun handleBackGestureEnd(
        side: EdgeSide,
        anchor: BackWaveAnchor,
        progress: BackGestureProgress
    ) {
        if (!shouldShowBackWave(side, progress.gestureType)) {
            if (::backWaveOverlayController.isInitialized) {
                backWaveOverlayController.dismiss()
            }
            backWavePresentedForGesture = false
            selectedSideFunctionIndex = null
            return
        }
        backWaveOverlayController.finish(progress, anchor, sideFunctionItems())
    }

    private fun handleMeteorGestureProgress(side: EdgeSide, data: GestureData) {
        if (!::preferences.isInitialized || !::meteorSwipeEffectController.isInitialized) return
        val density = resources.displayMetrics.density
        val minDistancePx = nativeGestureThresholdPx(preferences.shortPullThresholdDp, density)
        val dx = data.endX - data.startX
        val dy = data.endY - data.startY
        if (!isMeteorPreviewGesture(side, dx, dy, minDistancePx)) {
            meteorSwipeEffectController.dismissImmediately()
            return
        }
        meteorSwipeEffectController.update(
            startX = data.startX,
            startY = data.startY,
            endX = data.endX,
            endY = data.endY,
            progress = meteorPreviewProgress(dx, dy, minDistancePx)
        )
    }

    private fun handleMeteorGestureEnd() {
        if (::meteorSwipeEffectController.isInitialized) {
            meteorSwipeEffectController.dismissAnimated()
        }
    }

    private fun handleGesturePreview(side: EdgeSide, data: GestureData) {
        handleMeteorGestureProgress(side, data)
        val action = gestureHintClassifier.classify(
            side = side,
            dx = data.endX - data.startX,
            dy = data.endY - data.startY
        )?.let { preferences.actionFor(side, it) } ?: GestureAction.None
        if (action == GestureAction.None) {
            gestureHintOverlayController.hide()
        } else {
            gestureHintOverlayController.show(gestureHintLabel(action))
        }
    }

    private fun handleGesturePreviewEnd() {
        handleMeteorGestureEnd()
        gestureHintOverlayController.hide()
    }

    private fun gestureHintLabel(action: GestureAction): String = when (action) {
        GestureAction.Back -> "返回"
        GestureAction.Home -> "主页"
        GestureAction.Recents -> "最近任务"
        GestureAction.Notifications -> "通知栏"
        GestureAction.QuickSettings -> "快捷设置"
        GestureAction.Screenshot -> "截图"
        GestureAction.LockScreen -> "锁屏"
        GestureAction.VolumeUp -> "音量增加"
        GestureAction.VolumeDown -> "音量减少"
        GestureAction.ExpandFloatingChat -> "展开聊天"
        GestureAction.CollapseFloatingChat -> "收起聊天"
        GestureAction.PlayVideo -> "显示视频"
        is GestureAction.LaunchApp -> runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(action.packageName, 0)).toString()
        }.getOrDefault(action.packageName)
        GestureAction.None -> "无动作"
    }

    private fun gestureHintLabel(actionId: String): String {
        return if (actionId == SideFunctionConfig.CLOSE_ALL_ACTION_ID) {
            "关闭所有"
        } else {
            gestureHintLabel(GestureAction.fromId(actionId))
        }
    }

    private fun handleFloatingChatEdgeGesture(
        side: EdgeSide,
        gestureType: GestureType,
        data: GestureData
    ) {
        if (!::preferences.isInitialized || !::actionExecutor.isInitialized) return
        Log.d(TAG, "floating chat internal gesture side=${side.id} type=${gestureType.id}")
        executeConfiguredGestureAction(preferences.actionFor(side, gestureType), data)
    }

    private fun handleFloatingChatBottomGesture(
        gestureType: BottomGestureBarGestureType,
        data: GestureData
    ) {
        if (!::preferences.isInitialized || !::actionExecutor.isInitialized) return
        Log.d(TAG, "floating chat internal bottom gesture type=${gestureType.id}")
        executeConfiguredGestureAction(preferences.bottomGestureBarActionFor(gestureType), data)
    }

    private fun executeConfiguredGestureAction(action: GestureAction, data: GestureData) {
        if (shouldRestoreFloatingChatFromExternalActivity(action, floatingChatExternalActivityVisible)) {
            Log.d(TAG, "restore floating chat from external activity gesture")
            setFloatingChatExternalActivityVisible(false)
        }
        actionExecutor.execute(action, data)
    }

    private fun sideFunctionItems(): List<SideFunctionPanelItem> {
        val ids = SideFunctionConfig.fromCustomActionIds(preferences.sideFunctionCustomActionIds).allActionIds
        val labels = sideFunctionPanelLabels(preferences.sideFunctionCustomActionIds) { actionId ->
            when (val action = GestureAction.fromId(actionId)) {
                GestureAction.Home -> "主页"
                GestureAction.Recents -> "最近任务"
                GestureAction.Notifications -> "通知栏"
                GestureAction.QuickSettings -> "快捷设置"
                GestureAction.Screenshot -> "截图"
                GestureAction.LockScreen -> "锁屏"
                GestureAction.VolumeUp -> "音量增加"
                GestureAction.VolumeDown -> "音量减少"
                GestureAction.ExpandFloatingChat -> "展开聊天"
                GestureAction.CollapseFloatingChat -> "收起聊天"
                GestureAction.PlayVideo -> "显示视频"
                is GestureAction.LaunchApp -> {
                    val name = runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(action.packageName, 0)
                        ).toString()
                    }.getOrDefault(action.packageName)
                    name
                }
                else -> action.id
            }
        }
        return ids.zip(labels).map { (actionId, label) ->
            val action = GestureAction.fromId(actionId)
            val icon = (action as? GestureAction.LaunchApp)?.let { launch ->
                runCatching { packageManager.getApplicationIcon(launch.packageName) }.getOrNull()
            }
            SideFunctionPanelItem(label, icon)
        }
    }

    private fun executeSideFunction(actionId: String, data: GestureData) {
        if (actionId == SideFunctionConfig.CLOSE_ALL_ACTION_ID) {
            actionExecutor.performHapticFeedback()
            floatingChatOverlayController.dismiss()
            videoDemoOverlayController.dismissImmediately()
            meteorSwipeEffectController.dismissImmediately()
            backWaveOverlayController.dismiss()
            return
        }
        executeConfiguredGestureAction(GestureAction.fromId(actionId), data)
    }

    private fun handleFloatingChatBackGestureProgress(
        side: EdgeSide,
        progress: BackGestureProgress
    ) {
        handleBackGestureProgress(side, floatingChatBackWaveAnchor(side), progress)
    }

    private fun handleFloatingChatBackGestureCommit(
        side: EdgeSide,
        progress: BackGestureProgress,
        data: GestureData
    ): Boolean {
        return handleBackGestureCommit(side, progress, data)
    }

    private fun handleFloatingChatBackGestureEnd(
        side: EdgeSide,
        progress: BackGestureProgress
    ) {
        handleBackGestureEnd(side, floatingChatBackWaveAnchor(side), progress)
    }

    private fun handleFloatingChatBackGestureCancel() {
        if (::backWaveOverlayController.isInitialized) {
            backWaveOverlayController.dismiss()
        }
        backWavePresentedForGesture = false
        selectedSideFunctionIndex = null
        if (::gestureHintOverlayController.isInitialized) {
            gestureHintOverlayController.hide()
        }
    }

    private fun handleFloatingChatExpandedChanged(expanded: Boolean) {
        floatingChatExpanded = expanded
        scheduleBottomGestureBarZOrderRefresh()
        val (screenWidth, screenHeight) = currentDisplaySize()
        synchronizeGestureNavigationProtection(screenWidth, screenHeight)
        if (floatingChatOwnsGestureSurface(floatingChatExpanded, floatingChatExternalActivityVisible)) {
            removeEdgeGestureOverlays()
            syncBottomGestureBar()
            applyServiceRuntimeConfig()
            if (resolvedGestureInputMode() == ResolvedGestureInputMode.NativeTouchInteraction) {
                if (startNativeEdgeGestures(screenWidth, screenHeight)) {
                    Log.d(TAG, "native gesture input kept while floating chat is expanded")
                    return
                }
                nativeTouchInteractionRuntimeFailed = true
                applyServiceRuntimeConfig()
                synchronizeGestureNavigationProtection(screenWidth, screenHeight)
            }
            stopNativeEdgeGestures()
            Log.d(TAG, "using floating chat internal edge input fallback")
            return
        }

        removeEdgeGestureOverlays()
        syncBottomGestureBar()
        applyServiceRuntimeConfig()
        if (resolvedGestureInputMode() != ResolvedGestureInputMode.NativeTouchInteraction) {
            requestOverlayRefresh()
            return
        }
        if (startNativeEdgeGestures(screenWidth, screenHeight)) {
            Log.d(TAG, "native gesture input resumed after floating chat collapse")
        } else {
            nativeTouchInteractionRuntimeFailed = true
            applyServiceRuntimeConfig()
            synchronizeGestureNavigationProtection(screenWidth, screenHeight)
            requestOverlayRefresh()
        }
    }

    private fun startNativeEdgeGestures(screenWidth: Int, screenHeight: Int): Boolean {
        if (Build.VERSION.SDK_INT < NATIVE_TOUCH_INTERACTION_MIN_SDK) return false
        val density = resources.displayMetrics.density
        val controller = nativeEdgeGestureController ?: NativeEdgeGestureController(
            service = this,
            mainHandler = mainHandler,
            onGesture = { side, type, data ->
                Log.d(TAG, "native gesture side=${side.id} type=${type.id}")
                executeConfiguredGestureAction(preferences.actionFor(side, type), data)
            },
            onGestureProgress = ::handleGesturePreview,
            onGestureEnd = ::handleGesturePreviewEnd,
            onBottomGesture = { type, data ->
                Log.d(TAG, "native bottom gesture type=${type.id}")
                executeConfiguredGestureAction(preferences.bottomGestureBarActionFor(type), data)
            },
            onBackGestureProgress = { intercept, progress ->
                handleBackGestureProgress(
                    intercept.side,
                    backWaveAnchorForNativeIntercept(intercept, screenWidth),
                    progress
                )
            },
            onBackGestureCommit = { intercept, progress, data ->
                handleBackGestureCommit(intercept.side, progress, data)
            },
            onBackGestureEnd = { intercept, progress ->
                handleBackGestureEnd(
                    intercept.side,
                    backWaveAnchorForNativeIntercept(intercept, screenWidth),
                    progress
                )
            },
            onBackGestureCancel = ::handleFloatingChatBackGestureCancel
        ).also { nativeEdgeGestureController = it }
        controller.setFloatingChatExpanded(
            floatingChatOwnsGestureSurface(floatingChatExpanded, floatingChatExternalActivityVisible)
        )
        return controller.start(
            NativeEdgeGestureConfig(
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                density = density,
                leftConfigs = preferences.edgeConfigs(EdgeSide.LEFT),
                rightConfigs = preferences.edgeConfigs(EdgeSide.RIGHT),
                shortThresholdPx = nativeGestureThresholdPx(preferences.shortPullThresholdDp, density),
                longThresholdPx = nativeGestureThresholdPx(preferences.longPullThresholdDp, density),
                bottomGestureWidthDp = preferences.bottomGestureBarWidthDp
            )
        )
    }

    private fun stopNativeEdgeGestures() {
        nativeEdgeGestureController?.stop()
        handleMeteorGestureEnd()
    }

    private fun floatingChatBackWaveAnchor(side: EdgeSide): BackWaveAnchor {
        val (screenWidth, screenHeight) = currentDisplaySize()
        return physicalEdgeBackWaveAnchor(side, screenWidth, y = 0, height = screenHeight)
    }

    private fun shouldShowBackWave(side: EdgeSide, gestureType: GestureType): Boolean {
        if (!::preferences.isInitialized) return false
        return preferences.actionFor(side, gestureType) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_INWARD_SHORT) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_INWARD_LONG) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_DIAGONAL_UP_SHORT) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_DIAGONAL_UP_LONG) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_DIAGONAL_DOWN_SHORT) != GestureAction.None ||
            preferences.actionFor(side, GestureType.PULL_DIAGONAL_DOWN_LONG) != GestureAction.None
    }

    private fun applyServiceRuntimeConfig() {
        if (!::preferences.isInitialized) return
        val info = serviceInfo ?: return
        val needsWindowTracking = preferences.disableWhenKeyboardShown
        info.eventTypes = if (needsWindowTracking) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        info.flags = if (needsWindowTracking) {
            info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS.inv()
        }
        val wantsNativeTouch = shouldRequestNativeTouchInteraction(
            eligibility = NativeTouchInteractionEligibility(
                sdkInt = Build.VERSION.SDK_INT,
                requestedMode = preferences.gestureInputMode,
                runtimeFailed = nativeTouchInteractionRuntimeFailed,
                globalEnabled = preferences.globalEnabled,
                screenInteractive = screenInteractiveState.isInteractive,
                packageBlocked = currentPackageBlocked,
                paused = preferences.isTemporarilyPaused() || preferences.isQuietHoursActive(),
                landscapeDisabled = preferences.disableInLandscape &&
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                keyboardDisabled = preferences.disableWhenKeyboardShown && isKeyboardVisible
            ),
            floatingChatExpanded = floatingChatExpanded,
            externalActivityVisible = floatingChatExternalActivityVisible
        )
        info.flags = if (wantsNativeTouch) {
            info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv() and
                AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS.inv()
        }
        serviceInfo = info
    }

    private fun resolvedGestureInputMode(): ResolvedGestureInputMode {
        if (!::preferences.isInitialized) return ResolvedGestureInputMode.SlimOverlayFallback
        return resolveGestureInputMode(
            requestedMode = preferences.gestureInputMode,
            sdkInt = Build.VERSION.SDK_INT,
            nativeTouchInteractionAvailable = Build.VERSION.SDK_INT >= NATIVE_TOUCH_INTERACTION_MIN_SDK &&
                !nativeTouchInteractionRuntimeFailed,
            secureTakeoverApplied = nativeBackTakeoverApplied
        )
    }

    private fun schedulePauseExpiryIfNeeded() {
        mainHandler.removeCallbacks(pauseExpiredRunnable)
        val remainingMs = preferences.pausedUntilEpochMs - System.currentTimeMillis()
        if (remainingMs > 0L) {
            mainHandler.postDelayed(pauseExpiredRunnable, remainingMs.coerceAtMost(MAX_PAUSE_TIMER_DELAY_MS))
        }
    }

    private fun createLayoutParams(
        config: EdgeZoneConfig,
        screenWidth: Int,
        screenHeight: Int,
        touchTargetDp: Int = gestureOverlayTouchTargetDp(config.thicknessDp)
    ): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val sanitized = config.sanitized()
        val requestedWidth = (touchTargetDp * density).roundToInt().coerceAtLeast(1)
        val width = requestedWidth.coerceIn(1, screenWidth.coerceAtLeast(1))
        val heightPercent = 100 - sanitized.topInsetPercent - sanitized.bottomInsetPercent
        val height = (screenHeight * heightPercent.coerceIn(EdgeZoneConfig.MIN_LENGTH_PERCENT, 100) / 100).coerceAtLeast(1)
        val x = edgeTouchX(config.side, screenWidth, width)
        val y = (screenHeight * sanitized.topInsetPercent / 100).coerceIn(0, screenHeight - height)

        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun removeAllOverlays(restoreNavigationProtection: Boolean = true) {
        stopNativeEdgeGestures()
        if (::gestureHintOverlayController.isInitialized) {
            gestureHintOverlayController.hide()
        }
        removeEdgeGestureOverlays()
        removeEdgeOutlines()
        if (::bottomGestureBarIndicatorController.isInitialized) {
            bottomGestureBarIndicatorController.dismiss()
        }
        if (restoreNavigationProtection) {
            clearGestureNavigationProtection()
        } else if (::nativeGestureExclusionOverlayController.isInitialized) {
            nativeGestureExclusionOverlayController.remove()
        }
    }

    private fun removeEdgeGestureOverlays() {
        if (::meteorSwipeEffectController.isInitialized) {
            meteorSwipeEffectController.dismissImmediately()
        }
        if (::backWaveOverlayController.isInitialized) {
            runCatching { backWaveOverlayController.dismiss() }
                .onFailure { Log.w(TAG, "failed to dismiss back wave overlay", it) }
        }
        if (!::windowManager.isInitialized) {
            overlays.clear()
            return
        }
        overlays.values.forEach { overlay ->
            overlay.cancelPendingCallbacks()
            runCatching { windowManager.removeView(overlay) }
                .onFailure { Log.w(TAG, "failed to remove gesture overlay", it) }
        }
        overlays.clear()
    }

    private fun synchronizeEdgeOutlines(screenWidth: Int, screenHeight: Int) {
        removeEdgeOutlines()
        if (!preferences.showIndicators) return
        EdgeSide.entries.forEach { side ->
            preferences.edgeConfigs(side).forEach { config ->
                addEdgeOutline(config, screenWidth, screenHeight)
            }
        }
    }

    private fun addEdgeOutline(
        config: EdgeZoneConfig,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (!config.enabled) return
        val params = createEdgeOutlineLayoutParams(config, screenWidth, screenHeight)
        val outline = EdgeOutlineView(
            context = this,
            side = config.side,
            zoneId = config.zoneId
        )
        val key = OverlayKey(config.side, config.zoneId)
        runCatching { windowManager.addView(outline, params) }
            .onSuccess { edgeOutlines[key] = outline }
            .onFailure { error -> Log.w(TAG, "failed to add edge outline", error) }
    }

    private fun updatePersistentEdgeOutline(config: EdgeZoneConfig) {
        val key = OverlayKey(config.side, config.zoneId)
        if (!config.enabled) {
            edgeOutlines.remove(key)?.let { outline ->
                runCatching { windowManager.removeView(outline) }
                    .onFailure { error -> Log.w(TAG, "failed to remove persistent edge outline", error) }
            }
            return
        }

        val (screenWidth, screenHeight) = currentDisplaySize()
        val existingOutline = edgeOutlines[key]
        if (existingOutline == null) {
            addEdgeOutline(config, screenWidth, screenHeight)
            return
        }
        val params = createEdgeOutlineLayoutParams(config, screenWidth, screenHeight)
        runCatching { windowManager.updateViewLayout(existingOutline, params) }
            .onFailure { error -> Log.w(TAG, "failed to update persistent edge outline", error) }
    }

    private fun createEdgeOutlineLayoutParams(
        config: EdgeZoneConfig,
        screenWidth: Int,
        screenHeight: Int
    ): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val sanitized = config.sanitized()
        val insetPx = (sanitizeEdgeInsetDp(sanitized.edgeInsetDp) * density)
            .roundToInt()
            .coerceIn(0, (screenWidth - 1).coerceAtLeast(0))
        val width = (gestureOverlayThicknessDp(config.thicknessDp)
            .coerceAtLeast(OUTLINE_MIN_VISIBLE_WIDTH_DP) * density)
            .roundToInt()
            .coerceAtLeast(1)
        val placement = edgeOutlinePlacement(config.side, screenWidth, width, insetPx)
        val touchParams = createLayoutParams(config, screenWidth, screenHeight)
        return WindowManager.LayoutParams(
            placement.width,
            touchParams.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = placement.x
            y = touchParams.y
        }
    }

    private fun removeEdgeOutlines() {
        if (!::windowManager.isInitialized) {
            edgeOutlines.clear()
            return
        }
        edgeOutlines.values.forEach { outline ->
            runCatching { windowManager.removeView(outline) }
                .onFailure { Log.w(TAG, "failed to remove edge outline", it) }
        }
        edgeOutlines.clear()
    }

    private fun removeEdgeConfigAdjustmentPreview() {
        val outline = edgeConfigAdjustmentOutline ?: return
        edgeConfigAdjustmentOutline = null
        edgeConfigAdjustmentPreviewSide = null
        edgeConfigAdjustmentPreviewZoneId = null
        runCatching { windowManager.removeViewImmediate(outline) }
            .onFailure { Log.w(TAG, "failed to remove edge configuration preview", it) }
    }

    private fun syncBottomGestureBar() {
        if (!::bottomGestureBarOverlayController.isInitialized) return
        val barVisible = bottomGestureBarExternalOverlayVisibleForFloatingChat(floatingChatExpanded)
        if (barVisible) {
            bottomGestureBarOverlayController.show()
        } else {
            bottomGestureBarOverlayController.remove()
        }
        if (::bottomGestureBarIndicatorController.isInitialized) {
            if (shouldShowBottomGestureBarIndicator(preferences.showIndicators, barVisible)) {
                bottomGestureBarIndicatorController.show(preferences.bottomGestureBarWidthDp)
            } else {
                bottomGestureBarIndicatorController.dismiss()
            }
        }
    }

    private fun scheduleBottomGestureBarZOrderRefresh() {
        mainHandler.post {
            if (::bottomGestureBarOverlayController.isInitialized) {
                if (bottomGestureBarExternalOverlayVisibleForFloatingChat(floatingChatExpanded)) {
                    bottomGestureBarOverlayController.recreate()
                } else {
                    bottomGestureBarOverlayController.remove()
                }
            }
        }
    }

    private fun showFloatingChatOverlayIfAllowed() {
        if (!::floatingChatOverlayController.isInitialized) {
            Log.d(TAG, "skip floating chat overlay controller not initialized")
            return
        }
        if (!screenInteractiveState.isInteractive) {
            Log.d(TAG, "skip floating chat overlay interactive=false")
            return
        }
        runCatching { floatingChatOverlayController.show() }
            .onFailure { Log.e(TAG, "failed to show floating chat overlay", it) }
    }

    private fun removeFloatingChatOverlay() {
        if (::floatingChatOverlayController.isInitialized) {
            runCatching { floatingChatOverlayController.dismiss() }
                .onFailure { Log.w(TAG, "failed to remove floating chat overlay", it) }
        }
    }

    private fun refreshKeyboardVisibilityFor(event: AccessibilityEvent): Boolean {
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return false
        }
        val visible = if (preferences.disableWhenKeyboardShown) queryKeyboardVisibility() else false
        if (visible == isKeyboardVisible) return false
        isKeyboardVisible = visible
        Log.d(TAG, "keyboard visible=$visible")
        return true
    }

    private fun queryKeyboardVisibility(): Boolean {
        return runCatching {
            windows.any { window -> window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }.onFailure {
            Log.w(TAG, "failed to query accessibility windows", it)
        }.getOrDefault(false)
    }

    private fun isInputMethodEvent(event: AccessibilityEvent): Boolean {
        return runCatching {
            windows.any { window ->
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && window.id == event.windowId
            }
        }.getOrDefault(false)
    }

    private fun currentDisplaySize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
    }

    companion object {
        private const val TAG = "UbikiTouch"
        private const val OVERLAY_REFRESH_DEBOUNCE_MS = 120L
        private const val SCREEN_ON_RESUME_DELAY_MS = 800L
        private const val USER_PRESENT_RESUME_DELAY_MS = 120L
        private const val ACCESSIBILITY_EVENT_RESUME_DELAY_MS = 180L
        private const val ACCESSIBILITY_DESTROYED_RECOVERY_DELAY_MS = 3_000L
        private const val MAX_PAUSE_TIMER_DELAY_MS = 24L * 60L * 60L * 1000L

        var instance: UbikiAccessibilityService? = null
            private set
        var isRunning: Boolean = false
            private set
        var currentForegroundPackage: String? = null
            private set
    }

    private data class OverlayKey(
        val side: EdgeSide,
        val zoneId: Int
    )
}

internal fun gestureOverlayThicknessDp(configuredThicknessDp: Int): Int {
    return configuredThicknessDp.coerceIn(EdgeZoneConfig.MIN_THICKNESS_DP, EdgeZoneConfig.MAX_THICKNESS_DP)
}

internal fun shouldShowEdgeConfigAdjustmentPreview(showIndicators: Boolean): Boolean = !showIndicators

internal fun shouldUpdatePersistentEdgeOutline(showIndicators: Boolean): Boolean = showIndicators

internal fun canReuseEdgeConfigAdjustmentPreview(
    currentSide: EdgeSide?,
    currentZoneId: Int?,
    nextConfig: EdgeZoneConfig
): Boolean = currentSide == nextConfig.side && currentZoneId == nextConfig.zoneId

internal fun gestureOverlayTouchTargetDp(configuredThicknessDp: Int): Int {
    return gestureOverlayThicknessDp(configuredThicknessDp)
        .coerceAtLeast(MIN_USABLE_EDGE_GESTURE_TOUCH_TARGET_DP)
}

private const val MIN_USABLE_EDGE_GESTURE_TOUCH_TARGET_DP = 8
private const val OUTLINE_MIN_VISIBLE_WIDTH_DP = 14
private const val EDGE_CONFIG_ADJUSTMENT_PREVIEW_HIDE_DELAY_MS = 1_000L

internal fun edgeTouchX(side: EdgeSide, screenWidthPx: Int, touchWidthPx: Int): Int {
    val width = touchWidthPx.coerceIn(1, screenWidthPx.coerceAtLeast(1))
    return when (side) {
        EdgeSide.LEFT -> 0
        EdgeSide.RIGHT -> (screenWidthPx - width).coerceAtLeast(0)
    }
}

internal fun shouldConsumeBackGestureOption(
    option: BackGestureOption,
    optionWasPresented: Boolean
): Boolean {
    return optionWasPresented && option != BackGestureOption.None
}

internal fun shouldVibrateForSideFunctionPanelOpen(
    wasPresented: Boolean,
    isPresented: Boolean
): Boolean = !wasPresented && isPresented

internal fun sideFunctionSelectionIndex(
    progress: BackGestureProgress,
    itemCount: Int,
    density: Float,
    viewportHeightPx: Float,
    verticalSafeInsetPx: Float = DEFAULT_SIDE_FUNCTION_VERTICAL_SAFE_INSET_DP * density
): Int? {
    val layout = sideFunctionLayout(itemCount, progress.progress, density)
    val firstBox = layout.boxes.firstOrNull() ?: return null
    val groupShiftY = sideFunctionGroupShiftY(
        layout = layout,
        startY = progress.startY,
        viewportHeightPx = viewportHeightPx,
        verticalSafeInsetPx = verticalSafeInsetPx
    )
    return layout.hitTest(
        inwardDistancePx = firstBox.centerInwardPx,
        verticalOffsetPx = progress.touchY - progress.startY - groupShiftY
    )
}

private fun Intent.addFloatingChatBridgeFlags(): Intent {
    return addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    )
}

internal fun GestureData.toScreenCoordinates(originX: Int, originY: Int): GestureData {
    return copy(
        startX = startX + originX,
        startY = startY + originY,
        endX = endX + originX,
        endY = endY + originY
    )
}

internal fun physicalEdgeBackWaveAnchor(
    side: EdgeSide,
    screenWidthPx: Int,
    y: Int,
    height: Int
): BackWaveAnchor {
    return BackWaveAnchor(
        side = side,
        edgeX = if (side == EdgeSide.LEFT) 0 else screenWidthPx.coerceAtLeast(0),
        y = y.coerceAtLeast(0),
        height = height.coerceAtLeast(1)
    )
}

private fun BackGestureProgress.toScreenCoordinates(originY: Int): BackGestureProgress {
    return copy(
        startY = startY + originY,
        touchY = touchY + originY
    )
}
