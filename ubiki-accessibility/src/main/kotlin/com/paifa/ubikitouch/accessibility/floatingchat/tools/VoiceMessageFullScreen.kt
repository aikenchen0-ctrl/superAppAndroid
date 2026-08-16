package com.paifa.ubikitouch.accessibility.floatingchat.tools

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.launch

internal const val VoiceMessageStatusBarHeightDp = 30

internal enum class VoiceMessageFullScreenTab(val label: String) {
    Recorder("录音发送"),
    Conversation("当前会话")
}

internal fun voiceMessageEnterOffsetDirection(): Int = 1

internal fun voiceMessageExitOffsetDirection(): Int = -1

/**
 * 对应 iOS ChatWindow 的语音消息录制与播放工作区，使用 Android M3 全屏 View。
 * 测试流程：点击右侧“语音消息”，授权麦克风后开始录音，点击停止发送；切换“当前会话”播放语音，
 * 最后点击左上角返回，确认页面自下向上进入、自上向下退出，不使用 Dialog 或额外系统窗口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceMessageFullScreen(
    voiceMessages: List<FloatingChatMessage>,
    permissionRequestToken: Int,
    onSendVoice: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { VoiceMessageFullScreenTab.entries.size })
    fun closeWorkspace() {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 语音录制与播放共享悬浮根动画，工具栏只负责安全区和返回操作。
        FloatingWorkspaceTopAppBar(title = "语音消息", onBack = ::closeWorkspace)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            VoiceMessageFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (VoiceMessageFullScreenTab.entries[page]) {
                VoiceMessageFullScreenTab.Recorder -> VoiceRecorderPage(
                    permissionRequestToken = permissionRequestToken,
                    onSendVoice = { audioUri, durationMs ->
                        onSendVoice(audioUri, durationMs)
                        closeWorkspace()
                    }
                )
                VoiceMessageFullScreenTab.Conversation -> VoiceConversationPage(voiceMessages)
            }
        }
    }
}

@Composable
private fun VoiceRecorderPage(
    permissionRequestToken: Int,
    onSendVoice: (String, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "录音发送",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "录制语音消息",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = "录音完成后会写入当前会话，并保留本地音频地址。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal
                    )
                    RealVoiceInputPanel(
                        permissionRequestToken = permissionRequestToken,
                        onSendVoice = onSendVoice,
                        showCancelButton = false
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceConversationPage(messages: List<FloatingChatMessage>) {
    val context = LocalContext.current
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val latestPlayer by rememberUpdatedState(player)

    DisposableEffect(Unit) {
        onDispose { latestPlayer?.release() }
    }

    fun togglePlayback(message: FloatingChatMessage) {
        if (playingMessageId == message.id) {
            player?.pause()
            playingMessageId = null
            return
        }
        player?.release()
        val nextPlayer = message.resourceUrl
            ?.takeIf(String::isNotBlank)
            ?.let { uri -> runCatching { MediaPlayer.create(context, Uri.parse(uri)) }.getOrNull() }
        if (nextPlayer == null) {
            playingMessageId = null
            return
        }
        nextPlayer.setOnCompletionListener {
            playingMessageId = null
            player = null
            it.release()
        }
        player = nextPlayer
        playingMessageId = message.id
        nextPlayer.start()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "当前会话",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "暂无语音消息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        } else {
            items(messages, key = { message -> message.id }) { message ->
                val isPlaying = playingMessageId == message.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(onClick = { togglePlayback(message) }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停语音" else "播放语音"
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.text.ifBlank { "语音消息" },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = listOfNotNull(message.senderName.takeIf(String::isNotBlank), message.detail)
                                    .joinToString(" · ")
                                    .ifBlank { "本地音频" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
