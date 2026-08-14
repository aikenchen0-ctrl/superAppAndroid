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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap
import kotlinx.coroutines.launch

internal data class ContactRelationsOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun contactRelationsOverlayWindowPresentation() = ContactRelationsOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun contactRelationsStatusBarHeightDp(): Int = 30

internal fun contactRelationsEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

internal fun contactRelationsExitTranslationY(heightPx: Int): Float {
    val height = heightPx.coerceAtLeast(0)
    return if (height == 0) 0f else -height.toFloat()
}

/**
 * 通讯录关系页的全屏无障碍窗口。
 * 测试流程：点击右侧“通讯录”，搜索联系人并切换全部、组织、标签、共同关系、客户分页，最后点击左上角返回。
 */
internal class ContactRelationsOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: ComposeView? = null
    private var owner: AccessibilityOverlayComposeOwner? = null

    /** 使用受保护的窗口附着流程，避免无障碍令牌失效时出现 BadTokenException 泄漏。 */
    fun show() {
        if (view != null) return
        val composeOwner = AccessibilityOverlayComposeOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = contactRelationsEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { ContactRelationsFullScreen(onBack = ::dismiss) }
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
            Log.w(TAG, "failed to add contact relations overlay", error)
        }
    }

    /** 关闭时使用 View 的 translationY 属性动画将完整页面向顶部移出。 */
    fun dismiss() {
        val currentView = view ?: return
        currentView.animate().cancel()
        val height = currentView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        currentView.animate()
            .translationY(contactRelationsExitTranslationY(height))
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
            .onFailure { error -> Log.w(TAG, "failed to remove contact relations overlay", error) }
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
        const val TAG = "ContactRelationsOverlay"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}

private enum class ContactRelationsTab(
    val label: String,
    val segment: FloatingChatContactRelationSegment
) {
    All("全部", FloatingChatContactRelationSegment.All),
    Organization("组织", FloatingChatContactRelationSegment.Organization),
    Tags("标签", FloatingChatContactRelationSegment.Tags),
    Common("共同关系", FloatingChatContactRelationSegment.Common),
    Customers("客户", FloatingChatContactRelationSegment.Customers)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ContactRelationsFullScreen(onBack: () -> Unit) {
    val snapshot by FloatingChatContactRelationsBridge.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { ContactRelationsTab.entries.size }
    var query by remember { mutableStateOf("") }
    val activeTab = ContactRelationsTab.entries[pager.currentPage]

    /** 分页切换时直接触发已有 SCRM 接口，保留 iOS 的五类关系数据语义。 */
    LaunchedEffect(snapshot.selectedWeChatId, pager.currentPage) {
        if (!snapshot.selectedWeChatId.isNullOrBlank()) {
            FloatingChatContactRelationsBridge.loadSegment(activeTab.segment, query)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.height(contactRelationsStatusBarHeightDp().dp))
        TopAppBar(
            title = {
                Text(
                    "通讯录",
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
            actions = {
                IconButton(onClick = FloatingChatContactRelationsBridge::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            label = { Text("搜索联系人、组织或关系") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { FloatingChatContactRelationsBridge.loadSegment(activeTab.segment, query) }
            )
        )
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            ContactRelationsTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal, maxLines = 1) }
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            ContactRelationsPage(
                snapshot = snapshot,
                tab = ContactRelationsTab.entries[page]
            )
        }
    }
}

/** M3 列表页仅展示 Bridge 真实返回的数据，加载、失败和空状态均不伪造。 */
@Composable
private fun ContactRelationsPage(
    snapshot: FloatingChatContactRelationsSnapshot,
    tab: ContactRelationsTab
) {
    val accountName = snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }?.name
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                tab.label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(accountName ?: "当前账号", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                    Text(
                        "联系人 ${snapshot.totalCount}，群聊 ${snapshot.groups.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (snapshot.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        snapshot.error?.takeIf(String::isNotBlank)?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (!snapshot.loading && snapshot.error == null && snapshot.contacts.isEmpty()) {
            item {
                Text(
                    "当前账号没有可显示的联系人关系。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(snapshot.contacts, key = { it.id }) { contact ->
            ContactRelationListItem(contact = contact, segment = tab.segment)
        }
    }
}

@Composable
private fun ContactRelationListItem(
    contact: FloatingChatContactRelation,
    segment: FloatingChatContactRelationSegment
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(contactRelationDescription(contact, segment), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(contact.wxid.ifBlank { "微信号未返回" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { ContactRelationAvatar(contact) }
    )
}

@Composable
private fun ContactRelationAvatar(contact: FloatingChatContactRelation) {
    val bitmap = rememberAsyncImageThumbnailBitmap(LocalContext.current, contact.avatarUrl?.takeIf(String::isNotBlank))
    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (bitmap == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (contact.customerLevel != null) Icons.Filled.Business else Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${contact.name}头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun contactRelationDescription(
    contact: FloatingChatContactRelation,
    segment: FloatingChatContactRelationSegment
): String = when (segment) {
    FloatingChatContactRelationSegment.Organization -> contact.organization.orEmpty()
    FloatingChatContactRelationSegment.Tags -> contact.tags.joinToString("、")
    FloatingChatContactRelationSegment.Common -> contact.commonGroups.joinToString("、")
    FloatingChatContactRelationSegment.Customers -> listOfNotNull(
        contact.customerLevel?.takeIf(String::isNotBlank),
        contact.source?.takeIf(String::isNotBlank)
    ).joinToString("，")
    FloatingChatContactRelationSegment.All -> contact.source?.takeIf(String::isNotBlank) ?: "好友关系"
}.ifBlank { "暂无关系详情" }
