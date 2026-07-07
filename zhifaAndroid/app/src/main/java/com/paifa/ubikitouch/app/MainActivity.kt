package com.paifa.ubikitouch.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.UbikiAccessibilityService
import com.paifa.ubikitouch.accessibility.UbikiPreferences
import com.paifa.ubikitouch.accessibility.defaultFloatingChatBackgroundColorRgb
import com.paifa.ubikitouch.accessibility.floatingChatBackgroundColorPresetRgbs
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundOpacityPercent
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBackgroundColorRgb
import com.paifa.ubikitouch.accessibility.sanitizeFloatingChatBlurRadiusDp
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureActionCatalog
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.core.model.gestureMappingOrder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = UbikiPreferences(this)
        val launchableApps = loadLaunchableApps()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        preferences = preferences,
                        launchableApps = launchableApps,
                        openAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        openBatteryOptimizationSettings = {
                            openBatteryOptimizationSettings()
                        },
                        openAppBackgroundSettings = {
                            openAppBackgroundSettings()
                        }
                    )
                }
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", packageName, null)
                )
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .onFailure { openAppBackgroundSettings() }
    }

    private fun openAppBackgroundSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

@Composable
private fun MainScreen(
    preferences: UbikiPreferences,
    launchableApps: List<LaunchableApp>,
    openAccessibilitySettings: () -> Unit,
    openBatteryOptimizationSettings: () -> Unit,
    openAppBackgroundSettings: () -> Unit
) {
    var isRunning by remember { mutableStateOf(UbikiAccessibilityService.isRunning) }
    var globalEnabled by remember { mutableStateOf(preferences.globalEnabled) }
    var showIndicators by remember { mutableStateOf(preferences.showIndicators) }
    var hapticFeedback by remember { mutableStateOf(preferences.hapticFeedback) }
    var disableInLandscape by remember { mutableStateOf(preferences.disableInLandscape) }
    var disableWhenKeyboardShown by remember { mutableStateOf(preferences.disableWhenKeyboardShown) }
    var overlayOpacity by remember { mutableIntStateOf(preferences.overlayOpacity) }
    var floatingChatFrostedBackgroundEnabled by remember {
        mutableStateOf(preferences.floatingChatFrostedBackgroundEnabled)
    }
    var floatingChatBackgroundOpacityPercent by remember {
        mutableIntStateOf(preferences.floatingChatBackgroundOpacityPercent)
    }
    var floatingChatBlurRadiusDp by remember { mutableIntStateOf(preferences.floatingChatBlurRadiusDp) }
    var floatingChatBackgroundColorRgb by remember {
        mutableIntStateOf(preferences.floatingChatBackgroundColorRgb)
    }
    var shortPullThreshold by remember { mutableIntStateOf(preferences.shortPullThresholdDp) }
    var longPullThreshold by remember { mutableIntStateOf(preferences.longPullThresholdDp) }
    var pausedUntilEpochMs by remember { mutableStateOf(preferences.pausedUntilEpochMs) }
    var leftConfigs by remember { mutableStateOf(preferences.edgeConfigs(EdgeSide.LEFT)) }
    var rightConfigs by remember { mutableStateOf(preferences.edgeConfigs(EdgeSide.RIGHT)) }
    var blockedPackages by remember { mutableStateOf(preferences.blockedPackages) }
    var foregroundPackage by remember { mutableStateOf(UbikiAccessibilityService.currentForegroundPackage) }
    var actionRevision by remember { mutableIntStateOf(0) }
    var pickerTarget by remember { mutableStateOf<Pair<EdgeSide, GestureType>?>(null) }

    fun refreshOverlays() {
        UbikiAccessibilityService.instance?.requestOverlayRefresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            StatusPanel(
                isRunning = isRunning,
                openAccessibilitySettings = openAccessibilitySettings,
                openBatteryOptimizationSettings = openBatteryOptimizationSettings,
                openAppBackgroundSettings = openAppBackgroundSettings,
                refreshStatus = {
                    isRunning = UbikiAccessibilityService.isRunning
                    foregroundPackage = UbikiAccessibilityService.currentForegroundPackage
                }
            )
        }
        item {
            GlobalPanel(
                globalEnabled = globalEnabled,
                onGlobalEnabledChange = {
                    globalEnabled = it
                    preferences.globalEnabled = it
                    refreshOverlays()
                },
                showIndicators = showIndicators,
                onShowIndicatorsChange = {
                    showIndicators = it
                    preferences.showIndicators = it
                    refreshOverlays()
                },
                hapticFeedback = hapticFeedback,
                onHapticFeedbackChange = {
                    hapticFeedback = it
                    preferences.hapticFeedback = it
                },
                disableInLandscape = disableInLandscape,
                onDisableInLandscapeChange = {
                    disableInLandscape = it
                    preferences.disableInLandscape = it
                    refreshOverlays()
                },
                disableWhenKeyboardShown = disableWhenKeyboardShown,
                onDisableWhenKeyboardShownChange = {
                    disableWhenKeyboardShown = it
                    preferences.disableWhenKeyboardShown = it
                    refreshOverlays()
                },
                overlayOpacity = overlayOpacity,
                onOverlayOpacityChange = {
                    overlayOpacity = it
                    preferences.overlayOpacity = it
                    refreshOverlays()
                },
                shortPullThreshold = shortPullThreshold,
                onShortPullThresholdChange = {
                    shortPullThreshold = it
                    preferences.shortPullThresholdDp = it
                    longPullThreshold = preferences.longPullThresholdDp
                    refreshOverlays()
                },
                longPullThreshold = longPullThreshold,
                onLongPullThresholdChange = {
                    longPullThreshold = it
                    preferences.longPullThresholdDp = it
                    longPullThreshold = preferences.longPullThresholdDp
                    refreshOverlays()
                }
            )
        }
        item {
            FloatingChatAppearancePanel(
                frostedBackgroundEnabled = floatingChatFrostedBackgroundEnabled,
                onFrostedBackgroundEnabledChange = {
                    floatingChatFrostedBackgroundEnabled = it
                    preferences.floatingChatFrostedBackgroundEnabled = it
                    refreshOverlays()
                },
                backgroundOpacityPercent = floatingChatBackgroundOpacityPercent,
                onBackgroundOpacityPercentChange = {
                    floatingChatBackgroundOpacityPercent = sanitizeFloatingChatBackgroundOpacityPercent(it)
                    preferences.floatingChatBackgroundOpacityPercent = floatingChatBackgroundOpacityPercent
                    refreshOverlays()
                },
                blurRadiusDp = floatingChatBlurRadiusDp,
                onBlurRadiusDpChange = {
                    floatingChatBlurRadiusDp = sanitizeFloatingChatBlurRadiusDp(it)
                    preferences.floatingChatBlurRadiusDp = floatingChatBlurRadiusDp
                    refreshOverlays()
                },
                backgroundColorRgb = floatingChatBackgroundColorRgb,
                onBackgroundColorRgbChange = {
                    floatingChatBackgroundColorRgb = sanitizeFloatingChatBackgroundColorRgb(it)
                    preferences.floatingChatBackgroundColorRgb = floatingChatBackgroundColorRgb
                    refreshOverlays()
                }
            )
        }
        item {
            PausePanel(
                pausedUntilEpochMs = pausedUntilEpochMs,
                onPauseFor = { durationMs ->
                    preferences.pauseFor(durationMs)
                    pausedUntilEpochMs = preferences.pausedUntilEpochMs
                    refreshOverlays()
                },
                onResumeNow = {
                    preferences.resumeNow()
                    pausedUntilEpochMs = preferences.pausedUntilEpochMs
                    refreshOverlays()
                }
            )
        }
        item {
            BlockedAppsPanel(
                foregroundPackage = foregroundPackage,
                blockedPackages = blockedPackages,
                launchableApps = launchableApps,
                onRefreshForeground = {
                    foregroundPackage = UbikiAccessibilityService.currentForegroundPackage
                },
                onAddPackage = { packageName ->
                    preferences.addBlockedPackage(packageName)
                    blockedPackages = preferences.blockedPackages
                    refreshOverlays()
                },
                onRemovePackage = { packageName ->
                    preferences.removeBlockedPackage(packageName)
                    blockedPackages = preferences.blockedPackages
                    refreshOverlays()
                }
            )
        }
        item {
            EdgeConfigPanel(
                title = stringResource(id = R.string.left_edge),
                side = EdgeSide.LEFT,
                configs = leftConfigs,
                onConfigsChange = {
                    leftConfigs = it
                    preferences.setEdgeConfigs(EdgeSide.LEFT, it)
                    refreshOverlays()
                }
            )
        }
        item {
            EdgeConfigPanel(
                title = stringResource(id = R.string.right_edge),
                side = EdgeSide.RIGHT,
                configs = rightConfigs,
                onConfigsChange = {
                    rightConfigs = it
                    preferences.setEdgeConfigs(EdgeSide.RIGHT, it)
                    refreshOverlays()
                }
            )
        }
        item {
            GestureMappingPanel(
                side = EdgeSide.LEFT,
                title = stringResource(id = R.string.left_edge_gestures),
                preferences = preferences,
                revision = actionRevision,
                onPickAction = { pickerTarget = EdgeSide.LEFT to it }
            )
        }
        item {
            GestureMappingPanel(
                side = EdgeSide.RIGHT,
                title = stringResource(id = R.string.right_edge_gestures),
                preferences = preferences,
                revision = actionRevision,
                onPickAction = { pickerTarget = EdgeSide.RIGHT to it }
            )
        }
    }

    pickerTarget?.let { target ->
        ActionPickerDialog(
            current = preferences.actionFor(target.first, target.second),
            launchableApps = launchableApps,
            onDismiss = { pickerTarget = null },
            onSelect = { action ->
                preferences.setAction(target.first, target.second, action)
                actionRevision += 1
                refreshOverlays()
                pickerTarget = null
            }
        )
    }
}

