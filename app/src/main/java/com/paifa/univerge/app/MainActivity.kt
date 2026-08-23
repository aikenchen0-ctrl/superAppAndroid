package com.paifa.univerge.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MotionPhotosAuto
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.UniVergeAccessibilityService
import com.paifa.univerge.accessibility.UniVergeGesturePersistence
import com.paifa.univerge.accessibility.UniVergePreferences
import com.adbcore.WifiAutoRecover
import com.paifa.univerge.accessibility.BottomGestureBarGestureType
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.defaultFloatingChatBackgroundColorRgb
import com.paifa.univerge.accessibility.floatingChatBackgroundColorPresetRgbs
import com.paifa.univerge.accessibility.sanitizeFloatingChatBackgroundOpacityPercent
import com.paifa.univerge.accessibility.sanitizeFloatingChatBackgroundColorRgb
import com.paifa.univerge.accessibility.sanitizeFloatingChatBlurRadiusDp
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureActionCatalog
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.model.gestureMappingOrder
import kotlinx.coroutines.launch

/**
 * 应用设置页的入口 Activity。
 *
 * `ComponentActivity` 是 AndroidX 提供的 Activity 基类，能够通过 `setContent` 承载
 * Jetpack Compose 界面，而不需要传统 XML 布局文件。
 */
class MainActivity : ComponentActivity() {
    private lateinit var keepAlivePreferences: UniVergePreferences
    private var accessibilityServiceEnabled by mutableStateOf(false)

    /**
     * Android 创建此页面时调用的生命周期方法。
     * `savedInstanceState` 可用于恢复页面状态；本页面目前不需要直接读取它。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // `this` 就是当前 Activity。两个管理类负责读取和保存设置数据。
        val preferences = UniVergePreferences(this)
        keepAlivePreferences = preferences
        accessibilityServiceEnabled = UniVergeGesturePersistence.isAccessibilityServiceEnabled(this)
        val scrmSettingsManager = ScrmSettingsManager(this)

        // 读取手机中能从桌面启动的应用，供后面的“选择要启动/屏蔽的应用”对话框使用。
        val launchableApps = loadLaunchableApps()

        // Compose 的 UI 从这里开始声明；状态变化后，相关 Composable 会自动重新绘制。
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeDashboardScreen(
                        preferences = preferences,
                        scrmSettingsManager = scrmSettingsManager,
                        launchableApps = launchableApps,
                        keepAliveController = AccessibilityKeepAliveController(this@MainActivity, preferences),
                        accessibilityServiceEnabled = accessibilityServiceEnabled,
                        refreshAccessibilityServiceStatus = {
                            accessibilityServiceEnabled = UniVergeGesturePersistence.isAccessibilityServiceEnabled(this@MainActivity)
                        },
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

    override fun onResume() {
        super.onResume()
        accessibilityServiceEnabled = UniVergeGesturePersistence.isAccessibilityServiceEnabled(this)
        if (::keepAlivePreferences.isInitialized && keepAlivePreferences.accessibilityKeepAliveEnabled) {
            lifecycleScope.launch {
                AccessibilityKeepAliveController(this@MainActivity, keepAlivePreferences).ensureEnabled()
            }
        }
    }

    /** 打开电池优化设置，帮助无障碍服务在后台更稳定地运行。 */
    private fun openBatteryOptimizationSettings() {
        // Kotlin 中 `if` 可以作为表达式直接返回值，因此这里的分支共同产出一个 Intent。
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            // `?.` 是安全调用：系统服务不存在时不抛出空指针异常，结果会是 null。
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
                // 已忽略电池优化时，跳到系统的优化列表页面。
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                // 尚未忽略时，请求系统为当前包名开启该权限。
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", packageName, null)
                )
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        // `runCatching` 把可能抛出的异常包装为 Result；失败时退回到应用详情页。
        runCatching { startActivity(intent) }
            .onFailure { openAppBackgroundSettings() }
    }

    /** 打开当前应用的系统详情页；部分厂商把后台限制入口放在这里。 */
    private fun openAppBackgroundSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

class GlobalControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GlobalControlsScreen(onBack = ::finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UniVergePreferences(context) }
    var globalEnabled by remember { mutableStateOf(preferences.globalEnabled) }
    var showIndicators by remember { mutableStateOf(preferences.showIndicators) }
    var hapticFeedback by remember { mutableStateOf(preferences.hapticFeedback) }
    var disableInLandscape by remember { mutableStateOf(preferences.disableInLandscape) }
    var disableWhenKeyboardShown by remember { mutableStateOf(preferences.disableWhenKeyboardShown) }
    var shortPullThreshold by remember { mutableIntStateOf(preferences.shortPullThresholdDp) }
    var longPullThreshold by remember { mutableIntStateOf(preferences.longPullThresholdDp) }

    fun refreshOverlays() {
        UniVergeAccessibilityService.instance?.requestOverlayRefresh()
    }

    fun showPullDistancePreview() {
        UniVergeAccessibilityService.instance?.showPullDistancePreview(
            shortDistanceDp = shortPullThreshold,
            longDistanceDp = longPullThreshold
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局控制") },
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
                    shortPullThreshold = shortPullThreshold,
                    onShortPullThresholdChange = {
                        shortPullThreshold = it
                        preferences.shortPullThresholdDp = it
                        longPullThreshold = preferences.longPullThresholdDp
                        showPullDistancePreview()
                        refreshOverlays()
                    },
                    longPullThreshold = longPullThreshold,
                    onLongPullThresholdChange = {
                        longPullThreshold = it
                        preferences.longPullThresholdDp = it
                        longPullThreshold = preferences.longPullThresholdDp
                        showPullDistancePreview()
                        refreshOverlays()
                    }
                )
            }
        }
    }
}

