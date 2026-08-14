package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlinx.coroutines.launch

internal data class LeftSidebarOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun leftSidebarOverlayWindowPresentation() = LeftSidebarOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun leftSidebarStatusBarHeightDp(): Int = 30

internal fun leftSidebarEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

internal fun leftSidebarExitTranslationY(heightPx: Int): Float =
    heightPx.coerceAtLeast(0).takeIf { it > 0 }?.toFloat()?.unaryMinus() ?: 0f

/**
 * 左侧全部的全屏无障碍悬浮页。
 * 测试流程：从右侧工具打开，确认自下向上进入；切换预览和显示设置，选择模式后点击左上返回，确认自上向下离开。
 */
internal class LeftSidebarOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: ComposeView? = null
    private var owner: AccessibilityOverlayComposeOwner? = null

    /** 使用受保护的 WindowManager 添加流程，避免无障碍令牌失效时泄漏 BadTokenException。 */
    fun show() {
        if (view != null) return
        FloatingChatLeftSidebarBridge.loadDisplayMode(context)
        val composeOwner = AccessibilityOverlayComposeOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = leftSidebarEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { LeftSidebarFullScreen(onBack = ::dismiss) }
        }
        runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            composeView.post {
                if (view !== composeView) return@post
                composeView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(SHOW_DURATION_MILLIS)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
        }.onFailure { error ->
            composeOwner.destroy()
            Log.w(TAG, "failed to add left sidebar overlay", error)
        }
    }

    /** 关闭时让页面实体通过 translationY 属性动画从顶部退出。 */
    fun dismiss() {
        val currentView = view ?: return
        currentView.animate().cancel()
        val height = currentView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        currentView.animate()
            .translationY(leftSidebarExitTranslationY(height))
            .alpha(0f)
            .setDuration(HIDE_DURATION_MILLIS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction { if (view === currentView) dismissImmediately() }
            .start()
    }

    fun dismissImmediately() {
        val currentView = view ?: return
        view = null
        currentView.animate().cancel()
        owner?.destroy()
        owner = null
        runCatching { windowManager.removeViewImmediate(currentView) }
            .onFailure { error -> Log.w(TAG, "failed to remove left sidebar overlay", error) }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private companion object {
        const val TAG = "LeftSidebarOverlay"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}

private enum class LeftSidebarTab(val label: String) {
    Preview("预览"),
    Settings("显示设置")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LeftSidebarFullScreen(onBack: () -> Unit) {
    val snapshot by FloatingChatLeftSidebarBridge.snapshot.collectAsState()
    val displayMode by FloatingChatLeftSidebarBridge.displayMode.collectAsState()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { LeftSidebarTab.entries.size }

    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        // UI：左侧全部入口复用 UI组件 的全屏 toolbar；状态区不再使用独立空白占位。
        // 测试流程：从右侧左侧全部入口打开，切换分页后点击左上返回，确认向顶部退出。
        FloatingWorkspaceTopAppBar(
            title = "左侧全部",
            onBack = onBack,
            actions = {
                IconButton(onClick = FloatingChatLeftSidebarBridge::open) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新当前数据")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            LeftSidebarTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal, maxLines = 1) }
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (LeftSidebarTab.entries[page]) {
                LeftSidebarTab.Preview -> LeftSidebarPreviewPage(snapshot, displayMode)
                LeftSidebarTab.Settings -> LeftSidebarSettingsPage(displayMode)
            }
        }
    }
}

/** 预览页只渲染当前浮窗同步过来的真实好友和群聊数据。 */
@Composable
private fun LeftSidebarPreviewPage(
    snapshot: FloatingChatLeftSidebarSnapshot,
    displayMode: LeftSidebarDisplayMode
) {
    val contacts = when (displayMode) {
        LeftSidebarDisplayMode.All, LeftSidebarDisplayMode.Friends -> snapshot.contacts
        LeftSidebarDisplayMode.Groups -> emptyList()
    }
    val groups = when (displayMode) {
        LeftSidebarDisplayMode.All, LeftSidebarDisplayMode.Groups -> snapshot.groups
        LeftSidebarDisplayMode.Friends -> emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                displayMode.title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Text(
                "当前帐号 ${contacts.size} 位好友，${groups.size} 个群聊",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (contacts.isEmpty() && groups.isEmpty()) {
            item { Text("当前帐号没有可显示的左侧会话。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (contacts.isNotEmpty()) {
            item { LeftSidebarSectionTitle("好友") }
            items(contacts, key = { "contact-${it.id}" }) { contact ->
                LeftSidebarContactItem(contact, isGroup = false)
            }
        }
        if (groups.isNotEmpty()) {
            item { LeftSidebarSectionTitle("群聊") }
            items(groups, key = { "group-${it.id}" }) { group ->
                LeftSidebarContactItem(group, isGroup = true)
            }
        }
    }
}

@Composable
private fun LeftSidebarSectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun LeftSidebarContactItem(contact: FloatingChatContact, isGroup: Boolean) {
    ListItem(
        headlineContent = {
            Text(
                contact.name,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(if (isGroup) "群聊" else "好友", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(
                if (isGroup) Icons.Filled.Group else Icons.Filled.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

/** 设置页对应 iOS 的 action sheet，但使用全屏 M3 列表避免对话框和底部取消按钮。 */
@Composable
private fun LeftSidebarSettingsPage(displayMode: LeftSidebarDisplayMode) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "左侧栏展示模式",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Text("选择后立即应用到当前悬浮聊天，并保留到下次打开。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(LeftSidebarDisplayMode.entries, key = { it.rawValue }) { option ->
            ListItem(
                headlineContent = {
                    Text(option.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                },
                supportingContent = {
                    Text(
                        when (option) {
                            LeftSidebarDisplayMode.All -> "显示好友和群聊"
                            LeftSidebarDisplayMode.Friends -> "只显示好友用户"
                            LeftSidebarDisplayMode.Groups -> "只显示群聊"
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        if (option == LeftSidebarDisplayMode.Groups) Icons.Filled.Group else Icons.Filled.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = displayMode == option,
                        onClick = { FloatingChatLeftSidebarBridge.selectDisplayMode(context, option) }
                    )
                },
                modifier = Modifier.clickable { FloatingChatLeftSidebarBridge.selectDisplayMode(context, option) }
            )
        }
    }
}
