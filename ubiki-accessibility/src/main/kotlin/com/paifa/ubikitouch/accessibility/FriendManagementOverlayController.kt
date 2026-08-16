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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlinx.coroutines.launch

internal data class FriendManagementOverlayPresentation(val width: Int, val height: Int, val type: Int, val focusable: Boolean)

internal fun friendManagementOverlayWindowPresentation() = FriendManagementOverlayPresentation(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    true
)

internal fun friendManagementStatusBarHeightDp(): Int = 30
internal fun friendManagementEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()
internal fun friendManagementExitTranslationY(heightPx: Int): Float = -heightPx.coerceAtLeast(0).toFloat()

/** Fullscreen host for friend management. Test by opening the rail item, refreshing, then switching all three tabs. */
internal class FriendManagementOverlayController(private val context: Context, private val windowManager: WindowManager) {
    private var view: ComposeView? = null
    private var owner: AccessibilityOverlayComposeOwner? = null

    /** UI entry uses a guarded WindowManager attach so failed tokens do not retain a Compose lifecycle. */
    fun show() {
        if (view != null) return
        val composeOwner = AccessibilityOverlayComposeOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = friendManagementEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { FriendManagementFullscreenScreen(onBack = ::dismiss) }
        }
        runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            composeView.post {
                if (view !== composeView) return@post
                composeView.animate().translationY(0f).alpha(1f).setDuration(260L)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }.onFailure {
            composeOwner.destroy()
            Log.w(TAG, "failed to add friend management overlay", it)
        }
    }

    fun dismiss() {
        val current = view ?: return
        val height = current.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        current.animate().cancel()
        current.animate().translationY(friendManagementExitTranslationY(height)).alpha(0f).setDuration(190L)
            .setInterpolator(AccelerateInterpolator(1.5f)).withEndAction { if (view === current) dismissImmediately() }.start()
    }

    fun dismissImmediately() {
        val current = view ?: return
        view = null
        current.animate().cancel()
        owner?.destroy()
        owner = null
        runCatching { windowManager.removeViewImmediate(current) }.onFailure { Log.w(TAG, "failed to remove friend management overlay", it) }
        FloatingChatFriendManagementBridge.notifyClosed()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private companion object { const val TAG = "FriendManagementOverlay" }
}

private enum class FriendManagementTab(val label: String) { Requests("申请"), Friends("好友"), Groups("群聊") }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FriendManagementFullscreenScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { FriendManagementTab.entries.size }
    var refreshToken by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val snapshot = FloatingChatFriendManagementBridge.snapshot
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：好友管理复用 UI组件 的 surface M3 工具栏，顶部 30dp 由工具栏 Insets 承载。
        // 测试流程：从右侧打开好友管理，刷新后切换分页，再从左上返回确认根动画关闭。
        FloatingWorkspaceTopAppBar(
            title = "好友管理",
            onBack = onBack,
            actions = {
                IconButton(onClick = { FloatingChatFriendManagementBridge.refresh(); refreshToken++ }) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            FriendManagementTab.entries.forEachIndexed { index, tab -> Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(tab.label, fontWeight = FontWeight.Normal) }) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (FriendManagementTab.entries[page]) {
                FriendManagementTab.Requests -> FriendRequestPage(onPull = { FloatingChatFriendManagementBridge.pullFriendRequests(); refreshToken++ })
                FriendManagementTab.Friends -> FriendContactPage(snapshot.contacts, false)
                FriendManagementTab.Groups -> FriendContactPage(snapshot.groups, true)
            }
        }
    }
}

/** Existing Bridge interface dispatches the iOS-equivalent pull-friend-request task through the service. */
@Composable
private fun FriendRequestPage(onPull: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("好友申请", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    Text("从手机端同步待处理申请，结果会回写当前联系人数据。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onPull, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Filled.PersonAdd, "拉取好友申请") }
                }
            }
        }
        item { Text("暂无本地待处理申请，点击右上图标拉取。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FriendContactPage(contacts: List<FloatingChatContact>, group: Boolean) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (contacts.isEmpty()) item { Text(if (group) "当前账号暂无已同步群聊" else "当前账号暂无已同步好友", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(contacts, key = { it.id }) { contact ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(if (group) Icons.Filled.Group else Icons.Filled.Contacts, null, tint = MaterialTheme.colorScheme.primary)
                    Text(contact.name, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (group) "${contact.groupMemberContacts.size} 位成员，${contact.id}" else contact.id, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