@Composable
private fun PausePanel(
    pausedUntilEpochMs: Long,
    onPauseFor: (Long) -> Unit,
    onResumeNow: () -> Unit
) {
    val now = System.currentTimeMillis()
    val remainingMs = (pausedUntilEpochMs - now).coerceAtLeast(0L)
    val status = if (remainingMs > 0L) {
        stringResource(id = R.string.pause_status_paused, formatRemainingTimeLabel(remainingMs))
    } else {
        stringResource(id = R.string.status_active)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(id = R.string.pause_title), style = MaterialTheme.typography.titleMedium)
            Text(text = status, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPauseFor(60_000L) }) {
                    Text(text = stringResource(id = R.string.pause_1_min))
                }
                Button(onClick = { onPauseFor(5L * 60L * 1000L) }) {
                    Text(text = stringResource(id = R.string.pause_5_min))
                }
                Button(onClick = { onPauseFor(15L * 60L * 1000L) }) {
                    Text(text = stringResource(id = R.string.pause_15_min))
                }
            }
            OutlinedButton(
                enabled = remainingMs > 0L,
                onClick = onResumeNow
            ) {
                Text(text = stringResource(id = R.string.resume_now))
            }
        }
    }
}

@Composable
private fun BlockedAppsPanel(
    foregroundPackage: String?,
    blockedPackages: Set<String>,
    launchableApps: List<LaunchableApp>,
    onRefreshForeground: () -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit
) {
    var packageInput by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    val currentPackage = foregroundPackage.orEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(id = R.string.blocked_apps_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (currentPackage.isBlank()) {
                    stringResource(id = R.string.foreground_unknown)
                } else {
                    stringResource(id = R.string.foreground_package, currentPackage)
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRefreshForeground) {
                    Text(text = stringResource(id = R.string.refresh))
                }
                Button(
                    enabled = currentPackage.isNotBlank(),
                    onClick = { onAddPackage(currentPackage) }
                ) {
                    Text(text = stringResource(id = R.string.block_current))
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = packageInput,
                onValueChange = { packageInput = it },
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.package_name)) }
            )
            Button(
                enabled = packageInput.isNotBlank(),
                onClick = {
                    onAddPackage(packageInput)
                    packageInput = ""
                }
            ) {
                Text(text = stringResource(id = R.string.add_package))
            }
            OutlinedButton(onClick = { showAppPicker = true }) {
                Text(text = stringResource(id = R.string.pick_installed_app))
            }
            if (blockedPackages.isEmpty()) {
                Text(text = stringResource(id = R.string.no_blocked_apps), style = MaterialTheme.typography.bodySmall)
            } else {
                blockedPackages.sorted().forEach { packageName ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { onRemovePackage(packageName) }) {
                            Text(text = stringResource(id = R.string.remove))
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = stringResource(id = R.string.pick_app_to_block),
            launchableApps = launchableApps,
            onDismiss = { showAppPicker = false },
            onSelect = { app ->
                onAddPackage(app.packageName)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun StatusPanel(
    isRunning: Boolean,
    openAccessibilitySettings: () -> Unit,
    openBatteryOptimizationSettings: () -> Unit,
    openAppBackgroundSettings: () -> Unit,
    refreshStatus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(id = R.string.service_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isRunning) {
                    stringResource(id = R.string.service_running)
                } else {
                    stringResource(id = R.string.service_stopped)
                }
            )
            Text(
                text = stringResource(id = R.string.service_persistence_note),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = openAccessibilitySettings) {
                    Text(text = stringResource(id = R.string.open_settings))
                }
                OutlinedButton(onClick = refreshStatus) {
                    Text(text = stringResource(id = R.string.refresh))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = openBatteryOptimizationSettings) {
                    Text(text = stringResource(id = R.string.open_battery_optimization))
                }
                OutlinedButton(onClick = openAppBackgroundSettings) {
                    Text(text = stringResource(id = R.string.open_app_background_settings))
                }
            }
        }
    }
}