/**
 * 设置页的根 Compose 函数。
 *
 * 它读取持久化设置作为初始值，把每一块 UI 所需的数据和回调传给子函数。回调负责把用户
 * 的操作写回 `preferences`，并在需要时通知悬浮层刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDashboardScreen(
    preferences: UniVergePreferences,
    keepAliveController: AccessibilityKeepAliveController,
    accessibilityServiceEnabled: Boolean,
    refreshAccessibilityServiceStatus: () -> Unit,
    scrmSettingsManager: ScrmSettingsManager,
    launchableApps: List<LaunchableApp>,
    openAccessibilitySettings: () -> Unit,
    openBatteryOptimizationSettings: () -> Unit,
    openAppBackgroundSettings: () -> Unit
) {
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = minOf(320.dp, screenWidth * 0.82f)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val isRunning = accessibilityServiceEnabled
    var keepAlive by remember { mutableStateOf(preferences.accessibilityKeepAliveEnabled) }
    var keepAliveStatus by remember { mutableStateOf<String?>(null) }
    var globalEnabled by remember { mutableStateOf(preferences.globalEnabled) }
    var showIndicators by remember { mutableStateOf(preferences.showIndicators) }
    var hapticFeedback by remember { mutableStateOf(preferences.hapticFeedback) }
    var disableInLandscape by remember { mutableStateOf(preferences.disableInLandscape) }
    var disableWhenKeyboardShown by remember { mutableStateOf(preferences.disableWhenKeyboardShown) }

    fun refreshStatus() {
        refreshAccessibilityServiceStatus()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("菜单", style = MaterialTheme.typography.titleLarge)
                    DrawerSwitchRow(
                        label = "无障碍服务",
                        icon = Icons.Outlined.Accessibility,
                        checked = isRunning
                    ) { requestedEnabled ->
                        val serviceEnabled = UniVergeGesturePersistence.isAccessibilityServiceEnabled(context)
                        refreshStatus()
                        if (requestedEnabled && !serviceEnabled) {
                            openAccessibilitySettings()
                        }
                    }
                    DrawerSwitchRow(
                        label = "无障碍保活",
                        icon = Icons.Outlined.MotionPhotosAuto,
                        checked = keepAlive
                    ) { enabled ->
                        keepAlive = enabled
                        preferences.accessibilityKeepAliveEnabled = enabled
                        if (enabled) {
                            WifiAutoRecover.armOnce(context)
                            scope.launch {
                                keepAliveStatus = keepAliveController.ensureEnabled().message
                                refreshStatus()
                            }
                        } else {
                            keepAliveStatus = "无障碍保活已关闭"
                        }
                    }
                    keepAliveStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    DrawerNavigationRow(
                        label = "全局控制",
                        icon = Icons.Outlined.Tune,
                        onClick = {
                            context.startActivity(Intent(context, GlobalControlsActivity::class.java))
                        }
                    )
                    HorizontalDivider()
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = openBatteryOptimizationSettings
                    ) {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null)
                        Text("电池白名单", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = openAppBackgroundSettings
                    ) {
                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                        Text("后台自启动", modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(context, AccessibilityKeepAliveGuideActivity::class.java))
                    }) {
                        Text("查看保活说明")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "新一代AI全域企业助手",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            refreshStatus()
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "菜单")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                MainScreen(
                    preferences = preferences,
                    scrmSettingsManager = scrmSettingsManager,
                    launchableApps = launchableApps
                )
            }

            // The original configuration screen remains the active home content.
            // The legacy compact cards below are retained only while their navigation UI is migrated.
            if (false) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("服务状态", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (isRunning) "无障碍服务已开启" else "无障碍服务未开启",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "系统权限与保活设置请从左上角菜单进入。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    NavigationCard(
                        title = "临时暂停",
                        description = "配置免打扰分钟和固定时间段",
                        icon = Icons.Outlined.PauseCircleOutline,
                        intentFactory = { Intent(context, PauseSettingsActivity::class.java) }
                    )
                }
                item {
                    NavigationCard(
                        title = "应用黑名单",
                        description = "进入黑名单应用时会自动关闭侧边栏",
                        icon = Icons.Outlined.AppBlocking,
                        intentFactory = { Intent(context, BlockedAppsActivity::class.java) }
                    )
                }
                item {
                    NavigationCard(
                        title = "侧边功能",
                        description = "配置边缘手势拉出后的快捷功能列表",
                        icon = Icons.Outlined.DragIndicator,
                        intentFactory = { Intent(context, SideFunctionsActivity::class.java) }
                    )
                }
                item {
                    GlobalPanel(
                        globalEnabled = globalEnabled,
                        onGlobalEnabledChange = {
                            globalEnabled = it
                            preferences.globalEnabled = it
                            UniVergeAccessibilityService.instance?.requestOverlayRefresh()
                        },
                        showIndicators = showIndicators,
                        onShowIndicatorsChange = {
                            showIndicators = it
                            preferences.showIndicators = it
                            UniVergeAccessibilityService.instance?.requestOverlayRefresh()
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
                            UniVergeAccessibilityService.instance?.requestOverlayRefresh()
                        },
                        disableWhenKeyboardShown = disableWhenKeyboardShown,
                        onDisableWhenKeyboardShownChange = {
                            disableWhenKeyboardShown = it
                            preferences.disableWhenKeyboardShown = it
                            UniVergeAccessibilityService.instance?.requestOverlayRefresh()
                        },
                        shortPullThreshold = preferences.shortPullThresholdDp,
                        onShortPullThresholdChange = { preferences.shortPullThresholdDp = it },
                        longPullThreshold = preferences.longPullThresholdDp,
                        onLongPullThresholdChange = { preferences.longPullThresholdDp = it }
                    )
                }
                item { ScrmSettingsPanel(manager = scrmSettingsManager) }
            }
            }
        }
    }
}

@Composable
private fun DebugFloatingChatExpandButton() {
    if (!BuildConfig.DEBUG) return

    Button(
        onClick = {
            UniVergeAccessibilityService.instance?.requestFloatingChatExpandForDebug()
        }
    ) {
        Text("展开聊天")
    }
}

@Composable
private fun DebugHapticTestButton() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            context.startActivity(Intent(context, TouchTestActivity::class.java))
        }
    ) {
        Text("触感测试")
    }
}

@Composable
private fun MainScreen(
    preferences: UniVergePreferences,
    scrmSettingsManager: ScrmSettingsManager,
    launchableApps: List<LaunchableApp>
) {
    val context = LocalContext.current
    // `remember` 在界面重组时保留状态；`mutableStateOf` 的值变化会触发使用它的 UI 重绘。
    var leftConfigs by remember { mutableStateOf(preferences.edgeConfigs(EdgeSide.LEFT)) }
    var rightConfigs by remember { mutableStateOf(preferences.edgeConfigs(EdgeSide.RIGHT)) }

    // 这两个值只控制界面：revision 用来让手势动作重新读取，pickerTarget 决定是否显示对话框。
    var actionRevision by remember { mutableIntStateOf(0) }
    var pickerTarget by remember { mutableStateOf<Pair<EdgeSide, GestureType>?>(null) }
    var bottomGestureBarWidth by remember { mutableIntStateOf(preferences.bottomGestureBarWidthDp) }
    var bottomGesturePickerTarget by remember { mutableStateOf<BottomGestureBarGestureType?>(null) }

    // 设置变化后，请无障碍服务重新创建/更新边缘悬浮层；`?.` 允许服务尚未启动。
    fun refreshOverlays() {
        UniVergeAccessibilityService.instance?.requestOverlayRefresh()
    }

    // LazyColumn 是可滚动的竖向列表，只组合当前屏幕附近的内容，适合较长的设置页。
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DebugFloatingChatExpandButton()
            }
        }
        item { DebugHapticTestButton() }
        item {
            NavigationCard(
                title = "连接微信服务",
                description = "配置微信后台服务器，启用悬浮聊天中的微信收发能力",
                icon = Icons.Outlined.ChatBubbleOutline,
                intentFactory = { Intent(context, WeChatServiceActivity::class.java) }
            )
        }
        item {
            NavigationCard(
                title = "微信聊天外观",
                description = "调整悬浮微信聊天的背景颜色、毛玻璃和透明效果",
                icon = Icons.Outlined.Palette,
                intentFactory = { Intent(context, WeChatChatAppearanceActivity::class.java) }
            )
        }
        item {
            Text(
                text = "侧边功能",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item { EdgeConfigurationCards() }
        item {
            NavigationCard(
                title = "免打扰",
                description = "设置临时免打扰时长和固定免打扰时间段",
                icon = Icons.Outlined.DoNotDisturbOn,
                intentFactory = { Intent(context, PauseSettingsActivity::class.java) }
            )
        }
        item {
            NavigationCard(
                title = "应用黑名单",
                description = "进入黑名单应用时会自动关闭侧边栏",
                icon = Icons.Outlined.AppBlocking,
                intentFactory = { Intent(context, BlockedAppsActivity::class.java) }
            )
        }
        item {
            NavigationCard(
                title = "侧边功能",
                description = "配置边缘手势拉出后的快捷功能列表",
                icon = Icons.Outlined.DragIndicator,
                intentFactory = { Intent(context, SideFunctionsActivity::class.java) }
            )
        }
        // 用于占位，给最后一项保留舒适的滚动操作空间。
        item { Text(text = "", modifier = Modifier.height(60.dp)) }
    }

    // `?.let` 仅在选择目标不为 null 时执行，因此它自然地控制对话框的显示与隐藏。
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

    // 底部手势条与左右边缘手势共用动作选择器，但使用各自的设置读取/写入方法。
    bottomGesturePickerTarget?.let { gestureType ->
        ActionPickerDialog(
            current = preferences.bottomGestureBarActionFor(gestureType),
            launchableApps = launchableApps,
            onDismiss = { bottomGesturePickerTarget = null },
            onSelect = { action ->
                preferences.setBottomGestureBarAction(gestureType, action)
                actionRevision += 1
                bottomGesturePickerTarget = null
            }
        )
    }
}

@Composable
/**
 * 显示“暂停手势”状态并提供几个预设时长。
 * `pausedUntilEpochMs` 是 Unix 时间戳（毫秒），不是“还剩多少毫秒”。
 */
