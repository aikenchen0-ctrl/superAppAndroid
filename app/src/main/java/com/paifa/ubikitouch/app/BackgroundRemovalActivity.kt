package com.paifa.ubikitouch.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatBackgroundRemovalBridge
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

class BackgroundRemovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 兼容旧 Activity 深链：实际页面由聊天根的 BottomPanelMode 承载，禁止创建第二个 Window。
        // 测试流程：从旧入口启动后，确认直接打开已有悬浮聊天内的智能抠图工作区。
        FloatingChatBackgroundRemovalBridge.open()
        finish()
    }
}

private const val BackgroundRemovalPreferences = "background_removal_preferences"
private const val AutomaticRemovalKey = "automatic_background_removal_enabled"
private const val AndroidBackgroundRemovalSupported = false
private val RemovalPrimary = Color(0xFF152426)
private val RemovalSecondary = Color(0xFF596568)
private val RemovalGreen = Color(0xFF1A9069)

@Composable
private fun BackgroundRemovalScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = context.getSharedPreferences(BackgroundRemovalPreferences, Context.MODE_PRIVATE)
    var enabled by rememberSaveable { mutableStateOf(preferences.getBoolean(AutomaticRemovalKey, false)) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        FloatingWorkspaceTopAppBar(title = "智能抠图", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackgroundRemovalHeader(enabled)
            AutomaticRemovalCard(
                enabled = enabled,
                onEnabledChanged = { newValue ->
                    enabled = newValue
                    preferences.edit().putBoolean(AutomaticRemovalKey, newValue).apply()
                }
            )
            ProcessingMethodCard()
            Button(
                onClick = {},
                enabled = enabled && AndroidBackgroundRemovalSupported,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RemovalGreen,
                    disabledContainerColor = Color(0xFF858F93),
                    contentColor = Color.White,
                    disabledContentColor = Color.White
                )
            ) {
                Icon(if (enabled) Icons.Filled.Image else Icons.Filled.Lock, null, Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (enabled) "选择图片并抠背景" else "开启后可选择图片", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "图片预览中的“更多操作 - 扣除背景”将在接入 Android 本地抠图引擎后提供。",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color(0xFF6A7679),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BackgroundRemovalHeader(enabled: Boolean) {
    RemovalCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(52.dp).background(RemovalGreen, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Image, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("为照片生成透明背景", color = RemovalPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    if (enabled) "已开启" else "系统不支持",
                    color = if (enabled) Color(0xFF106B47) else Color(0xFF9A4B10),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(if (enabled) Color(0xFFCCF0DC) else Color(0xFFFFE8CC), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("开启后，新拍摄或新选择的图片会在发送前自动处理。关闭时仍按原图发送。", color = RemovalSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun AutomaticRemovalCard(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    RemovalCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("自动处理新照片", color = RemovalPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("当前 Android 版本暂未接入本地抠图引擎。新图片、已有图片和视频都会按原图发送。", color = RemovalSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                enabled = AndroidBackgroundRemovalSupported,
                colors = SwitchDefaults.colors(checkedTrackColor = RemovalGreen)
            )
        }
    }
}

@Composable
private fun ProcessingMethodCard() {
    RemovalCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Security, null, tint = Color(0xFF2D66AD), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("处理方式", color = RemovalPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("iOS 参考实现使用系统 Vision 生成透明 PNG。Android 端尚未接入等价的前景分割模型，因此不会伪造处理成功或替换原图。", color = RemovalSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun RemovalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
