package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

internal fun MediaPlayer.setPlayableVideoDataSource(context: Context, uri: Uri) {
    if (uri.scheme == "file") {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            setDataSource(path)
            return
        }
    }
    if (uri.scheme.isNullOrBlank()) {
        setDataSource(uri.toString())
        return
    }
    setDataSource(context, uri)
}