@Composable
private fun GlobalPanel(
    globalEnabled: Boolean,
    onGlobalEnabledChange: (Boolean) -> Unit,
    showIndicators: Boolean,
    onShowIndicatorsChange: (Boolean) -> Unit,
    hapticFeedback: Boolean,
    onHapticFeedbackChange: (Boolean) -> Unit,
    disableInLandscape: Boolean,
    onDisableInLandscapeChange: (Boolean) -> Unit,
    disableWhenKeyboardShown: Boolean,
    onDisableWhenKeyboardShownChange: (Boolean) -> Unit,
    overlayOpacity: Int,
    onOverlayOpacityChange: (Int) -> Unit,
    shortPullThreshold: Int,
    onShortPullThresholdChange: (Int) -> Unit,
    longPullThreshold: Int,
    onLongPullThresholdChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(id = R.string.global_controls_title), style = MaterialTheme.typography.titleMedium)
            SwitchRow(stringResource(id = R.string.enable_edge_bars), globalEnabled, onGlobalEnabledChange)
            SwitchRow(stringResource(id = R.string.show_indicators), showIndicators, onShowIndicatorsChange)
            SwitchRow(stringResource(id = R.string.haptic_feedback), hapticFeedback, onHapticFeedbackChange)
            SwitchRow(stringResource(id = R.string.disable_in_landscape), disableInLandscape, onDisableInLandscapeChange)
            SwitchRow(
                stringResource(id = R.string.disable_when_keyboard_shown),
                disableWhenKeyboardShown,
                onDisableWhenKeyboardShownChange
            )
            SliderRow(
                title = stringResource(id = R.string.indicator_opacity),
                value = overlayOpacity,
                range = 0f..100f,
                suffix = stringResource(id = R.string.percent_suffix),
                onValueChange = onOverlayOpacityChange
            )
            SliderRow(
                title = stringResource(id = R.string.short_pull_threshold),
                value = shortPullThreshold,
                range = 8f..80f,
                suffix = stringResource(id = R.string.dp_suffix),
                onValueChange = onShortPullThresholdChange
            )
            SliderRow(
                title = stringResource(id = R.string.long_pull_threshold),
                value = longPullThreshold,
                range = 32f..320f,
                suffix = stringResource(id = R.string.dp_suffix),
                onValueChange = onLongPullThresholdChange
            )
        }
    }
}

