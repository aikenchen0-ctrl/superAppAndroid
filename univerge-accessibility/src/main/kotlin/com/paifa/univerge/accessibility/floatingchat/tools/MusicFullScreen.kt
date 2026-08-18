package com.paifa.univerge.accessibility.floatingchat.tools

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.core.model.FloatingChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class MusicFullScreenTab(val label: String) {
    Player("播放器"),
    Conversation("当前会话")
}

internal data class MusicShareDraft(
    val title: String,
    val artist: String,
    val audioUrl: String?,
    val durationLabel: String
)

/**
 * 对应 iOS MusicPlayerViewController 的 Android M3 全屏工作区。
 * 测试流程：点击右侧“音乐分享”，编辑歌曲标题和歌手，点击播放验证进度变化，点击分享确认本地音乐卡片进入会话，
 * 切换“当前会话”查看记录，最后点击左上角返回验证实体自上向下退出；无 Dialog 和额外系统窗口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicFullScreen(
    musicMessages: List<FloatingChatMessage>,
    onShareMusic: (MusicShareDraft) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { MusicFullScreenTab.entries.size })
    val initialMessage = remember(musicMessages) { musicMessages.lastOrNull() }
    var title by remember(initialMessage?.id) {
        mutableStateOf(initialMessage?.text?.ifBlank { "路上听" } ?: "路上听")
    }
    var artist by remember(initialMessage?.id) {
        mutableStateOf(initialMessage?.senderName?.ifBlank { "演示歌手" } ?: "演示歌手")
    }
    var audioUrl by remember(initialMessage?.id) { mutableStateOf(initialMessage?.resourceUrl.orEmpty()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }
    val mediaPlayer = rememberPlayableMediaPlayer(context, audioUrl)

    DisposableEffect(mediaPlayer) {
        onDispose { mediaPlayer?.release() }
    }
    LaunchedEffect(playing, mediaPlayer) {
        if (!playing) return@LaunchedEffect
        while (playing) {
            delay(200)
            if (mediaPlayer == null) {
                progress = (progress + 0.01f).coerceAtMost(1f)
                if (progress >= 1f) playing = false
            } else if (!mediaPlayer.isPlaying) {
                playing = false
            }
        }
    }
    fun togglePlayback() {
        if (mediaPlayer == null) {
            playing = !playing
            return
        }
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            playing = false
        } else {
            mediaPlayer.start()
            playing = true
        }
    }

    fun closeWorkspace() {
        mediaPlayer?.stop()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 关闭交给外层悬浮根视图，避免媒体页再叠加一次整页运动。
        FloatingWorkspaceTopAppBar(title = "音乐分享", onBack = ::closeWorkspace)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            MusicFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (MusicFullScreenTab.entries[page]) {
                MusicFullScreenTab.Player -> MusicPlayerPage(
                    title = title,
                    artist = artist,
                    audioUrl = audioUrl,
                    progress = progress,
                    playing = playing,
                    favorite = favorite,
                    onTitleChange = { title = it },
                    onArtistChange = { artist = it },
                    onAudioUrlChange = { audioUrl = it },
                    onProgressChange = { progress = it },
                    onTogglePlayback = ::togglePlayback,
                    onToggleFavorite = { favorite = !favorite },
                    onShare = {
                        onShareMusic(
                            MusicShareDraft(
                                title = title.trim().ifBlank { "路上听" },
                                artist = artist.trim().ifBlank { "演示歌手" },
                                audioUrl = audioUrl.trim().takeIf(String::isNotBlank),
                                durationLabel = "3:42"
                            )
                        )
                    }
                )
                MusicFullScreenTab.Conversation -> MusicConversationPage(musicMessages)
            }
        }
    }
}

@Composable
private fun MusicPlayerPage(
    title: String,
    artist: String,
    audioUrl: String,
    progress: Float,
    playing: Boolean,
    favorite: Boolean,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAudioUrlChange: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "音乐播放器",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(82.dp)
                    )
                    Text(title.ifBlank { "路上听" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
                    Text(artist.ifBlank { "演示歌手" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Slider(value = progress, onValueChange = onProgressChange, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { onProgressChange((progress - 0.1f).coerceAtLeast(0f)) }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "上一段")
                        }
                        FilledIconButton(onClick = onTogglePlayback) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "暂停" else "播放")
                        }
                        IconButton(onClick = { onProgressChange((progress + 0.1f).coerceAtMost(1f)) }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "下一段")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("分享", fontWeight = FontWeight.Normal)
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(Icons.Filled.FavoriteBorder, contentDescription = if (favorite) "取消收藏" else "收藏", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("歌曲标题") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = artist,
                onValueChange = onArtistChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("歌手") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = audioUrl,
                onValueChange = onAudioUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("本地音频地址（可选）") },
                supportingText = { Text("未填写时使用模拟播放") },
                singleLine = true
            )
        }
    }
}

@Composable
private fun MusicConversationPage(messages: List<FloatingChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("当前会话", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        }
        if (messages.isEmpty()) {
            item { Text("暂无音乐分享消息", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(messages, key = { message -> message.id }) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(message.text.ifBlank { "音乐分享" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                            Text(message.detail.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPlayableMediaPlayer(context: Context, audioUrl: String): MediaPlayer? {
    val uri = remember(audioUrl) {
        audioUrl.trim().takeIf { value ->
            value.startsWith("file:") || value.startsWith("content:") || value.startsWith("android.resource:")
        }?.let(Uri::parse)
    }
    return remember(uri) {
        uri?.let { runCatching { MediaPlayer.create(context, it) }.getOrNull() }
    }
}
