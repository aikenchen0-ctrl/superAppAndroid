package com.paifa.univerge.app

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun NavigationCard(
    title: String,
    description: String,
    icon: ImageVector,
    intentFactory: () -> Intent
) {
    val context = LocalContext.current
    var lastClickAt by remember { mutableLongStateOf(0L) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickAt < NAVIGATION_CLICK_DEBOUNCE_MS) return@clickable
                lastClickAt = now
                context.startActivity(intentFactory())
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private const val NAVIGATION_CLICK_DEBOUNCE_MS = 700L

@Composable
internal fun EdgeConfigurationCards() {
    val context = LocalContext.current
    var lastClickAt by remember { mutableLongStateOf(0L) }
    fun open(target: ConfigurationTarget, destination: Class<out Activity>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < NAVIGATION_CLICK_DEBOUNCE_MS) return
        lastClickAt = now
        context.startActivity(Intent(context, destination).putExtra("configuration_target", target.name))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConfigurationShortcutCard(
            modifier = Modifier.weight(1f),
            target = ConfigurationTarget.LEFT,
            label = "左侧边缘",
            indicatorAlignment = Alignment.CenterStart
        ) { target, destination -> open(target, destination) }
        ConfigurationShortcutCard(
            modifier = Modifier.weight(1f),
            target = ConfigurationTarget.RIGHT,
            label = "右侧边缘",
            indicatorAlignment = Alignment.CenterEnd
        ) { target, destination -> open(target, destination) }
        ConfigurationShortcutCard(
            modifier = Modifier.weight(1f),
            target = ConfigurationTarget.BOTTOM,
            label = "底部边缘",
            indicatorAlignment = Alignment.BottomCenter
        ) { target, destination -> open(target, destination) }
    }
}

@Composable
private fun ConfigurationShortcutCard(
    modifier: Modifier,
    target: ConfigurationTarget,
    label: String,
    indicatorAlignment: Alignment,
    onOpen: (ConfigurationTarget, Class<out Activity>) -> Unit
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
                Box(
                    modifier = Modifier
                        .align(indicatorAlignment)
                        .size(if (target == ConfigurationTarget.BOTTOM) 28.dp else 3.dp, if (target == ConfigurationTarget.BOTTOM) 3.dp else 30.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ConfigurationShortcutAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Tune,
                    label = "样式",
                    onClick = { onOpen(target, EdgeStyleSettingsActivity::class.java) }
                )
                ConfigurationShortcutAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.TouchApp,
                    label = "功能",
                    onClick = { onOpen(target, EdgeFunctionSettingsActivity::class.java) }
                )
            }
        }
    }
}

/**
 * 卡片组件：图标+文本（组合）
 */
@Composable
private fun ConfigurationShortcutAction(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val buttonShape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(buttonShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, buttonShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
