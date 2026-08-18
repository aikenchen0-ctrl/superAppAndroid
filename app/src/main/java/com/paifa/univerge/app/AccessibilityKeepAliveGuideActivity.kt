package com.paifa.univerge.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.UbikiAccessibilityService

class AccessibilityKeepAliveGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = accessibilityServiceComponent(this)
        val command = buildAccessibilityEnableCommands(
            packageName = packageName,
            serviceClassName = UbikiAccessibilityService::class.java.name,
            currentServices = ""
        ).joinToString("\n") { "adb shell $it" }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuideContent(
                        packageName = packageName,
                        service = service,
                        command = command,
                        copyCommand = { copyCommand(command) },
                        close = { finish() }
                    )
                }
            }
        }
    }

    private fun copyCommand(command: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB 无障碍保活命令", command))
        Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun GuideContent(
    packageName: String,
    service: String,
    command: String,
    copyCommand: () -> Unit,
    close: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "无障碍保活教程", style = MaterialTheme.typography.headlineSmall)
        Text(text = "本应用包名：$packageName")
        Text(text = "无障碍服务组件：$service", style = MaterialTheme.typography.bodySmall)
        GuideSection("ROOT 方案", "开启开关后，应用先执行 su 检测。检测到 ROOT 时，通过 root shell 写入 accessibility_enabled 和 enabled_accessibility_services，并保留其他已启用的无障碍服务。每次打开应用都会再次校验并恢复。")
        GuideSection("ADB 方案首次配置", "1. Android 11 及以上打开开发者选项和无线调试。\n2. 在无线调试中选择使用配对码配对设备，记录配对端口和配对码。\n3. 使用电脑上的 adb 工具完成配对，并保持设备与电脑在同一网络。\n4. 在本页复制命令执行；命令中的包名和服务名已经按本应用动态生成。\n5. 回到应用打开“无障碍保活”开关。已配对的 ADB 通道会在应用启动时尝试恢复服务。")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "可复制的 ADB 命令", style = MaterialTheme.typography.titleMedium)
                Text(text = command, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = copyCommand) { Text(text = "复制命令") }
                    OutlinedButton(onClick = close) { Text(text = "返回") }
                }
            }
        }
        GuideSection("验证和撤销", "验证：adb shell settings get secure accessibility_enabled；adb shell settings get secure enabled_accessibility_services；也可以执行 adb shell dumpsys accessibility。撤销 ADB 权限请在系统的无线调试页面取消配对；撤销 ROOT 权限请在 ROOT 管理器中关闭本应用授权。")
        GuideSection("实现细节与限制", "ADB 保活使用 AdbKeeper 的无线调试通道，权限级别是 ADB shell，不等于 root。Android 11 以下不支持无线调试配对；部分 ROM 会在重启或 Wi-Fi 切换后关闭无线调试。系统设置中手动关闭服务后，下一次应用启动且开关仍开启时会再次尝试恢复。")
    }
}

@Composable
private fun GuideSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}
