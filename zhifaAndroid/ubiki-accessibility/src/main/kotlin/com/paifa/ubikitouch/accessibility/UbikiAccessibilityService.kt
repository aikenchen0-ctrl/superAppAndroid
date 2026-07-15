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
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.core.overlay.ScreenInteractiveState
import com.paifa.ubikitouch.overlay.EdgeOverlayView

class UbikiAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: UbikiPreferences
    private lateinit var actionExecutor: UbikiActionExecutor
    private lateinit var backWaveOverlayController: BackWaveOverlayController
    private lateinit var bottomGestureBarOverlayController: BottomGestureBarOverlayController
    private lateinit var floatingChatOverlayController: FloatingChatOverlayController
    private lateinit var nativeBackGestureTakeoverController: NativeBackGestureTakeoverController
    private val overlays = mutableMapOf<OverlayKey, EdgeOverlayView>()
    private var nativeEdgeGestureController: NativeEdgeGestureController? = null
    private var nativeBackTakeoverApplied = false
    private var nativeTouchInteractionRuntimeFailed = false
    private var floatingChatExpanded = false
    private var floatingChatExternalActivityVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
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
        backWaveOverlayController = BackWaveOverlayController(this, windowManager)
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
        actionExecutor = UbikiActionExecutor(this, preferences.hapticFeedback, floatingChatOverlayController)
        bottomGestureBarOverlayController = BottomGestureBarOverlayController(
            context = this,
            windowManager = windowManager,
            preferences = preferences,
            onGesture = ::executeConfiguredGestureAction
        )
        screenInteractiveState.updateFromSystem(isDeviceInteractive())
        preferences.registerChangeListener(preferenceListener)
        registerScreenReceiver()
        applyServiceRuntimeConfig()
        applyNativeBackGestureTakeover()
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
        if (::bottomGestureBarOverlayController.isInitialized) {
            bottomGestureBarOverlayController.remove()
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
            removeAllOverlays()
            actionExecutor = UbikiActionExecutor(this, preferences.hapticFeedback, floatingChatOverlayController)
            applyNativeBackGestureTakeover()
            createOverlays()
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

    private fun applyNativeBackGestureTakeover() {
        if (!::nativeBackGestureTakeoverController.isInitialized) return
        val result = nativeBackGestureTakeoverController.applyTakeover()
        nativeBackTakeoverApplied = result.applied
        if (!result.applied) {
            Log.w(TAG, "native back gesture takeover not applied error=${result.errorMessage}")
        }
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
        val paused = preferences.isTemporarilyPaused()
        applyServiceRuntimeConfig()
        if (!preferences.globalEnabled || !screenInteractiveState.isInteractive || currentPackageBlocked || disabledByLandscape || disabledByKeyboard || paused) {
            stopNativeEdgeGestures()
            Log.d(
                TAG,
                "skip overlays enabled=${preferences.globalEnabled} interactive=${screenInteractiveState.isInteractive} blocked=$currentPackageBlocked landscape=$disabledByLandscape keyboard=$disabledByKeyboard paused=$paused"
            )
            return
        }

        if (!edgeGestureOverlayWindowsAllowed(floatingChatExpanded, floatingChatExternalActivityVisible)) {
            removeEdgeGestureOverlays()
            syncBottomGestureBar()
            val (screenWidth, screenHeight) = currentDisplaySize()
            if (resolvedGestureInputMode() == ResolvedGestureInputMode.NativeTouchInteraction &&
                startNativeEdgeGestures(screenWidth, screenHeight)
            ) {
                Log.d(TAG, "native gesture input active while floating chat is expanded")
                return
            }
            stopNativeEdgeGestures()
            Log.d(TAG, "use floating chat internal edge input fallback while floating chat is expanded")
            return
        }

        val (screenWidth, screenHeight) = currentDisplaySize()
        val resolvedInputMode = resolvedGestureInputMode()
        if (!shouldCreateGestureOverlayWindows(resolvedInputMode)) {
            if (startNativeEdgeGestures(screenWidth, screenHeight)) {
                Log.d(TAG, "native gesture input active mode=$resolvedInputMode")
                return
            }
            nativeTouchInteractionRuntimeFailed = true
            applyServiceRuntimeConfig()
            Log.w(TAG, "native gesture input unavailable, falling back to slim overlay")
        }
        stopNativeEdgeGestures()
        EdgeSide.entries.forEach { side ->
            preferences.edgeConfigs(side).forEach { config ->
                addEdgeGestureOverlay(config, screenWidth, screenHeight)
            }
        }
    }

    private fun addEdgeGestureOverlay(
        config: EdgeZoneConfig,
        screenWidth: Int,
        screenHeight: Int,
        touchTargetDp: Int = gestureOverlayTouchTargetDp(config.thicknessDp)
    ) {
        if (!config.enabled) return
        val side = config.side
        val params = createLayoutParams(config, screenWidth, screenHeight, touchTargetDp)
        val backWaveAnchor = BackWaveAnchor(
            side = side,
            y = params.y,
            height = params.height
        )
        val overlay = EdgeOverlayView(
            context = this,
            side = side,
            showIndicator = preferences.showIndicators,
            opacityPercent = preferences.overlayOpacity,
            visibleThicknessDp = gestureOverlayThicknessDp(config.thicknessDp),
            swipeThresholdDp = preferences.shortPullThresholdDp,
            longSwipeThresholdDp = preferences.longPullThresholdDp,
            onGesture = { type, data ->
                Log.d(TAG, "gesture side=${side.id} type=${type.id}")
                executeConfiguredGestureAction(preferences.actionFor(side, type), data)
            },
            onBackGestureProgress = { progress ->
                handleBackGestureProgress(side, backWaveAnchor, progress)
            },
            onBackGestureCommit = { progress, data ->
                handleBackGestureCommit(side, progress, data)
            },
            onBackGestureEnd = { progress ->
                handleBackGestureEnd(side, backWaveAnchor, progress)
            },
            onBackGestureCancel = {
                if (::backWaveOverlayController.isInitialized) {
                    backWaveOverlayController.dismiss()
                }
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
            return
        }
        backWaveOverlayController.update(progress, anchor)
    }

    private fun handleBackGestureCommit(
        side: EdgeSide,
        progress: BackGestureProgress,
        data: GestureData
    ): Boolean {
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
            return
        }
        backWaveOverlayController.finish(progress, anchor)
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
    }

    private fun handleFloatingChatExpandedChanged(expanded: Boolean) {
        floatingChatExpanded = expanded
        scheduleBottomGestureBarZOrderRefresh()
        if (floatingChatOwnsGestureSurface(floatingChatExpanded, floatingChatExternalActivityVisible)) {
            removeEdgeGestureOverlays()
            syncBottomGestureBar()
            applyServiceRuntimeConfig()
            if (resolvedGestureInputMode() == ResolvedGestureInputMode.NativeTouchInteraction) {
                val (screenWidth, screenHeight) = currentDisplaySize()
                if (startNativeEdgeGestures(screenWidth, screenHeight)) {
                    Log.d(TAG, "native gesture input kept while floating chat is expanded")
                    return
                }
                nativeTouchInteractionRuntimeFailed = true
                applyServiceRuntimeConfig()
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
        val (screenWidth, screenHeight) = currentDisplaySize()
        if (startNativeEdgeGestures(screenWidth, screenHeight)) {
            Log.d(TAG, "native gesture input resumed after floating chat collapse")
        } else {
            nativeTouchInteractionRuntimeFailed = true
            applyServiceRuntimeConfig()
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
            onBottomGesture = { type, data ->
                Log.d(TAG, "native bottom gesture type=${type.id}")
                executeConfiguredGestureAction(preferences.bottomGestureBarActionFor(type), data)
            },
            onBackGestureProgress = ::handleFloatingChatBackGestureProgress,
            onBackGestureCommit = ::handleFloatingChatBackGestureCommit,
            onBackGestureEnd = ::handleFloatingChatBackGestureEnd,
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
    }

    private fun floatingChatBackWaveAnchor(side: EdgeSide): BackWaveAnchor {
        return BackWaveAnchor(
            side = side,
            y = 0,
            height = currentDisplaySize().second
        )
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
                paused = preferences.isTemporarilyPaused(),
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
        val width = (touchTargetDp * density).toInt().coerceAtLeast(1)
        val heightPercent = 100 - sanitized.topInsetPercent - sanitized.bottomInsetPercent
        val height = (screenHeight * heightPercent.coerceIn(EdgeZoneConfig.MIN_LENGTH_PERCENT, 100) / 100).coerceAtLeast(1)
        val x = when (config.side) {
            EdgeSide.LEFT -> 0
            EdgeSide.RIGHT -> screenWidth - width
        }
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

    private fun removeAllOverlays() {
        stopNativeEdgeGestures()
        removeEdgeGestureOverlays()
    }

    private fun removeEdgeGestureOverlays() {
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

    private fun syncBottomGestureBar() {
        if (!::bottomGestureBarOverlayController.isInitialized) return
        if (bottomGestureBarExternalOverlayVisibleForFloatingChat(floatingChatExpanded)) {
            bottomGestureBarOverlayController.show()
        } else {
            bottomGestureBarOverlayController.remove()
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

internal fun gestureOverlayTouchTargetDp(configuredThicknessDp: Int): Int {
    return gestureOverlayThicknessDp(configuredThicknessDp)
        .coerceAtLeast(MIN_USABLE_EDGE_GESTURE_TOUCH_TARGET_DP)
}

private const val MIN_USABLE_EDGE_GESTURE_TOUCH_TARGET_DP = 8

private fun Intent.addFloatingChatBridgeFlags(): Intent {
    return addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    )
}
