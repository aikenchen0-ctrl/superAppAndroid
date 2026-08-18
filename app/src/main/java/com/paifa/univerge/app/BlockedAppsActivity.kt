package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.UbikiAccessibilityService
import com.paifa.univerge.accessibility.UbikiPreferences

class BlockedAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = UbikiPreferences(this)
        val apps = loadLaunchableApps()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlockedAppsScreen(
                        preferences = preferences,
                        launchableApps = apps,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAppsScreen(
    preferences: UbikiPreferences,
    launchableApps: List<LaunchableApp>,
    onBack: () -> Unit
) {
    var blockedPackages by remember { mutableStateOf(preferences.blockedPackages.toList().sorted()) }
    var showDialog by remember { mutableStateOf(false) }

    fun refreshOverlays() {
        UbikiAccessibilityService.instance?.requestOverlayRefresh()
    }

    fun updateBlockedPackages() {
        blockedPackages = preferences.blockedPackages.toList().sorted()
        refreshOverlays()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用黑名单") },
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
                onClick = { showDialog = true }
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = "添加黑名单应用")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AppBlocking,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "打开黑名单应用时，侧边栏不会生效", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "被加入黑名单的应用不会受到侧边栏控制，进入这些应用时也会自动关闭侧边栏。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (blockedPackages.isEmpty()) {
                item {
                    Text(
                        text = "还没有黑名单应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(blockedPackages, key = { it }) { packageName ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AppBlocking,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = launchableApps.firstOrNull { it.packageName == packageName }?.label ?: packageName)
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            preferences.removeBlockedPackage(packageName)
                            updateBlockedPackages()
                        }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除黑名单应用")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddBlockedAppDialog(
            launchableApps = launchableApps,
            onDismiss = { showDialog = false },
            onAddPackage = { packageName ->
                preferences.addBlockedPackage(packageName)
                updateBlockedPackages()
                showDialog = false
            }
        )
    }
}

@Composable
private fun AddBlockedAppDialog(
    launchableApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onAddPackage: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filteredApps = remember(normalizedQuery, launchableApps) {
        if (normalizedQuery.isBlank()) {
            launchableApps
        } else {
            launchableApps.filter {
                it.label.lowercase().contains(normalizedQuery) ||
                    it.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = query.trim().isNotBlank(),
                onClick = { onAddPackage(query.trim()) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("添加黑名单应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("输入应用包名") }
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps.take(30), key = { it.packageName }) { app ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAddPackage(app.packageName) }
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
    )
}
