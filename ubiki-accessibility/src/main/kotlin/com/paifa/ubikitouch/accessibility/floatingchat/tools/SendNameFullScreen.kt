package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

/**
 * iOS「发送名字设置」对应的 Android Material 3 全屏悬浮页。
 *
 * iOS 仅维护按账号划分的本地展示开关，没有对应 OpenAPI；Android 通过回调更新同一状态，
 * 不伪造服务端接口。测试流程：从右侧「携带名字」进入，切换开关后返回聊天，确认当前账号
 * 的发送消息名称立即显示或隐藏；切换账号后，确认两者状态互不影响。
 */
@Composable
internal fun SendNameFullScreen(
    accountName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    // UI：由聊天根 AnimatedVisibility 承担唯一进出场，避免页面位移叠加造成首帧不可见。
    // 测试流程：从右侧“携带名字”打开，确认标题栏内置状态区，点击返回后内容随根视图上滑退出。
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(title = "携带名字", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "发送名字设置",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        headlineContent = { Text("发送消息时携带名字", fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                text = "$accountName 发送的消息显示账号名称",
                                fontWeight = FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = onEnabledChange)
                        }
                    )
                }
            }
        }
    }
}