private fun PausePanel(
    pausedUntilEpochMs: Long,
    onPauseFor: (Long) -> Unit,
    onResumeNow: () -> Unit
) {
    val now = System.currentTimeMillis()
    // `coerceAtLeast` 将负数钳制为 0，避免暂停到期后显示负的剩余时间。
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DoNotDisturbOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = stringResource(id = R.string.pause_title), style = MaterialTheme.typography.titleMedium)
            }
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
/**
 * 管理不应触发边缘手势的应用包名。
 * 输入框和“是否显示选应用对话框”属于此面板自己的短期 UI 状态。
 */
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
    // `orEmpty()` 将可能为 null 的包名转换为空字符串，之后可以直接调用字符串方法。
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
/** 显示无障碍服务是否运行，并提供跳转到系统设置的操作入口。 */
private fun AccessibilityKeepAlivePanel(
    enabled: Boolean,
    status: String?,
    onEnabledChange: (Boolean) -> Unit,
    openGuide: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "无障碍保活", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "ROOT 优先；无 ROOT 时使用已配对的 ADB 保活",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (!status.isNullOrBlank()) {
                Text(text = status, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = openGuide) {
                Text(text = "查看保活教程与 ADB 命令")
            }
        }
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
/**
 * 全局手势开关和阈值设置面板。
 * 每个 `on...Change` 参数都是回调函数：子界面不直接保存数据，而是把新值交给父界面处理。
 */
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
                title = stringResource(id = R.string.short_pull_threshold),
                value = shortPullThreshold,
                range = 20f..100f,
                suffix = stringResource(id = R.string.dp_suffix),
                onValueChange = onShortPullThresholdChange
            )
            SliderRow(
                title = stringResource(id = R.string.long_pull_threshold),
                value = longPullThreshold,
                range = 110f..285f,
                suffix = stringResource(id = R.string.dp_suffix),
                onValueChange = onLongPullThresholdChange
            )
        }
    }
}

