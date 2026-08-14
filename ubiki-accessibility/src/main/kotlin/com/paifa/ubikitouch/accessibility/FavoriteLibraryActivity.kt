package com.paifa.ubikitouch.accessibility

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionItem
import com.paifa.ubikitouch.accessibility.floatingchat.tools.LegacyFavoriteAccountId
import com.paifa.ubikitouch.accessibility.floatingchat.tools.loadFavoriteCollectionItems
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import kotlinx.coroutines.launch

class FavoriteLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFullscreenFloatingWindow()
        setContent {
            BackHandler(onBack = ::finish)
            FavoriteLibraryScreen(applicationContext, ::finish)
        }
    }

    private fun configureFullscreenFloatingWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

private enum class FavoriteCategory(val title: String) {
    Recent("最近使用"), Media("图片与视频"), File("文件"), Link("链接"), Text("文本"), Chat("聊天记录")
}

private val FavoriteBackground = Color(0xFFEDEDED)
private val FavoritePrimary = Color(0xFF1D1D1F)
private val FavoriteSecondary = Color(0xFF8A8A8F)
private val FavoriteBlue = Color(0xFF2E476F)

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
internal fun FavoriteLibraryScreen(context: android.content.Context, onBack: () -> Unit) {
    val snapshot = remember { FloatingChatFavoriteLibraryBridge.currentSnapshot() }
    val accountId = snapshot?.accountId.orEmpty()
    val savedItems = remember(accountId) {
        loadFavoriteCollectionItems(context).filter { item ->
            item.accountId == accountId || (accountId.isNotBlank() && item.accountId == LegacyFavoriteAccountId)
        }
    }
    var query by remember { mutableStateOf("") }
    val pagerState = rememberPagerState { FavoriteCategory.entries.size }
    val pagerScope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(FavoriteBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(favoriteLibraryStatusBarHeightDp().dp))
            FavoriteTopBar(title = "收藏", onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                placeholder = { Text("搜索", color = FavoriteSecondary, fontSize = 17.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = FavoriteSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFCFCFC),
                    unfocusedContainerColor = Color(0xFFFCFCFC),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = FavoriteBlue
                ),
                shape = RoundedCornerShape(12.dp)
            )
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                FavoriteCategory.entries.forEachIndexed { index, category ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(category.title, maxLines = 1) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                FavoriteLibraryPage(
                    items = savedItems,
                    category = FavoriteCategory.entries[page],
                    query = query,
                    accountName = snapshot?.accountName.orEmpty(),
                    onItemSelected = { item ->
                        if (FloatingChatFavoriteLibraryBridge.send(item)) onBack()
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoriteLibraryPage(
    items: List<FavoriteCollectionItem>,
    category: FavoriteCategory,
    query: String,
    accountName: String,
    onItemSelected: (FavoriteCollectionItem) -> Unit
) {
    val visibleItems = remember(items, category, query) {
        items.filter { item ->
            favoriteMatchesCategory(item, category) && favoriteMatchesQuery(item, query)
        }
    }
    if (visibleItems.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text("暂无收藏", color = FavoriteSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 48.dp))
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(visibleItems, key = { item -> "${item.accountId}:${item.messageId}" }) { item ->
            FavoriteLibraryCard(
                item = item,
                accountName = accountName,
                onClick = { onItemSelected(item) }
            )
        }
    }
}

@Composable
private fun FavoriteTopBar(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = FavoritePrimary)
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun FavoriteLibraryCard(item: FavoriteCollectionItem, accountName: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 22.dp, top = 24.dp, end = 20.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = FavoritePrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.description, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp, lineHeight = 19.sp, color = FavoriteSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.source.ifBlank { accountName }, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFFADADB2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("收藏", fontSize = 14.sp, color = Color(0xFFA3A3A8))
                }
            }
            Spacer(Modifier.width(16.dp))
            FavoritePreview(item)
        }
    }
}

@Composable
private fun FavoritePreview(item: FavoriteCollectionItem) {
    val (icon, label, color) = favoritePreview(item)
    Box(Modifier.size(76.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF2F5FA)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun favoriteMatchesCategory(item: FavoriteCollectionItem, category: FavoriteCategory): Boolean = when (category) {
    FavoriteCategory.Recent -> true
    FavoriteCategory.Media -> item.type == FloatingChatMessageType.ImageThumbnail || item.type == FloatingChatMessageType.VideoPreview
    FavoriteCategory.File -> item.type == FloatingChatMessageType.FilePreview
    FavoriteCategory.Link -> item.type in setOf(
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.WebLink,
        FloatingChatMessageType.Article
    )
    FavoriteCategory.Text -> item.type == FloatingChatMessageType.Text || item.type == FloatingChatMessageType.MixedText
    FavoriteCategory.Chat -> item.type == FloatingChatMessageType.Quote || item.type == FloatingChatMessageType.ChatHistory
}

private fun favoriteMatchesQuery(item: FavoriteCollectionItem, query: String): Boolean {
    if (query.isBlank()) return true
    return item.title.contains(query, true) || item.description.contains(query, true) || item.source.contains(query, true)
}

private fun favoritePreview(item: FavoriteCollectionItem): Triple<ImageVector, String, Color> = when (item.type) {
    FloatingChatMessageType.ImageThumbnail -> Triple(Icons.Filled.Image, "IMG", Color(0xFF387AD6))
    FloatingChatMessageType.VideoPreview -> Triple(Icons.Filled.PlayCircle, "VID", Color(0xFF2E438C))
    FloatingChatMessageType.FilePreview -> Triple(Icons.Filled.InsertDriveFile, "DOC", Color(0xFF3D6BB4))
    FloatingChatMessageType.ContactLink, FloatingChatMessageType.MiniProgramLink, FloatingChatMessageType.WebLink, FloatingChatMessageType.Article -> Triple(Icons.Filled.Link, "URL", Color(0xFFDB3430))
    FloatingChatMessageType.Quote, FloatingChatMessageType.ChatHistory -> Triple(Icons.Filled.Collections, "CHAT", Color(0xFF3D947A))
    FloatingChatMessageType.Text, FloatingChatMessageType.MixedText -> Triple(Icons.Filled.TextFields, "TXT", Color(0xFF666B7A))
    else -> Triple(Icons.Filled.Article, "TXT", Color(0xFF666B7A))
}