@Composable
private fun EdgeConfigPanel(
    title: String,
    side: EdgeSide,
    configs: List<EdgeZoneConfig>,
    onConfigsChange: (List<EdgeZoneConfig>) -> Unit
) {
    val normalizedConfigs = remember(side, configs) {
        normalizeEdgeConfigs(side, configs)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            normalizedConfigs.forEachIndexed { index, config ->
                if (index > 0) {
                    HorizontalDivider()
                }
                EdgeZoneConfigSection(
                    title = stringResource(id = R.string.edge_zone_title, index + 1),
                    config = config,
                    canRemove = normalizedConfigs.size > 1,
                    onConfigChange = { updated ->
                        val updatedConfigs = normalizedConfigs.toMutableList()
                        updatedConfigs[index] = updated
                        onConfigsChange(normalizeEdgeConfigs(side, updatedConfigs))
                    },
                    onRemove = {
                        onConfigsChange(
                            normalizeEdgeConfigs(
                                side,
                                normalizedConfigs.filterIndexed { itemIndex, _ -> itemIndex != index }
                            )
                        )
                    }
                )
            }
            OutlinedButton(
                enabled = normalizedConfigs.size < EdgeZoneConfig.MAX_ZONES_PER_SIDE,
                onClick = {
                    onConfigsChange(
                        normalizeEdgeConfigs(
                            side,
                            normalizedConfigs + EdgeZoneConfig.defaultFor(side, normalizedConfigs.size)
                        )
                    )
                }
            ) {
                Text(text = stringResource(id = R.string.add_edge_zone))
            }
        }
    }
}