@Composable
/**
 * 编辑一侧屏幕边缘的多个可触发区域。
 * `configs` 是不可变列表；修改某一项时会创建新列表，再通过回调交给上层保存。
 */
internal fun EdgeConfigPanel(
    title: String,
    side: EdgeSide,
    configs: List<EdgeZoneConfig>,
    onConfigsChange: (List<EdgeZoneConfig>) -> Unit,
    onConfigAdjusted: (EdgeZoneConfig) -> Unit = {}
) {
    // 滑块拖动期间只更新草稿和预览，避免每一格变化都重建全部悬浮层。
    var draftConfigs by remember(side, configs) {
        mutableStateOf(normalizeEdgeConfigs(side, configs))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            draftConfigs.forEachIndexed { index, config ->
                if (index > 0) {
                    HorizontalDivider()
                }
                EdgeZoneConfigSection(
                    title = stringResource(id = R.string.edge_zone_title, index + 1),
                    config = config,
                    canRemove = draftConfigs.isNotEmpty(),
                    onConfigChange = { updated, adjustmentMode ->
                        val updatedConfigs = draftConfigs.toMutableList()
                        updatedConfigs[index] = updated
                        val normalizedUpdatedConfigs = normalizeEdgeConfigs(side, updatedConfigs)
                        draftConfigs = normalizedUpdatedConfigs
                        onConfigAdjusted(normalizedUpdatedConfigs[index])
                        if (adjustmentMode == EdgeConfigAdjustmentMode.Persist) {
                            onConfigsChange(normalizedUpdatedConfigs)
                        }
                    },
                    onSliderAdjustmentFinished = {
                        onConfigsChange(draftConfigs)
                    },
                    onRemove = {
                        val updatedConfigs = normalizeEdgeConfigs(
                            side,
                            draftConfigs.filterIndexed { itemIndex, _ -> itemIndex != index }
                        )
                        draftConfigs = updatedConfigs
                        onConfigsChange(updatedConfigs)
                    }
                )
            }
            OutlinedButton(
                enabled = draftConfigs.size < EdgeZoneConfig.MAX_ZONES_PER_SIDE,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = {
                    val updatedConfigs = normalizeEdgeConfigs(
                        side,
                        draftConfigs + EdgeZoneConfig.defaultFor(side, draftConfigs.size)
                    )
                    draftConfigs = updatedConfigs
                    onConfigsChange(updatedConfigs)
                }
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.add_edge_zone))
            }
        }
    }
}

