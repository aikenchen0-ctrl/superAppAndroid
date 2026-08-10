package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlaybackProgressTicker(player: MediaPlayer?, playing: Boolean, dragging: Boolean, completed: Boolean, onPositionChanged: (Int) -> Unit) {
    LaunchedEffect(player, playing, dragging, completed) {
        while (player != null && !dragging && (playing || completed)) {
            val currentPlayer = player ?: break
            val position = runCatching { currentPlayer.currentPosition }.getOrNull() ?: break
            onPositionChanged(position)
            delay(250)
        }
    }
}
