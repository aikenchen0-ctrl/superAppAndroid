package com.paifa.ubikitouch.accessibility

import android.content.Context
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureType

class UbikiTouch private constructor(context: Context) {
    private val preferences = UbikiPreferences(context.applicationContext)

    var globalEnabled: Boolean
        get() = preferences.globalEnabled
        set(value) {
            preferences.globalEnabled = value
            requestOverlayRefresh()
        }

    val isServiceRunning: Boolean
        get() = UbikiAccessibilityService.isRunning

    fun getAction(side: EdgeSide, gestureType: GestureType): GestureAction {
        return preferences.actionFor(side, gestureType)
    }

    fun setAction(side: EdgeSide, gestureType: GestureType, action: GestureAction) {
        preferences.setAction(side, gestureType, action)
        requestOverlayRefresh()
    }

    fun getEdgeConfig(side: EdgeSide): EdgeZoneConfig {
        return preferences.edgeConfig(side)
    }

    fun getEdgeConfigs(side: EdgeSide): List<EdgeZoneConfig> {
        return preferences.edgeConfigs(side)
    }

    fun setEdgeConfig(config: EdgeZoneConfig) {
        preferences.setEdgeConfig(config)
        requestOverlayRefresh()
    }

    fun setEdgeConfigs(side: EdgeSide, configs: List<EdgeZoneConfig>) {
        preferences.setEdgeConfigs(side, configs)
        requestOverlayRefresh()
    }

    fun setOverlayAppearance(
        showIndicators: Boolean,
        opacityPercent: Int,
        swipeThresholdDp: Int,
        hapticFeedback: Boolean,
        disableInLandscape: Boolean,
        disableWhenKeyboardShown: Boolean = preferences.disableWhenKeyboardShown
    ) {
        preferences.showIndicators = showIndicators
        preferences.overlayOpacity = opacityPercent
        preferences.swipeThresholdDp = swipeThresholdDp
        preferences.hapticFeedback = hapticFeedback
        preferences.disableInLandscape = disableInLandscape
        preferences.disableWhenKeyboardShown = disableWhenKeyboardShown
        requestOverlayRefresh()
    }

    fun setPullDistanceThresholds(shortThresholdDp: Int, longThresholdDp: Int) {
        preferences.shortPullThresholdDp = shortThresholdDp
        preferences.longPullThresholdDp = longThresholdDp
        requestOverlayRefresh()
    }

    fun getBlockedPackages(): Set<String> {
        return preferences.blockedPackages
    }

    fun addBlockedPackage(packageName: String) {
        preferences.addBlockedPackage(packageName)
        requestOverlayRefresh()
    }

    fun removeBlockedPackage(packageName: String) {
        preferences.removeBlockedPackage(packageName)
        requestOverlayRefresh()
    }

    fun pauseFor(durationMs: Long) {
        preferences.pauseFor(durationMs)
        requestOverlayRefresh()
    }

    fun resumeNow() {
        preferences.resumeNow()
        requestOverlayRefresh()
    }

    fun getPausedUntilEpochMs(): Long {
        return preferences.pausedUntilEpochMs
    }

    fun requestOverlayRefresh() {
        UbikiAccessibilityService.instance?.requestOverlayRefresh()
    }

    companion object {
        fun create(context: Context): UbikiTouch = UbikiTouch(context)
    }
}