@Composable
/** 编辑一个边缘区域的开关、宽度和上下留白。 */
private fun EdgeZoneConfigSection(
    title: String,
    config: EdgeZoneConfig,
    canRemove: Boolean,
    onConfigChange: (EdgeZoneConfig, EdgeConfigAdjustmentMode) -> Unit,
    onSliderAdjustmentFinished: () -> Unit,
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
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.remove_edge_zone))
            }
        }
        SwitchRow(
            label = stringResource(id = R.string.enabled),
            checked = config.enabled,
            onCheckedChange = {
                // data class 的 `copy` 会返回新对象；sanitized() 则保证数值仍在合法范围内。
                onConfigChange(config.copy(enabled = it).sanitized(), edgeConfigAdjustmentMode(isFinished = true))
            }
        )
        SliderRow(
            title = stringResource(id = R.string.edge_thickness),
            value = config.thicknessDp,
            range = EdgeZoneConfig.MIN_THICKNESS_DP.toFloat()..72f,
            suffix = stringResource(id = R.string.dp_suffix),
            onValueChange = {
                onConfigChange(config.copy(thicknessDp = it).sanitized(), edgeConfigAdjustmentMode(isFinished = false))
            },
            onValueChangeFinished = onSliderAdjustmentFinished
        )
        SliderRow(
            title = stringResource(
                id = if (config.side == EdgeSide.LEFT) R.string.left_edge_inset else R.string.right_edge_inset
            ),
            value = config.edgeInsetDp,
            range = EdgeZoneConfig.MIN_EDGE_INSET_DP.toFloat()..EdgeZoneConfig.MAX_EDGE_INSET_DP.toFloat(),
            suffix = stringResource(id = R.string.dp_suffix),
            onValueChange = {
                onConfigChange(config.copy(edgeInsetDp = it).sanitized(), edgeConfigAdjustmentMode(isFinished = false))
            },
            onValueChangeFinished = onSliderAdjustmentFinished
        )
        SliderRow(
            title = stringResource(id = R.string.edge_top_inset),
            value = config.topInsetPercent,
            range = 0f..EdgeZoneConfig.MAX_INSET_PERCENT.toFloat(),
            suffix = stringResource(id = R.string.percent_suffix),
            onValueChange = {
                onConfigChange(config.copy(topInsetPercent = it).sanitized(), edgeConfigAdjustmentMode(isFinished = false))
            },
            onValueChangeFinished = onSliderAdjustmentFinished
        )
        SliderRow(
            title = stringResource(id = R.string.edge_bottom_inset),
            value = config.bottomInsetPercent,
            range = 0f..EdgeZoneConfig.MAX_INSET_PERCENT.toFloat(),
            suffix = stringResource(id = R.string.percent_suffix),
            onValueChange = {
                onConfigChange(config.copy(bottomInsetPercent = it).sanitized(), edgeConfigAdjustmentMode(isFinished = false))
            },
            onValueChangeFinished = onSliderAdjustmentFinished
        )
    }
}