@Composable
private fun EdgeZoneConfigSection(
    title: String,
    config: EdgeZoneConfig,
    canRemove: Boolean,
    onConfigChange: (EdgeZoneConfig) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            TextButton(
                enabled = canRemove,
                onClick = onRemove
            ) {
                Text(text = stringResource(id = R.string.remove_edge_zone))
            }
        }
        SwitchRow(
            label = stringResource(id = R.string.enabled),
            checked = config.enabled,
            onCheckedChange = {
                onConfigChange(config.copy(enabled = it).sanitized())
            }
        )
        SliderRow(
            title = stringResource(id = R.string.edge_thickness),
            value = config.thicknessDp,
            range = EdgeZoneConfig.MIN_THICKNESS_DP.toFloat()..72f,
            suffix = stringResource(id = R.string.dp_suffix),
            onValueChange = {
                onConfigChange(config.copy(thicknessDp = it).sanitized())
            }
        )
        SliderRow(
            title = stringResource(id = R.string.edge_top_inset),
            value = config.topInsetPercent,
            range = 0f..EdgeZoneConfig.MAX_INSET_PERCENT.toFloat(),
            suffix = stringResource(id = R.string.percent_suffix),
            onValueChange = {
                onConfigChange(config.copy(topInsetPercent = it).sanitized())
            }
        )
        SliderRow(
            title = stringResource(id = R.string.edge_bottom_inset),
            value = config.bottomInsetPercent,
            range = 0f..EdgeZoneConfig.MAX_INSET_PERCENT.toFloat(),
            suffix = stringResource(id = R.string.percent_suffix),
            onValueChange = {
                onConfigChange(config.copy(bottomInsetPercent = it).sanitized())
            }
        )
    }
}

private fun normalizeEdgeConfigs(side: EdgeSide, configs: List<EdgeZoneConfig>): List<EdgeZoneConfig> {
    return configs
        .take(EdgeZoneConfig.MAX_ZONES_PER_SIDE)
        .mapIndexed { index, config ->
            config.copy(side = side, zoneId = index).sanitized()
        }
        .ifEmpty { listOf(EdgeZoneConfig.defaultFor(side, EdgeZoneConfig.DEFAULT_ZONE_ID)) }
}

@Composable
private fun GestureMappingPanel(
    side: EdgeSide,
    title: String,
    preferences: UbikiPreferences,
    revision: Int,
    onPickAction: (GestureType) -> Unit
) {
    val gestures = remember {
        gestureMappingOrder()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            gestures.forEachIndexed { index, gestureType ->
                val action = remember(revision, side, gestureType) {
                    preferences.actionFor(side, gestureType)
                }
                GestureActionRow(
                    gestureType = gestureType,
                    action = action,
                    onClick = { onPickAction(gestureType) }
                )
                if (index != gestures.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GestureActionRow(
    gestureType: GestureType,
    action: GestureAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(text = gestureLabel(gestureType), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(id = R.string.action_value, actionLabel(action)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(text = stringResource(id = R.string.change))
        }
    }
}

@Composable
private fun ActionPickerDialog(
    current: GestureAction,
    launchableApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit
) {
    var launchPackage by remember { mutableStateOf((current as? GestureAction.LaunchApp)?.packageName.orEmpty()) }
    var showLaunchAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.choose_action)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureActionCatalog.systemActions.forEach { action ->
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(action) }
                        ) {
                            val label = actionLabel(action)
                            val text = if (action.id == current.id) {
                                stringResource(id = R.string.current_value, label)
                            } else {
                                label
                            }
                            Text(text = text)
                        }
                    }
                }
                item { HorizontalDivider() }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showLaunchAppPicker = true }
                    ) {
                        Text(text = stringResource(id = R.string.pick_launch_app))
                    }
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = launchPackage,
                        onValueChange = { launchPackage = it },
                        singleLine = true,
                        label = { Text(text = stringResource(id = R.string.launch_package)) }
                    )
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = launchPackage.isNotBlank(),
                        onClick = { onSelect(GestureAction.LaunchApp(launchPackage.trim())) }
                    ) {
                        Text(text = stringResource(id = R.string.use_launch_package))
                    }
                }
            }
        }
    )

    if (showLaunchAppPicker) {
        AppPickerDialog(
            title = stringResource(id = R.string.pick_launch_app),
            launchableApps = launchableApps,
            onDismiss = { showLaunchAppPicker = false },
            onSelect = { app ->
                onSelect(GestureAction.LaunchApp(app.packageName))
                showLaunchAppPicker = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    launchableApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSelect: (LaunchableApp) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(query, launchableApps) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            launchableApps
        } else {
            launchableApps.filter {
                it.label.lowercase().contains(normalized) ||
                    it.packageName.lowercase().contains(normalized)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.search_app)) }
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredApps.take(40).forEach { app ->
                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSelect(app) }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = app.label)
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title)
            Text(text = "$value$suffix")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range
        )
    }
}

