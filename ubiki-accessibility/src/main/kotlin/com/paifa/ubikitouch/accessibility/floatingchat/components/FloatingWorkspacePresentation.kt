package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 全屏悬浮工作区的统一视觉规格。
 * 状态区空间属于工具栏，禁止以独立空白布局占位，供右侧 iconButton 打开的页面复用。
 */
object FloatingWorkspaceTopBarDefaults {
    const val StatusBarTopPaddingDp: Int = 30
}

/**
 * 全屏悬浮工作区的统一位移动画规格。
 * Compose 容器和 WindowManager View 分别使用各自动画 API，但必须使用相同的进入与退出方向。
 */
object FloatingWorkspaceMotion {
    const val EnterOffsetDirection: Int = 1
    const val ExitOffsetDirection: Int = -1

    fun enterTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

    fun exitTranslationY(heightPx: Int): Float = -heightPx.coerceAtLeast(0).toFloat()
}

/**
 * M3 全屏悬浮工作区工具栏。
 * 测试流程：打开任意右侧功能，确认 30dp 状态区已并入工具栏顶部，点击左上返回关闭工作区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingWorkspaceTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = actions
    )
}

/**
 * 与标准标题栏共享同一 surface、Insets 和高度的自定义内容槽。
 * 聊天 header 用它组织左右等权区域，其他工作区继续使用字符串标题重载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingWorkspaceTopAppBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        },
        navigationIcon = {},
        actions = {}
    )
}
