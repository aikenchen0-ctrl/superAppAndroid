package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.media.MediaPlayer
import android.view.Surface
import android.net.Uri

internal fun prepareVideoPlayer(
    context: Context,
    uri: Uri,
    surface: Surface,
    onPrepared: (MediaPlayer) -> Unit,
    onCompleted: () -> Unit,
    onError: () -> Boolean,
    player: MediaPlayer
) {
    player.setSurface(surface)
    player.isLooping = false
    player.setOnPreparedListener(onPrepared)
    player.setOnCompletionListener { onCompleted() }
    player.setOnErrorListener { _, _, _ -> onError() }
    player.setPlayableVideoDataSource(context, uri)
    player.prepareAsync()
}