@Composable
private fun gestureLabel(type: GestureType): String {
    return when (type) {
        GestureType.TAP -> stringResource(id = R.string.gesture_tap)
        GestureType.DOUBLE_TAP -> stringResource(id = R.string.gesture_double_tap)
        GestureType.LONG_PRESS -> stringResource(id = R.string.gesture_long_press)
        GestureType.SWIPE_UP -> stringResource(id = R.string.gesture_swipe_up)
        GestureType.SWIPE_DOWN -> stringResource(id = R.string.gesture_swipe_down)
        GestureType.PULL_INWARD -> stringResource(id = R.string.gesture_pull_inward)
        GestureType.PULL_INWARD_SHORT -> stringResource(id = R.string.gesture_pull_inward_short)
        GestureType.PULL_INWARD_LONG -> stringResource(id = R.string.gesture_pull_inward_long)
        GestureType.PULL_INWARD_HOLD -> stringResource(id = R.string.gesture_pull_inward_hold)
        GestureType.PULL_DIAGONAL_UP -> stringResource(id = R.string.gesture_diagonal_up)
        GestureType.PULL_DIAGONAL_DOWN -> stringResource(id = R.string.gesture_diagonal_down)
        GestureType.PULL_DIAGONAL_UP_SHORT -> stringResource(id = R.string.gesture_diagonal_up_short)
        GestureType.PULL_DIAGONAL_UP_LONG -> stringResource(id = R.string.gesture_diagonal_up_long)
        GestureType.PULL_DIAGONAL_DOWN_SHORT -> stringResource(id = R.string.gesture_diagonal_down_short)
        GestureType.PULL_DIAGONAL_DOWN_LONG -> stringResource(id = R.string.gesture_diagonal_down_long)
    }
}

@Composable
private fun FloatingChatAppearancePanel(
    frostedBackgroundEnabled: Boolean,
    onFrostedBackgroundEnabledChange: (Boolean) -> Unit,
    backgroundOpacityPercent: Int,
    onBackgroundOpacityPercentChange: (Int) -> Unit,
    blurRadiusDp: Int,
    onBlurRadiusDpChange: (Int) -> Unit,
    backgroundColorRgb: Int,
    onBackgroundColorRgbChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.floating_chat_appearance_title),
                style = MaterialTheme.typography.titleMedium
            )
            SwitchRow(
                label = stringResource(id = R.string.floating_chat_frosted_background),
                checked = frostedBackgroundEnabled,
                onCheckedChange = onFrostedBackgroundEnabledChange
            )
            SliderRow(
                title = stringResource(id = R.string.floating_chat_background_opacity),
                value = backgroundOpacityPercent,
                range = 0f..100f,
                suffix = stringResource(id = R.string.percent_suffix),
                onValueChange = onBackgroundOpacityPercentChange
            )
            SliderRow(
                title = stringResource(id = R.string.floating_chat_blur_radius),
                value = blurRadiusDp,
                range = 0f..40f,
                suffix = stringResource(id = R.string.dp_suffix),
                onValueChange = onBlurRadiusDpChange
            )
            FloatingChatBackgroundColorPicker(
                selectedColorRgb = backgroundColorRgb,
                onColorSelected = onBackgroundColorRgbChange
            )
            FloatingChatAppearancePreview(
                frostedBackgroundEnabled = frostedBackgroundEnabled,
                backgroundOpacityPercent = backgroundOpacityPercent,
                blurRadiusDp = blurRadiusDp,
                backgroundColorRgb = backgroundColorRgb
            )
        }
    }
}

