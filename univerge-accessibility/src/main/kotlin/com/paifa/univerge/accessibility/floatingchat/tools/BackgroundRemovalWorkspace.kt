package com.paifa.univerge.accessibility.floatingchat.tools

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

private const val BackgroundRemovalPreferences = "background_removal_preferences"
private const val AutomaticRemovalKey = "automatic_background_removal_enabled"

/** 当前 Android 端没有与 iOS Vision 等价的本地分割引擎，不能伪造处理成功。 */
private const val AndroidBackgroundRemovalSupported = false

/**
 * 右侧智能抠图的 M3 全屏工作区。
 * 接口说明：当前仅持久化自动处理偏好；图片分割接口尚未接入时明确禁用操作，不返回伪造结果。
 * 测试流程：点击右侧智能抠图，确认聊天根不消失、工具栏从 UI组件 同一根容器进入；点击左上返回。
 */
@Composable
internal fun BackgroundRemovalWorkspace(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = context.getSharedPreferences(BackgroundRemovalPreferences, Context.MODE_PRIVATE)
    var automaticEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean(AutomaticRemovalKey, false))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        FloatingWorkspaceTopAppBar(title = "智能抠图", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BackgroundRemovalOverviewCard(automaticEnabled)
            }
            item {
                BackgroundRemovalPreferenceCard(
                    automaticEnabled = automaticEnabled,
                    onAutomaticEnabledChange = { next ->
                        automaticEnabled = next
                        preferences.edit().putBoolean(AutomaticRemovalKey, next).apply()
                    }
                )
            }
            item {
                BackgroundRemovalCapabilityCard()
            }
            item {
                Button(
                    onClick = {},
                    enabled = automaticEnabled && AndroidBackgroundRemovalSupported,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        imageVector = if (automaticEnabled) Icons.Filled.Image else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (automaticEnabled) "选择图片并抠背景" else "开启后可选择图片")
                }
            }
            item {
                Text(
                    text = "图片预览中的“更多操作 - 扣除背景”会在 Android 本地抠图引擎接入后提供。",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BackgroundRemovalOverviewCard(automaticEnabled: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "为照片生成透明背景",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = if (automaticEnabled && AndroidBackgroundRemovalSupported) "自动处理已开启" else "当前设备尚未接入处理引擎",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "开启后，新拍摄或新选择的图片会在发送前自动处理。关闭时仍按原图发送。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BackgroundRemovalPreferenceCard(
    automaticEnabled: Boolean,
    onAutomaticEnabledChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "自动处理新照片",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Android 端尚未接入本地抠图引擎，新图片、已有图片和视频都会按原图发送。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = automaticEnabled,
                onCheckedChange = onAutomaticEnabledChange,
                enabled = AndroidBackgroundRemovalSupported
            )
        }
    }
}

@Composable
private fun BackgroundRemovalCapabilityCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "处理方式",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "iOS 参考实现使用系统 Vision 生成透明 PNG。Android 端未接入等价前景分割模型，因此不会伪造处理成功或替换原图。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
