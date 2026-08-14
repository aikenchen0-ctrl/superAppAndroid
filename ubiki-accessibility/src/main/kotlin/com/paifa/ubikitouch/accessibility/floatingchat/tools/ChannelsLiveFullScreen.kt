package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.launch

internal const val ChannelsLiveStatusBarHeightDp = 30
private const val ChannelsLiveAnimationDurationMillis = 260

internal enum class ChannelsLiveFullScreenTab(val label: String) {
    Draft("直播素材"),
    Conversation("当前会话")
}

internal sealed interface ChannelsLiveDraftState {
    data object Idle : ChannelsLiveDraftState
    data object Creating : ChannelsLiveDraftState
    data class Created(val message: String) : ChannelsLiveDraftState
    data class Failed(val message: String) : ChannelsLiveDraftState
}

/**
 * 对应 iOS channelsLive 消息详情的 Android Material 3 全屏工作区。
 * 测试流程：右侧点击“视频号直播”，输入已同步朋友圈编号创建直播素材，再从左上角返回验证向下退出动画。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelsLiveFullScreen(
    channelsLives: List<FloatingChatMessage>,
    draftState: ChannelsLiveDraftState,
    onCreateLiveDraft: (snsId: Long, materialName: String?) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ChannelsLiveFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var liveRoomNotice by remember { mutableStateOf<String?>(null) }
    val pageTranslationY = remember { Animatable(0f) }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            pageTranslationY.snapTo(pageHeightPx)
            pageTranslationY.animateTo(0f, tween(ChannelsLiveAnimationDurationMillis))
            entered = true
        }
    }

    fun closeWithExitAnimation() {
        if (exiting) return
        exiting = true
        scope.launch {
            pageTranslationY.animateTo(pageHeightPx, tween(ChannelsLiveAnimationDurationMillis))
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { translationY = pageTranslationY.value }
    ) {
        Spacer(Modifier.height(30.dp))
        TopAppBar(
            title = {
                Text(
                    text = "视频号直播",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
            },
            navigationIcon = {
                IconButton(onClick = ::closeWithExitAnimation) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            ChannelsLiveFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (ChannelsLiveFullScreenTab.entries[page]) {
                ChannelsLiveFullScreenTab.Draft -> ChannelsLiveDraftPage(draftState, onCreateLiveDraft)
                ChannelsLiveFullScreenTab.Conversation -> ChannelsLiveConversationPage(
                    channelsLives = channelsLives,
                    liveRoomNotice = liveRoomNotice,
                    onEnterLiveRoom = { liveRoomNotice = "已进入直播间" }
                )
            }
        }
    }
}

@Composable
private fun ChannelsLiveDraftPage(
    draftState: ChannelsLiveDraftState,
    onCreateLiveDraft: (snsId: Long, materialName: String?) -> Unit
) {
    var snsIdText by remember { mutableStateOf("") }
    var materialName by remember { mutableStateOf("") }
    val snsId = snsIdText.toLongOrNull()
    val canSubmit = snsId != null && snsId > 0L && draftState !is ChannelsLiveDraftState.Creating

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "创建直播素材",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = snsIdText,
                        onValueChange = { value -> snsIdText = value.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("已同步朋友圈编号") },
                        singleLine = true,
                        isError = snsIdText.isNotBlank() && snsId == null
                    )
                    OutlinedTextField(
                        value = materialName,
                        onValueChange = { materialName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("素材名称（可选）") },
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = { onCreateLiveDraft(requireNotNull(snsId), materialName.ifBlank { null }) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (draftState is ChannelsLiveDraftState.Creating) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("创建直播素材", fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
        when (draftState) {
            is ChannelsLiveDraftState.Created -> item {
                Text(draftState.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
            }
            is ChannelsLiveDraftState.Failed -> item {
                Text(draftState.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Normal)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ChannelsLiveConversationPage(
    channelsLives: List<FloatingChatMessage>,
    liveRoomNotice: String?,
    onEnterLiveRoom: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "当前会话",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        if (channelsLives.isEmpty()) {
            item { Text("暂无视频号直播消息", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            liveRoomNotice?.let { notice ->
                item { Text(notice, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal) }
            }
            items(channelsLives.size, key = { index -> channelsLives[index].id }) { index ->
                val message = channelsLives[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                        headlineContent = { Text(message.text.ifBlank { "视频号直播" }, fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                message.detail ?: "点击预约或进入直播间查看互动状态。",
                                fontWeight = FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(onClick = onEnterLiveRoom) {
                                Text("进入直播间", fontWeight = FontWeight.Normal)
                            }
                        }
                    )
                }
            }
        }
    }
}
