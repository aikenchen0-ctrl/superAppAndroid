package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import java.util.Locale

@Composable
internal fun VoiceMessageContent(message: FloatingChatMessage) {
    val context = LocalContext.current
    var playing by remember(message.id) { mutableStateOf(false) }
    var failed by remember(message.id) { mutableStateOf(false) }
    var playerRef by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    val durationText = message.detail ?: formatVoiceTimecode(message.mediaDurationMs ?: 0)

    DisposableEffect(message.id) {
        onDispose {
            playerRef?.release()
            playerRef = null
        }
    }

    Row(
        modifier = Modifier.widthIn(min = 132.dp, max = 228.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        CompactInteractiveSize {
            FilledTonalIconButton(
                onClick = {
                    val currentPlayer = playerRef
                    if (currentPlayer?.isPlaying == true) {
                        currentPlayer.pause()
                        playing = false
                        return@FilledTonalIconButton
                    }
                    if (currentPlayer != null) {
                        currentPlayer.start()
                        playing = true
                        failed = false
                        return@FilledTonalIconButton
                    }
                    val uri = message.resourceUrl?.let(Uri::parse)
                    if (uri == null) {
                        failed = true
                        return@FilledTonalIconButton
                    }
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(context, uri)
                            setOnCompletionListener {
                                playing = false
                                it.seekTo(0)
                            }
                            setOnErrorListener { mp, _, _ ->
                                playing = false
                                failed = true
                                mp.release()
                                playerRef = null
                                true
                            }
                            prepare()
                            start()
                        }
                    }.onSuccess { mediaPlayer ->
                        playerRef = mediaPlayer
                        playing = true
                        failed = false
                    }.onFailure {
                        playing = false
                        failed = true
                    }
                },
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = OverlayTokens.voiceButton,
                    contentColor = OverlayTokens.voiceIcon
                )
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停语音" else "播放语音",
                    tint = OverlayTokens.voiceIcon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = if (failed) "语音播放失败" else "语音消息",
                size = 11.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = durationText,
                size = 10.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

internal fun formatVoiceTimecode(durationMs: Int): String {
    val totalSeconds = kotlin.math.ceil(durationMs.coerceAtLeast(0) / 1000.0)
        .toInt()
        .coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
