package com.paifa.univerge.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.UbikiPreferences
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureActionCatalog

class SideFunctionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = UbikiPreferences(this)
        val launchableApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).map { info ->
            SideLaunchableApp(
                label = info.loadLabel(packageManager).toString(),
                packageName = info.activityInfo.packageName,
                isSystem = info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                icon = info.loadIcon(packageManager)
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SideFunctionsScreen(
                        preferences = preferences,
                        apps = launchableApps,
                        packageManager = packageManager,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideFunctionsScreen(
    preferences: UbikiPreferences,
    apps: List<SideLaunchableApp>,
    packageManager: PackageManager,
    onBack: () -> Unit
) {
    var customIds by remember { mutableStateOf(preferences.sideFunctionCustomActionIds) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }

    fun save(ids: List<String>) {
        preferences.sideFunctionCustomActionIds = ids
        customIds = preferences.sideFunctionCustomActionIds
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("侧边功能") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = 30.dp),
                onClick = { showActionPicker = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = "添加侧边功能")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "边缘手势拉出后，手指进入功能区域即可执行。前两项固定，自定义功能最多 5 个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                FixedFunctionCard(
                    title = "关闭所有",
                    detail = "关闭本应用的悬浮聊天、视频和手势效果"
                )
            }
            item {
                FixedFunctionCard(
                    title = "返回",
                    detail = "执行系统返回操作"
                )
            }
            item {
                Text("自定义功能", style = MaterialTheme.typography.titleMedium)
            }
            items(customIds, key = { it }) { actionId ->
                val action = GestureAction.fromId(actionId)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = sideFunctionActionLabel(action, packageManager),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "拖出侧边栏后可直接执行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { save(customIds.filterNot { it == actionId }) }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除侧边功能")
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        SideFunctionAppPicker(
            apps = apps,
            onDismiss = { showAppPicker = false },
            onSelect = { app ->
                save(customIds + GestureAction.LaunchApp(app.packageName).id)
                showAppPicker = false
            }
        )
    }
    if (showActionPicker) {
        AlertDialog(
            onDismissRequest = { showActionPicker = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showActionPicker = false }) {
                    Text("取消")
                }
            },
            title = { Text("选择功能") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GestureActionCatalog.systemActions.filter { it != GestureAction.None }) { action ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = customIds.size < 5 && action.id !in customIds,
                            onClick = {
                                save(customIds + action.id)
                                showActionPicker = false
                            }
                        ) {
                            Text(sideFunctionActionLabel(action, packageManager))
                        }
                    }
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = customIds.size < 5,
                            onClick = {
                                showActionPicker = false
                                showAppPicker = true
                            }
                        ) {
                            Text("打开应用")
                        }
                    }
                }
            }
        )
    }
}

private data class SideLaunchableApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: Drawable
)

@Composable
private fun SideFunctionAppPicker(
    apps: List<SideLaunchableApp>,
    onDismiss: () -> Unit,
    onSelect: (SideLaunchableApp) -> Unit
) {
    var systemTab by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("打开应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = if (systemTab) 1 else 0) {
                    Tab(selected = !systemTab, onClick = { systemTab = false }, text = { Text("用户应用") })
                    Tab(selected = systemTab, onClick = { systemTab = true }, text = { Text("系统应用") })
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps.filter { it.isSystem == systemTab }, key = { it.packageName }) { app ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onSelect(app) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    factory = { context -> ImageView(context).apply { setImageDrawable(app.icon) } },
                                    update = { it.setImageDrawable(app.icon) },
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
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
private fun FixedFunctionCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun sideFunctionActionLabel(
    action: GestureAction,
    packageManager: PackageManager
): String = when (action) {
    GestureAction.None -> "无动作"
    GestureAction.Back -> "返回"
    GestureAction.Home -> "主页"
    GestureAction.Recents -> "最近任务"
    GestureAction.Notifications -> "通知栏"
    GestureAction.QuickSettings -> "快捷设置"
    GestureAction.Screenshot -> "截图"
    GestureAction.LockScreen -> "锁屏"
    GestureAction.VolumeUp -> "音量增加"
    GestureAction.VolumeDown -> "音量减少"
    GestureAction.ExpandFloatingChat -> "展开悬浮聊天"
    GestureAction.CollapseFloatingChat -> "收起悬浮聊天"
    GestureAction.PlayVideo -> "显示视频"
    is GestureAction.LaunchApp -> {
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(action.packageName, 0)).toString()
        }.getOrDefault(action.packageName)
        "打开 $appName"
    }
}