@Composable
private fun FloatingChatBackgroundColorPicker(
    selectedColorRgb: Int,
    onColorSelected: (Int) -> Unit
) {
    val selected = sanitizeFloatingChatBackgroundColorRgb(selectedColorRgb)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(id = R.string.floating_chat_background_color),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            floatingChatBackgroundColorPresetRgbs().forEach { colorRgb ->
                val color = Color(0xFF000000 or sanitizeFloatingChatBackgroundColorRgb(colorRgb).toLong())
                val selectedShape = CircleShape
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(selectedShape)
                        .background(color)
                        .border(
                            width = if (selected == colorRgb) 3.dp else 1.dp,
                            color = if (selected == colorRgb) MaterialTheme.colorScheme.primary else Color(0x66808A91),
                            shape = selectedShape
                        )
                        .clickable { onColorSelected(colorRgb) }
                )
            }
        }
        OutlinedButton(onClick = { onColorSelected(defaultFloatingChatBackgroundColorRgb()) }) {
            Text(text = stringResource(id = R.string.floating_chat_background_color_reset))
        }
    }
}

@Composable
private fun FloatingChatAppearancePreview(
    frostedBackgroundEnabled: Boolean,
    backgroundOpacityPercent: Int,
    blurRadiusDp: Int,
    backgroundColorRgb: Int
) {
    val opacityAlpha = if (frostedBackgroundEnabled) {
        sanitizeFloatingChatBackgroundOpacityPercent(backgroundOpacityPercent) / 100f
    } else {
        0f
    }
    val blurAlpha = if (frostedBackgroundEnabled) {
        sanitizeFloatingChatBlurRadiusDp(blurRadiusDp) / 40f
    } else {
        0f
    }
    val previewShape = RoundedCornerShape(18.dp)
    val selectedBackgroundColor = Color(
        0xFF000000 or sanitizeFloatingChatBackgroundColorRgb(backgroundColorRgb).toLong()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(previewShape)
            .background(Color(0xFFE4ECF0))
            .border(1.dp, Color(0x338799A3), previewShape)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color(0xFFB8C8D2))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color(0xFFD7E0E6))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color(0xFFC6D7DD))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(selectedBackgroundColor.copy(alpha = opacityAlpha))
        )
        Box(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(0.72f)
                .height(58.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(selectedBackgroundColor.copy(alpha = (0.48f + blurAlpha * 0.34f).coerceIn(0f, 1f)))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(15.dp))
                .align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.floating_chat_appearance_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF20313A)
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF6E858F).copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun actionLabel(action: GestureAction): String {
    return when (action) {
        GestureAction.None -> stringResource(id = R.string.action_none)
        GestureAction.Back -> stringResource(id = R.string.action_back)
        GestureAction.Home -> stringResource(id = R.string.action_home)
        GestureAction.Recents -> stringResource(id = R.string.action_recents)
        GestureAction.Notifications -> stringResource(id = R.string.action_notifications)
        GestureAction.QuickSettings -> stringResource(id = R.string.action_quick_settings)
        GestureAction.Screenshot -> stringResource(id = R.string.action_screenshot)
        GestureAction.LockScreen -> stringResource(id = R.string.action_lock_screen)
        GestureAction.VolumeUp -> stringResource(id = R.string.action_volume_up)
        GestureAction.VolumeDown -> stringResource(id = R.string.action_volume_down)
        GestureAction.ExpandFloatingChat -> stringResource(id = R.string.action_expand_floating_chat)
        GestureAction.CollapseFloatingChat -> stringResource(id = R.string.action_collapse_floating_chat)
        is GestureAction.LaunchApp -> stringResource(id = R.string.action_launch_app, action.packageName)
    }
}


@Composable
private fun formatRemainingTimeLabel(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        stringResource(id = R.string.remaining_minutes_seconds, minutes, seconds)
    } else {
        stringResource(id = R.string.remaining_seconds, seconds)
    }
}

private data class LaunchableApp(
    val label: String,
    val packageName: String
)

private fun MainActivity.loadLaunchableApps(): List<LaunchableApp> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    return resolveInfos.mapNotNull { info ->
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        val label = info.loadLabel(packageManager)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        LaunchableApp(label = label, packageName = packageName)
    }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
