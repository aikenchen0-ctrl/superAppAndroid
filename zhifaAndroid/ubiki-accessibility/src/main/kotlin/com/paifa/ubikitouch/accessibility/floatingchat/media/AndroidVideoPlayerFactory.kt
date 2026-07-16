package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface

internal object AndroidVideoPlayerFactory {
    fun createPlayer(): MediaPlayer = MediaPlayer()

    fun createSurface(texture: SurfaceTexture): Surface = Surface(texture)
}