/**
 * 规范化边缘区域配置：限制最多数量、重设所属边和连续编号、清理非法值，且至少保留一个区域。
 * 这条链式写法从上到下依次执行 `take`、`mapIndexed`、`ifEmpty`。
 */
private fun normalizeEdgeConfigs(side: EdgeSide, configs: List<EdgeZoneConfig>): List<EdgeZoneConfig> {
    return configs
        .take(EdgeZoneConfig.MAX_ZONES_PER_SIDE)
        .mapIndexed { index, config ->
            config.copy(side = side, zoneId = index).sanitized()
        }
}

@Composable
/** 显示某一侧所有手势与当前绑定动作，并在用户点击时请求打开选择器。 */
internal fun GestureMappingPanel(
    side: EdgeSide,
    title: String,
    preferences: UniVergePreferences,
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
                // revision 每次选择动作后递增，使 remember 重新读取最新的持久化配置。
                val action = remember(revision, side, gestureType) {
                    preferences.actionFor(side, gestureType)
                }
                GestureActionRow(
                    label = gestureLabel(gestureType),
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
/** 一行“手势名称 + 当前动作 + 修改按钮”的可复用布局。 */
private fun GestureActionRow(
    label: String,
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
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
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
/**
 * 选择手势动作的对话框，既能选内置系统动作，也能选择或手动填写要启动的应用包名。
 */
internal fun ActionPickerDialog(
    current: GestureAction,
    launchableApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit
) {
    // `as?` 是安全类型转换：current 不是 LaunchApp 时返回 null，再由 orEmpty() 变为空字符串。
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
/**
 * 从可启动应用中选择一项的对话框。
 * 搜索关键字变化时，`remember(query, launchableApps)` 会重新计算过滤结果。
 */
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
/** 把文字和 Switch 组合为一行，减少各设置面板中的重复布局代码。 */
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
private fun DrawerSwitchRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DrawerNavigationRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var lastClickAt by remember { mutableStateOf(0L) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickAt < 700L) return@clickable
                lastClickAt = now
                onClick()
            }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(text = label, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
/**
 * 把标题、整数数值和滑块组合为一行设置。
 * Material 的 Slider 使用 Float，所以在回调时用 `toInt()` 转回本项目保存的整数。
 */
private fun SliderRow(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
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
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range
        )
    }
}

internal enum class EdgeConfigAdjustmentMode {
    Preview,
    Persist
}

internal fun edgeConfigAdjustmentMode(isFinished: Boolean): EdgeConfigAdjustmentMode {
    return if (isFinished) EdgeConfigAdjustmentMode.Persist else EdgeConfigAdjustmentMode.Preview
}

internal fun shouldPersistBottomGestureBarWidth(isFinished: Boolean): Boolean = isFinished

@Composable
/** 将手势枚举转换为本地化显示文本；`when` 类似其他语言中的 switch。 */
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
/** 将底部手势条的手势枚举转换为本地化显示文本。 */
private fun bottomGestureLabel(type: BottomGestureBarGestureType): String {
    return when (type) {
        BottomGestureBarGestureType.Tap -> stringResource(id = R.string.bottom_gesture_tap)
        BottomGestureBarGestureType.SwipeUp -> stringResource(id = R.string.bottom_gesture_swipe_up)
        BottomGestureBarGestureType.SwipeUpHold -> stringResource(id = R.string.bottom_gesture_swipe_up_hold)
        BottomGestureBarGestureType.SwipeHorizontal -> stringResource(id = R.string.bottom_gesture_swipe_horizontal)
        BottomGestureBarGestureType.LongPress -> stringResource(id = R.string.bottom_gesture_long_press)
    }
}

@Composable
/** 配置底部手势条宽度及其每种手势对应的动作。 */
internal fun BottomGestureBarPanel(
    widthDp: Int,
    onWidthChange: (Int) -> Unit,
    onWidthChangeFinished: (() -> Unit)? = null,
    preferences: UniVergePreferences,
    revision: Int,
    onPickAction: (BottomGestureBarGestureType) -> Unit,
    showWidth: Boolean = true,
    showActions: Boolean = true
) {
    val gestures = remember { BottomGestureBarGestureType.entries }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.bottom_gesture_bar_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (showWidth) {
                SliderRow(
                    title = stringResource(id = R.string.bottom_gesture_bar_width),
                    value = widthDp,
                    range = 96f..260f,
                    suffix = stringResource(id = R.string.dp_suffix),
                    onValueChange = onWidthChange,
                    onValueChangeFinished = onWidthChangeFinished
                )
            }
            if (showActions) {
                gestures.forEachIndexed { index, gestureType ->
                    val action = remember(revision, gestureType) {
                        preferences.bottomGestureBarActionFor(gestureType)
                    }
                    GestureActionRow(
                        label = bottomGestureLabel(gestureType),
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
}

@Composable
/** 配置悬浮聊天窗口的磨砂背景、透明度、模糊半径和颜色，并显示实时预览。 */
internal fun FloatingChatAppearancePanel(
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
            FloatingChatAppearancePreview(
                frostedBackgroundEnabled = frostedBackgroundEnabled,
                backgroundOpacityPercent = backgroundOpacityPercent,
                blurRadiusDp = blurRadiusDp,
                backgroundColorRgb = backgroundColorRgb
            )
            FloatingChatBackgroundColorPicker(
                selectedColorRgb = backgroundColorRgb,
                onColorSelected = onBackgroundColorRgbChange
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
        }
    }
}

@Composable
/** 显示预设背景色圆点，并允许恢复默认颜色。 */
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
                // RGB 值本身没有透明度；与 0xFF000000 做按位或后强制使用完全不透明的 alpha 通道。
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
/**
 * 用纯 Compose 图形模拟悬浮聊天窗口的背景效果。
 * 这是设置页内的预览，不会直接修改真正的悬浮窗。
 */
private fun FloatingChatAppearancePreview(
    frostedBackgroundEnabled: Boolean,
    backgroundOpacityPercent: Int,
    blurRadiusDp: Int,
    backgroundColorRgb: Int
) {
    // 百分比/像素值先清理到合法范围，再转为 Compose Color 需要的 0f..1f alpha 值。
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
/** 将动作对象转换为界面文本；不同动作类型在 `when` 中映射到不同的字符串资源。 */
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
        GestureAction.PlayVideo -> stringResource(id = R.string.action_play_video)
        is GestureAction.LaunchApp -> stringResource(id = R.string.action_launch_app, action.packageName)
    }
}


@Composable
/** 将毫秒时长格式化为“X 分 Y 秒”或“X 秒”的本地化文本。 */
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

/** 选择器中展示的应用：人类可读名称与 Android 唯一包名。 */
internal data class LaunchableApp(
    val label: String,
    val packageName: String
)

/**
 * 查询系统中带有 LAUNCHER 分类的 Activity，得到可从桌面启动的应用列表。
 *
 * 这是 MainActivity 的扩展函数：写在类外，但第一个参数隐含为当前 Activity，因此可以直接访问
 * `packageManager`。Android 13 及以上使用新的查询 API，旧版本保留兼容调用。
 */
internal fun ComponentActivity.loadLaunchableApps(): List<LaunchableApp> {
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
        // `?: return@mapNotNull null` 表示没有包名时跳过当前元素，而不是退出整个函数。
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        val label = info.loadLabel(packageManager)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        LaunchableApp(label = label, packageName = packageName)
    }
        // 去重后按应用名称（忽略大小写）排序，方便在选择器中浏览。
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
