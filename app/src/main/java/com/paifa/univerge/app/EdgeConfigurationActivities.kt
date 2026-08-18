package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.BottomGestureBarGestureType
import com.paifa.univerge.accessibility.UbikiAccessibilityService
import com.paifa.univerge.accessibility.UbikiPreferences
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureType

private const val EXTRA_CONFIGURATION_TARGET = "configuration_target"

internal enum class ConfigurationTarget(val title: String) {
    LEFT("左侧边缘"),
    RIGHT("右侧边缘"),
    BOTTOM("底部边缘");

    companion object {
        fun fromIntentValue(value: String?): ConfigurationTarget = entries.firstOrNull { it.name == value } ?: LEFT
    }
}

class EdgeStyleSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = ConfigurationTarget.fromIntentValue(intent.getStringExtra(EXTRA_CONFIGURATION_TARGET))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EdgeStyleSettingsScreen(target = target, onBack = ::finish)
                }
            }
        }
    }
}

class EdgeFunctionSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = ConfigurationTarget.fromIntentValue(intent.getStringExtra(EXTRA_CONFIGURATION_TARGET))
        val apps = loadLaunchableApps()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EdgeFunctionSettingsScreen(target = target, launchableApps = apps, onBack = ::finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgeStyleSettingsScreen(target: ConfigurationTarget, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UbikiPreferences(context) }
    val side = target.edgeSideOrNull()
    var configs by remember(side) { mutableStateOf(side?.let(preferences::edgeConfigs).orEmpty()) }
    var bottomWidth by remember { mutableIntStateOf(preferences.bottomGestureBarWidthDp) }

    SettingsPageScaffold(title = "${target.title}样式", onBack = onBack) {
        when (side) {
            null -> BottomGestureBarPanel(
                widthDp = bottomWidth,
                onWidthChange = { width ->
                    bottomWidth = width
                    UbikiAccessibilityService.instance?.showBottomGestureBarPreview(bottomWidth)
                },
                onWidthChangeFinished = {
                    if (shouldPersistBottomGestureBarWidth(isFinished = true)) {
                        preferences.bottomGestureBarWidthDp = bottomWidth
                    }
                },
                preferences = preferences,
                revision = 0,
                onPickAction = {},
                showActions = false
            )
            else -> EdgeConfigPanel(
                title = target.title,
                side = side,
                configs = configs,
                onConfigsChange = { updated ->
                    configs = updated
                    preferences.setEdgeConfigs(side, updated)
                },
                onConfigAdjusted = { config ->
                    UbikiAccessibilityService.instance?.showEdgeConfigAdjustmentPreview(config)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgeFunctionSettingsScreen(
    target: ConfigurationTarget,
    launchableApps: List<LaunchableApp>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { UbikiPreferences(context) }
    val side = target.edgeSideOrNull()
    var revision by remember { mutableIntStateOf(0) }
    var edgePicker by remember { mutableStateOf<Pair<com.paifa.univerge.core.model.EdgeSide, com.paifa.univerge.core.model.GestureType>?>(null) }
    var bottomPicker by remember { mutableStateOf<BottomGestureBarGestureType?>(null) }

    SettingsPageScaffold(title = "${target.title}功能", onBack = onBack) {
        when (side) {
            null -> BottomGestureBarPanel(
                widthDp = preferences.bottomGestureBarWidthDp,
                onWidthChange = {},
                preferences = preferences,
                revision = revision,
                onPickAction = { bottomPicker = it },
                showWidth = false
            )
            else -> GestureMappingPanel(
                side = side,
                title = "${target.title}手势",
                preferences = preferences,
                revision = revision,
                onPickAction = { edgePicker = side to it }
            )
        }
    }

    edgePicker?.let { (pickerSide, gestureType) ->
        ActionPickerDialog(
            current = preferences.actionFor(pickerSide, gestureType),
            launchableApps = launchableApps,
            onDismiss = { edgePicker = null },
            onSelect = { action ->
                preferences.setAction(pickerSide, gestureType, action)
                revision += 1
                UbikiAccessibilityService.instance?.requestOverlayRefresh()
                edgePicker = null
            }
        )
    }
    bottomPicker?.let { gestureType ->
        ActionPickerDialog(
            current = preferences.bottomGestureBarActionFor(gestureType),
            launchableApps = launchableApps,
            onDismiss = { bottomPicker = null },
            onSelect = { action ->
                preferences.setBottomGestureBarAction(gestureType, action)
                revision += 1
                bottomPicker = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { content() }
        }
    }
}

private fun ConfigurationTarget.edgeSideOrNull(): com.paifa.univerge.core.model.EdgeSide? = when (this) {
    ConfigurationTarget.LEFT -> _root_ide_package_.com.paifa.univerge.core.model.EdgeSide.LEFT
    ConfigurationTarget.RIGHT -> _root_ide_package_.com.paifa.univerge.core.model.EdgeSide.RIGHT
    ConfigurationTarget.BOTTOM -> null
}
