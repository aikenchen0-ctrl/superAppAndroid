package com.paifa.univerge.app

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.QuietHoursSchedule
import com.paifa.univerge.accessibility.UbikiAccessibilityService
import com.paifa.univerge.accessibility.UbikiPreferences

class PauseSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = UbikiPreferences(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PauseSettingsScreen(
                        preferences = preferences,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseSettingsScreen(
    preferences: UbikiPreferences,
    onBack: () -> Unit
) {
    var pausedUntilEpochMs by remember { mutableStateOf(preferences.pausedUntilEpochMs) }
    var schedules by remember { mutableStateOf(preferences.quietHoursSchedules) }

    fun refreshOverlays() {
        UbikiAccessibilityService.instance?.requestOverlayRefresh()
    }

    fun updateSchedules(newValue: List<QuietHoursSchedule>) {
        preferences.quietHoursSchedules = newValue
        schedules = preferences.quietHoursSchedules
        refreshOverlays()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("免打扰") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            AddQuietHoursFab(
                modifier = Modifier.padding(bottom = 30.dp),
                onRangeSelected = { start, end ->
                    updateSchedules(schedules + QuietHoursSchedule(startMinuteOfDay = start, endMinuteOfDay = end))
                }
            )
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
                QuickPauseCard(
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
                Text(
                    text = "免打扰时段",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        text = "还没有设置免打扰时段。添加后，在对应时间内侧边栏会自动关闭。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(schedules, key = { it.id }) { schedule ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoNotDisturbOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "\${formatMinuteOfDay(schedule.startMinuteOfDay)} - \${formatMinuteOfDay(schedule.endMinuteOfDay)}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) "全天免打扰" else "在这个时间段内自动关闭侧边栏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { updateSchedules(schedules.filterNot { it.id == schedule.id }) }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除时段")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPauseCard(
    pausedUntilEpochMs: Long,
    onPauseFor: (Long) -> Unit,
    onResumeNow: () -> Unit
) {
    val remainingMs = (pausedUntilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "快速免打扰", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (remainingMs > 0L) "当前还剩 \${formatRemainingTimeLabel(remainingMs)}" else "立即暂停侧边栏一段时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onPauseFor(5L * 60_000L) }) { Text("5分钟") }
                OutlinedButton(onClick = { onPauseFor(15L * 60_000L) }) { Text("15分钟") }
                OutlinedButton(onClick = { onPauseFor(30L * 60_000L) }) { Text("30分钟") }
            }
            OutlinedButton(onClick = onResumeNow) {
                Icon(imageVector = Icons.Outlined.AlarmOff, contentDescription = null)
                Text(text = "立即恢复", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AddQuietHoursFab(
    modifier: Modifier = Modifier,
    onRangeSelected: (Int, Int) -> Unit
) {
    val context = LocalContext.current

    fun pickTime(initialHour: Int, initialMinute: Int, onResult: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onResult(hour * 60 + minute) },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    FloatingActionButton(
        modifier = modifier,
        onClick = {
            pickTime(22, 0) { start ->
                pickTime(7, 0) { end ->
                    onRangeSelected(start, end)
                }
            }
        }
    ) {
        Icon(imageVector = Icons.Outlined.AddAlarm, contentDescription = "添加免打扰时段")
    }
}

private fun formatMinuteOfDay(value: Int): String {
    val hour = (value / 60).coerceIn(0, 23)
    val minute = (value % 60).coerceIn(0, 59)
    return "%02d:%02d".format(hour, minute)
}
