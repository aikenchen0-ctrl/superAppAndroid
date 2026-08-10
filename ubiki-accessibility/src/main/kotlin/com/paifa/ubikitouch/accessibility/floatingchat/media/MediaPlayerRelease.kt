package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.media.MediaPlayer
import android.view.Surface
import android.util.Log

internal fun releaseVideoPlayer(player: MediaPlayer, surface: Surface) {
    runCatching {
        player.setSurface(null)
        player.setOnPreparedListener(null)
        player.setOnCompletionListener(null)
        player.setOnErrorListener(null)
        if (player.isPlaying) player.stop()
    }.onFailure { Log.w("UbikiVideo", "failed to stop preview video player", it) }
    runCatching { player.reset() }
        .onFailure { Log.w("UbikiVideo", "failed to reset preview video player", it) }
    runCatching { player.release() }
        .onFailure { Log.w("UbikiVideo", "failed to release preview video player", it) }
    runCatching { surface.release() }
        .onFailure { Log.w("UbikiVideo", "failed to release preview video surface", it) }
}
